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

(defn derive-session-info
  "Compute socket path and session name from project + branch.
   Sanitizes project name for tmux compatibility (no / . : in names).
   Pure — no I/O."
  [project branch]
  (let [safe-project (sanitize-name project)
        hash         (sh/md5-short branch)]
    {:project project
     :branch  branch
     :hash    hash
     :sock    (str "/tmp/claude-" safe-project "-" hash ".sock")
     :session (str safe-project "-" hash)}))

;; -- Pure helpers: target --

(defn build-target
  "Build tmux target string: session:window."
  [session window]
  (str session ":" window))

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
   Returns a protocol map with :new-window! :send! :capture! :list! :ctx.
   :capture! returns last 1000 lines of scrollback."
  [{:keys [sock session] :as ctx}]
  {:name "tmux"
   :ctx  ctx

   :new-window!
   (fn [window-name]
     (tmux! sock "new-window" "-t" session "-n" window-name))

   :send!
   (fn [window-name text]
     (tmux! sock "send-keys" "-t" (build-target session window-name) text "Enter"))

   :capture!
   (fn [window-name]
     (tmux! sock "capture-pane"
            "-t" (build-target session window-name)
            "-p" "-S" (str "-" scrollback-lines)))

   :list!
   (fn []
     (or (some-> (tmux? sock "list-windows" "-t" session "-F" "#W")
                 str/split-lines)
         []))})
