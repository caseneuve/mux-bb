(ns mux.cmux
  "cmux mux backend. Implements the mux protocol map.
   Uses the cmux CLI binary rather than raw sockets.
   Only works when the agent runs inside a cmux pane (socket auth restriction)."
  (:require [mux.shell :as sh]
            [clojure.string :as str]))

;; -- Pure helpers --

(defn escape-send-text
  "Escape newlines and tabs for cmux send (uses \\n and \\t escapes)."
  [text]
  (-> text
      (str/replace "\n" "\\n")
      (str/replace "\t" "\\t")))

(defn build-cmux-args
  "Build cmux CLI argument vector for a given operation.
   Pure — returns a vec of strings."
  [op params]
  (case op
    :send
    (cond-> ["send"]
      (:workspace params) (into ["--workspace" (:workspace params)])
      true                (into ["--" (escape-send-text (:text params))]))

    :capture
    (cond-> ["capture-pane"]
      (:workspace params) (into ["--workspace" (:workspace params)])
      true                (into ["--scrollback" "--lines" (str (:lines params))]))

    :new-workspace
    (cond-> ["new-workspace"]
      (:name params) (into ["--name" (:name params)])
      (:cwd params)  (into ["--cwd" (:cwd params)]))

    :list-workspaces
    ["list-workspaces"]

    :wait-for
    (cond-> ["wait-for"]
      (:signal? params) (conj "-S")
      true              (conj (:name params))
      (:timeout params) (into ["--timeout" (str (:timeout params))]))

    :notify
    (cond-> ["notify"]
      (:workspace params) (into ["--workspace" (:workspace params)])
      (:title params)     (into ["--title" (:title params)])
      (:body params)      (into ["--body" (:body params)]))

    :set-description
    (cond-> ["workspace-action" "--action" "set-description"]
      (:workspace params) (into ["--workspace" (:workspace params)])
      (:description params) (into ["--description" (:description params)]))

    :set-color
    (cond-> ["workspace-action" "--action" "set-color"]
      (:workspace params) (into ["--workspace" (:workspace params)])
      (:color params)     (into ["--color" (:color params)]))))

