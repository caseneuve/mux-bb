(ns mux.protocol
  "Mux backend detection and dispatch.

   Protocol contract (backend map keys):
     Core (sync/query-compatible):
       :new-window!        (fn [window-name] → id)        Create/find a window (sync)
       :send!              (fn [window-name text] → any)  Send text + Enter (sync)
       :capture!           (fn [window-name] → string)    Read terminal scrollback (sync query)
       :list!              (fn [] → [name ...])           List known windows (sync query; never nil)

     Async command extensions (optional):
       :new-window-async!  (fn [window-name] → future)
       :send-async!        (fn [window-name text] → future)

     Extended (optional, backend-specific):
       :spawn-pane!        (fn [opts] → pane-meta)        Spawn a split pane (tmux)
       :spawn-pane-async!  (fn [opts] → future)           Async pane spawn (tmux)
       :wait-for!          (fn [signal timeout] → any)    Block until signal (cmux)
       :signal-cmd         (fn [signal] → string)         Shell cmd to fire signal (cmux)
       :notify!            (fn [window title body])       Sidebar notification (cmux)
       :set-description!   (fn [window text])             Workspace description (cmux)
       :set-color!         (fn [window color])            Workspace tab color (cmux)

     Completion semantics:
       - sync keys complete when command exits.
       - async keys return a future that yields the same value/exception as sync counterpart.
     Callers check optional fns with when-let before use.")

;; -- Pure detection --

(defn detect-mux
  "Detect mux backend from environment map.
   Priority: cmux (if inside it) > tmux (if inside it) > tmux (fallback).
   Returns :cmux or :tmux."
  [env]
  (cond
    (seq (get env "CMUX_SOCKET_PATH")) :cmux
    (seq (get env "TMUX"))             :tmux
    :else                              :tmux))

;; -- Backend constructor dispatch --

(defn make-backend
  "Create the appropriate mux backend based on detection.
   Lazy-requires backend namespaces to avoid loading cmux code when using tmux.
   ctx-opts is backend-specific: {:sock :session} for tmux, {:cmux-bin} for cmux."
  [backend-type ctx-opts]
  (case backend-type
    :tmux (do (require 'mux.tmux)
              ((resolve 'mux.tmux/make-backend) ctx-opts))
    :cmux (do (require 'mux.cmux)
              ((resolve 'mux.cmux/make-backend) ctx-opts))
    (throw (ex-info (str "Unknown mux backend: " backend-type)
                    {:type :unknown-backend :backend backend-type}))))
