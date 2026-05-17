(ns mux.tmux
  "Tmux mux backend. Implements the mux protocol map.
   Pure helpers are separated from the backend constructor."
  (:require [mux.shell :as sh]
            [clojure.string :as str]))

;; -- Pure helpers: name sanitization --

(defn sanitize-name
  "Remove characters that are invalid in tmux session names and socket paths.
   Replaces / . : with hyphens, collapses runs, trims edges.
   Returns \"unnamed\" for empty/degenerate inputs."
  [s]
  (let [cleaned (-> (or s "")
                    (str/replace #"[/.:]+" "-")
                    (str/replace #"-+" "-")
                    (str/replace #"^-|-$" ""))]
    (if (str/blank? cleaned) "unnamed" cleaned)))

;; -- Pure helpers: hashing + session derivation --

(def ^:private default-sock
  "Default shared tmux socket path. One socket, many sessions."
  "/tmp/mux.sock")

(defn derive-session-info
  "Compute socket path and session name from project + branch.
   Sanitizes project name for tmux compatibility (no / . : in names).
   Default socket: /tmp/mux.sock (shared). Override with opts :sock.
   Pure — no I/O."
  ([project branch] (derive-session-info project branch {}))
  ([project branch {:keys [sock]}]
   (let [safe-project (sanitize-name project)
         hash         (sh/md5-short branch)]
     {:project project
      :branch  branch
      :hash    hash
      :sock    (or sock default-sock)
      :session (str safe-project "-" hash)})))

;; -- Pure helpers: target --

(defn build-target
  "Build tmux target string: session:window."
  [session window]
  (str session ":" window))

(defn direction->flags
  "Map logical pane direction to tmux split-window flags."
  [direction]
  (case (or direction :below)
    :right ["-h"]
    :left  ["-h" "-b"]
    :below ["-v"]
    :above ["-v" "-b"]
    (throw (ex-info (str "Invalid split direction: " direction)
                    {:cause :invalid-direction :direction direction}))))

(defn build-spawn-pane-args
  "Pure tmux split-window argv builder.
   opts: :direction :size :target :cwd :command"
  [{:keys [direction size target cwd command]}]
  (cond-> ["split-window" "-P" "-F" "#{session_name}|#{window_name}|#{pane_id}|#{session_name}:#{window_name}.#{pane_index}"]
    true (into (direction->flags direction))
    (seq size) (into ["-l" size])
    (seq target) (into ["-t" target])
    (seq cwd) (into ["-c" cwd])
    (seq command) (conj command)))

;; -- tmux shell wrappers (imperative) --

(defn tmux!
  "Run a tmux command on the given socket. Returns trimmed stdout."
  [sock & args]
  (apply sh/sh "tmux" "-S" sock args))

(defn tmux?
  "Run a tmux command, returning nil on failure."
  [sock & args]
  (try (apply tmux! sock args) (catch Exception _ nil)))

;; -- Scrollback depth: backend-internal detail --

(def ^:private scrollback-lines 1000)

;; -- Backend constructor --

(defn make-backend
  "Create a tmux mux backend from a context map {:sock :session}.
   Returns protocol fns and async counterparts for command ops.
   :capture! returns last 1000 lines of scrollback."
  [{:keys [sock session] :as ctx}]
  (let [new-window! (fn [window-name]
                      (tmux! sock "new-window" "-t" session "-n" window-name))
        send! (fn [window-name text]
                (tmux! sock "send-keys" "-t" (build-target session window-name) text "Enter"))
        capture! (fn [window-name]
                   (tmux! sock "capture-pane"
                          "-t" (build-target session window-name)
                          "-p" "-S" (str "-" scrollback-lines)))
        list! (fn []
                (or (some-> (tmux? sock "list-windows" "-t" session "-F" "#W")
                            str/split-lines)
                    []))
        spawn-pane! (fn [opts]
                      (let [resolved-target (or (:target opts)
                                                (some-> (tmux? sock "display-message" "-p" "#{session_name}:#{window_name}.#{pane_index}") str/trim))
                            args (assoc opts :target resolved-target)]
                        (when-not resolved-target
                          (throw (ex-info "No tmux target available for pane split"
                                          {:cause :invalid-target :args opts :target nil})))
                        (try
                          (let [out (apply tmux! sock (build-spawn-pane-args args))
                                [sess win pane-id target] (str/split out #"\|")]
                            {:session sess :window win :pane-id pane-id :target target :launch-command (:command opts)})
                          (catch clojure.lang.ExceptionInfo e
                            (let [d (ex-data e)
                                  msg (.getMessage e)
                                  detail (str/lower-case (str (or (:err d) "") " " msg))
                                  cause (cond
                                          (re-find #"can't find|unknown" detail) :invalid-target
                                          (re-find #"no such file|cannot run program.*tmux|command not found" detail) :tmux-missing
                                          :else :split-failed)]
                              (throw (ex-info "tmux pane spawn failed"
                                              {:cause cause :args opts :target resolved-target :stderr (:err d) :exit (:exit d)} e)))))))]
    {:name "tmux"
     :ctx  ctx
     :new-window! new-window!
     :send! send!
     :capture! capture!
     :list! list!
     :spawn-pane! spawn-pane!
     :new-window-async! (fn [window-name] (future (new-window! window-name)))
     :send-async! (fn [window-name text] (future (send! window-name text)))
     :spawn-pane-async! (fn [opts] (future (spawn-pane! opts)))}))