(defn parse-workspaces
  "Parse cmux list-workspaces output into a vec of maps.
   Format: '* workspace:1  Terminal 1  [selected]' or '  workspace:7  Terminal 2'
   Note: first line may lose leading spaces from sh/sh trim."
  [output]
  (if (or (nil? output) (str/blank? output))
    []
    (->> (str/split-lines output)
         (keep (fn [line]
                 (when-let [[_ sel id name-and-flags]
                            (re-find #"^([* ]?)\s*(workspace:\S+)\s+(.+)" line)]
                   (let [name (str/trim (str/replace name-and-flags #"\s+\[.*\]$" ""))]
                     {:id        id
                      :name      name
                      :selected? (= sel "*")}))))
         vec)))

;; -- cmux CLI wrapper (imperative) --

(def ^:private default-cmux-bin
  "/Applications/cmux.app/Contents/Resources/bin/cmux")

(defn- resolve-cmux-bin
  "Find the cmux CLI binary. Checks opts, then PATH, then app bundle."
  [opts]
  (or (:cmux-bin opts)
      (sh/sh? "which" "cmux")
      default-cmux-bin))

(defn cmux!
  "Run a cmux CLI command. Returns trimmed stdout. Throws on failure."
  [cmux-bin & args]
  (apply sh/sh cmux-bin args))

(defn- require-workspace!
  "Look up workspace ID by name, throw if not registered."
  [workspaces window-name]
  (or (get @workspaces window-name)
      (throw (ex-info (str "Workspace '" window-name "' not registered. Call :new-window! first.")
                      {:type :workspace-not-found :window window-name}))))

(defn- find-workspace-by-name
  "Find existing workspace by name. Returns workspace ID or nil."
  [cmux-bin name]
  (when-let [output (try (cmux! cmux-bin "list-workspaces") (catch Exception _ nil))]
    (some (fn [ws] (when (= name (:name ws)) (:id ws)))
          (parse-workspaces output))))

;; -- Scrollback depth: backend-internal detail --

(def ^:private scrollback-lines 1000)

;; -- Backend constructor --

(defn make-backend
  "Create a cmux mux backend.
   Workspaces in cmux ≈ windows in tmux. Each 'window' we create
   becomes a cmux workspace. We track workspace IDs by name.

   :new-window! finds existing workspace by name or creates a new one.
   :send! sends text to a workspace (with Enter appended).
   :capture! reads terminal text with scrollback.
   :list! returns tracked workspace names.
   :wait-for! blocks until a named signal is raised (native cmux sync).
   :notify! sends a notification to a workspace sidebar."
  [opts]
  (let [cmux-bin   (resolve-cmux-bin opts)
        workspaces (atom {})]
    {:name "cmux"
     :ctx  {:cmux-bin cmux-bin :workspaces workspaces}

     :new-window!
     (fn [window-name]
       (or (when-let [existing (find-workspace-by-name cmux-bin window-name)]
             (swap! workspaces assoc window-name existing)
             existing)
           (let [prev-ws (try (str/trim (cmux! cmux-bin "current-workspace"))
                              (catch Exception _ nil))
                 args    (build-cmux-args :new-workspace {:name window-name})
                 result  (try (apply cmux! cmux-bin args)
                              (catch clojure.lang.ExceptionInfo e
                                (or (find-workspace-by-name cmux-bin window-name)
                                    (throw e))))
                 ws-id   (str/replace-first (str/trim (str result)) #"^OK " "")]
             (when prev-ws
               (try (cmux! cmux-bin "select-workspace" "--workspace" prev-ws)
                    (catch Exception _ nil)))
             (if (seq ws-id)
               (do (swap! workspaces assoc window-name ws-id)
                   (Thread/sleep 500)
                   ws-id)
               (if-let [retry (find-workspace-by-name cmux-bin window-name)]
                 (do (swap! workspaces assoc window-name retry)
                     retry)
                 (throw (ex-info (str "cmux: failed to create or find workspace '" window-name "'")
                                 {:type :cmux-error :output result})))))))

     :send!
     (fn [window-name text]
       (let [ws-id (require-workspace! workspaces window-name)
             args  (build-cmux-args :send {:workspace ws-id
                                           :text (str text "\n")})]
         (apply cmux! cmux-bin args)))

     :capture!
     (fn [window-name]
       (let [ws-id (require-workspace! workspaces window-name)
             args  (build-cmux-args :capture {:workspace ws-id
                                              :lines scrollback-lines})]
         (apply cmux! cmux-bin args)))

     :wait-for!
     (fn [signal-name timeout]
       (let [args (build-cmux-args :wait-for {:name signal-name :timeout timeout})]
         (apply cmux! cmux-bin args)))

     :signal-cmd
     (fn [signal-name]
       (str cmux-bin " wait-for -S " signal-name))

     :notify!
     (fn [window-name title body]
       (let [ws-id (require-workspace! workspaces window-name)
             args  (build-cmux-args :notify {:workspace ws-id
                                             :title title
                                             :body body})]
         (try (apply cmux! cmux-bin args)
              (catch Exception _ nil))))

     :set-description!
     (fn [window-name description]
       (let [ws-id (require-workspace! workspaces window-name)
             args  (build-cmux-args :set-description {:workspace ws-id
                                                      :description description})]
         (try (apply cmux! cmux-bin args)
              (catch Exception _ nil))))

     :set-color!
     (fn [window-name color]
       (let [ws-id (require-workspace! workspaces window-name)
             args  (build-cmux-args :set-color {:workspace ws-id
                                                :color color})]
         (try (apply cmux! cmux-bin args)
              (catch Exception _ nil))))

     :list!
     (fn []
       (keys @workspaces))}))
