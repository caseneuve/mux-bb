(ns mux.runner.preflight
  "Session and window lifecycle for tmux backend.
   Ensures sessions/windows exist before running commands.
   Tmux-specific — cmux consumers call :new-window! directly.

   Pure functions (parse-probe-marker) are separated for testing.
   Imperative functions use mux.tmux directly."
  (:require [mux.tmux :as mt]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Pure functions
;; ---------------------------------------------------------------------------

(defn parse-probe-marker
  "Extract probe result from raw pane capture.
   Finds the last line starting with 'marker:' and returns what follows.
   Returns string or nil."
  [marker raw]
  (let [prefix (str marker ":")]
    (->> (str/split-lines (or raw ""))
         (filter #(str/starts-with? % prefix))
         last
         (#(when % (subs % (count prefix)))))))

;; ---------------------------------------------------------------------------
;; Imperative functions (tmux-specific)
;; ---------------------------------------------------------------------------

(defn ensure-session!
  "Ensure tmux session exists on the backend's socket.
   Creates it if needed. Returns {:session :status}.
   opts: {:start-dir \"/path\", :venv \"/path\"}"
  [backend & [opts]]
  (let [{:keys [sock session]} (:ctx backend)
        {:keys [start-dir venv]} opts]
    (if (mt/tmux? sock "has-session" "-t" session)
      {:session session :status :exists}
      (do
        (mt/tmux! sock "new-session" "-d" "-s" session
                  "-c" (or start-dir (System/getenv "HOME")))
        (when venv
          (mt/tmux! sock "send-keys" "-t" session
                    (str "source " venv "/bin/activate") "Enter")
          (Thread/sleep 500))
        {:session session :status :created}))))

(defn ensure-window!
  "Ensure tmux window exists in session.
   Creates it if needed. Returns :exists or :created.
   opts: {:venv \"/path\"}"
  [backend window & [opts]]
  (let [{:keys [sock session]} (:ctx backend)
        {:keys [venv]} opts
        windows (or (some-> (mt/tmux? sock "list-windows" "-t" session "-F" "#W")
                            str/split-lines
                            set)
                    #{})]
    (if (contains? windows window)
      :exists
      (do
        (mt/tmux! sock "new-window" "-t" session "-n" window)
        (when venv
          (mt/tmux! sock "send-keys" "-t" (mt/build-target session window)
                    (str "source " venv "/bin/activate") "Enter")
          (Thread/sleep 500))
        :created))))

(defn probe-env!
  "Send a probe expression to a window and extract the result.
   expression: shell string to echo (consumer provides, e.g. \"VENV:$VIRTUAL_ENV:CWD:$(pwd)\")
   Returns the evaluated expression result string, or nil."
  [backend window expression]
  (let [{:keys [sock session]} (:ctx backend)
        target  (mt/build-target session window)
        marker  (str "PROBE_" (System/currentTimeMillis))
        send!   (:send! backend)
        cap!    (:capture! backend)]
    (send! window (str "echo " marker ":" expression))
    (Thread/sleep 300)
    (let [raw (cap! window)]
      (parse-probe-marker marker raw))))
