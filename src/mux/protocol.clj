(ns mux.protocol
  "Mux backend detection and dispatch.

   Protocol contract (backend map keys):
     Core command ops (sync):
       :new-window!        (fn [window-name] → id)
       :send!              (fn [window-name text] → any)
     Core query ops (sync):
       :capture!           (fn [window-name] → string)
       :list!              (fn [] → [name ...])

     Async command extensions (optional):
       :new-window-async!  (fn [window-name] → future)
       :send-async!        (fn [window-name text] → future)
       :spawn-pane-async!  (fn [opts] → future) ; tmux

     Extended (optional, backend-specific):
       :spawn-pane!        (fn [opts] → pane-meta)        ; tmux
       :wait-for!          (fn [signal timeout] → any)    ; cmux
       :signal-cmd         (fn [signal] → string)         ; cmux
       :notify!            (fn [window title body])       ; cmux
       :set-description!   (fn [window text])             ; cmux
       :set-color!         (fn [window color])            ; cmux

     Per-key semantics:
       - sync command/query keys complete when backend command exits.
       - async keys return a future; completion point is future realization.
       - async deref returns same value as sync counterpart or rethrows same exception.
       - no backend-level timeout/cancel in mux protocol; caller owns bounded deref/poll/cancel policy.
       - :list! returns [] on tmux list failure (never nil).
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
