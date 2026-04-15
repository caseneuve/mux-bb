(ns mux.runner
  "Run a command in a mux window, wait for completion, return clean output.
   Pure functions (marker generation, output extraction, arg parsing) are
   separated from imperative execution for testability.
   Backend-agnostic: works with tmux and cmux via mux.protocol."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Pure functions
;; ---------------------------------------------------------------------------

(defn make-marker
  "Generate a unique marker prefix from timestamp and random value.
   Returns a string like MUXRUN_<ts>_<rand>."
  [ts rand-val]
  (str "MUXRUN_" ts "_" rand-val))

(defn wrap-with-markers
  "Wrap a command with echo markers for output extraction.
   Returns the shell string to send."
  [command marker]
  (str "echo " marker "_START; " command "; echo " marker "_END:$?"))

(defn extract-output
  "Extract command output and exit code from raw pane capture.
   Looks for start/end markers. Uses last occurrence (handles scrollback).
   Returns {:output string, :exit-code int} or nil."
  [raw start-marker end-marker]
  (let [lines     (str/split-lines raw)
        indexed   (map-indexed vector lines)
        start-idx (->> indexed
                       (filter #(= (second %) start-marker))
                       last first)
        end-entry (->> indexed
                       (filter #(str/starts-with? (second %) (str end-marker ":")))
                       last)]
    (when (and start-idx end-entry)
      (let [end-idx   (first end-entry)
            end-line  (second end-entry)
            exit-code (some-> (last (str/split end-line #":")) parse-long)
            first-out (inc start-idx)
            last-out  end-idx]
        {:output    (if (< first-out last-out)
                      (str/join "\n" (subvec (vec lines) first-out last-out))
                      "")
         :exit-code (or exit-code 0)}))))

(defn parse-args
  "Parse CLI arguments into an options map.
   Positional: <window> <command>. Named: --timeout, --cd, --sock, --session.
   Utility for consumer CLIs — run-cmd! itself takes a map."
  [args]
  (loop [args (seq args)
         opts {:timeout 300}]
    (if-not args
      opts
      (let [[head & tail] args]
        (case head
          "--timeout" (recur (next tail) (assoc opts :timeout (parse-long (first tail))))
          "--cd"      (recur (next tail) (assoc opts :cd (first tail)))
          "--sock"    (recur (next tail) (assoc opts :sock (first tail)))
          "--session" (recur (next tail) (assoc opts :session (first tail)))
          ;; positional: window then command
          (if-not (:window opts)
            (recur tail (assoc opts :window head))
            (recur tail (assoc opts :command head))))))))

;; ---------------------------------------------------------------------------
;; Workspace UX colors (cmux tab coloring)
;; ---------------------------------------------------------------------------

(def color-running "#1565C0")
(def color-passed  "#22C55E")
(def color-failed  "#EF4444")

;; ---------------------------------------------------------------------------
;; Workspace UX helpers (cmux-only, no-op on tmux)
;; ---------------------------------------------------------------------------

(defn- set-description!
  "Update workspace description (cmux only, best-effort)."
  [backend window text]
  (when-let [set-desc! (:set-description! backend)]
    (set-desc! window text)))

(defn- set-color!
  "Update workspace color (cmux only, best-effort)."
  [backend window color]
  (when-let [sc! (:set-color! backend)]
    (sc! window color)))

(defn- notify-completion!
  "Send notification, update description and color on completion (cmux only)."
  [backend window command exit-code elapsed-s]
  (let [status (if (zero? exit-code) "Done" (str "Failed (exit " exit-code ")"))
        desc   (str status " (" (format "%.1f" (double elapsed-s)) "s)")]
    (when-let [notify! (:notify! backend)]
      (notify! window status command))
    (set-description! backend window desc)
    (set-color! backend window (if (zero? exit-code) color-passed color-failed))))

;; ---------------------------------------------------------------------------
;; Waiting for completion
;; ---------------------------------------------------------------------------

(defn- poll-for-marker!
  "Poll pane content until end marker appears or timeout (seconds).
   Uses backend :capture! function. Throws ex-info on timeout."
  [capture-fn window end-marker timeout]
  (loop [elapsed 0]
    (let [pane (capture-fn window)]
      (if (some #(str/starts-with? % (str end-marker ":"))
                (str/split-lines pane))
        :found
        (if (>= elapsed timeout)
          (throw (ex-info (str "Command did not complete within " timeout "s")
                          {:type :timeout :timeout timeout :elapsed elapsed}))
          (do (Thread/sleep 2000)
              (recur (+ elapsed 2))))))))

(defn- wait-for-completion!
  "Wait for command to complete. Uses native cmux wait-for! when available,
   falls back to polling capture for tmux."
  [backend window end-marker timeout]
  (if-let [wait-for! (:wait-for! backend)]
    ;; cmux: native sync — block until signal, no polling
    (try
      (wait-for! end-marker timeout)
      (catch Exception e
        (throw (ex-info (str "Command did not complete within " timeout "s")
                        {:type :timeout :timeout timeout} e))))
    ;; tmux: poll capture-pane for end marker
    (poll-for-marker! (:capture! backend) window end-marker timeout)))

;; ---------------------------------------------------------------------------
;; Main entry point
;; ---------------------------------------------------------------------------

(defn run-cmd!
  "Run a command in a mux window, wait for completion, return {:output :exit-code}.
   backend: protocol map from mux.protocol/make-backend.
   opts: {:window :command :timeout :cd}.
   Throws ex-info on timeout or marker extraction failure."
  [backend {:keys [window command timeout cd] :or {timeout 300}}]
  (let [send! (:send! backend)
        cap!  (:capture! backend)]
    ;; cd if requested
    (when cd
      (send! window (str "cd " cd))
      (Thread/sleep 200))
    ;; workspace UX: running state
    (set-description! backend window (str "Running: " command))
    (set-color! backend window color-running)
    ;; generate marker and build wrapped command
    (let [marker  (make-marker (System/currentTimeMillis) (rand-int 100000))
          start   (str marker "_START")
          end     (str marker "_END")
          ;; cmux: append signal command for native sync
          signal-cmd (when-let [sc (:signal-cmd backend)] (sc end))
          cmd-str (cond-> (wrap-with-markers command marker)
                    signal-cmd (str "; " signal-cmd))
          t0      (System/currentTimeMillis)]
      ;; send and wait
      (send! window cmd-str)
      (wait-for-completion! backend window end timeout)
      (Thread/sleep 200)
      ;; capture and extract
      (let [raw     (cap! window)
            result  (extract-output raw start end)
            elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)]
        (if result
          (do (notify-completion! backend window command (:exit-code result) elapsed)
              result)
          (do (set-description! backend window "Error: output markers not found")
              (throw (ex-info "Could not find output markers in pane"
                              {:type :marker-not-found :raw raw}))))))))
