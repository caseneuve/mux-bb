(ns mux.protocol
  "Mux backend detection and dispatch.

   Protocol contract (backend map keys):
     Core (required by all backends):
       :new-window!  (fn [window-name] → id)        Create/find a window
       :send!        (fn [window-name text] → any)   Send text + Enter
       :capture!     (fn [window-name] → string)     Read terminal scrollback
       :list!        (fn [] → [name ...])            List known windows (always returns [], never nil)
     Extended (optional, backend-specific):
       :spawn-pane!     (fn [opts] → pane-meta)      Spawn a split pane (tmux-first)
       :wait-for!        (fn [signal timeout] → any) Block until signal
       :signal-cmd       (fn [signal] → string)      Shell cmd to fire signal
       :notify!          (fn [window title body])     Sidebar notification
       :set-description! (fn [window text])           Workspace description
       :set-color!       (fn [window color])          Workspace tab color
     Callers check extended fns with when-let before use.")

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
