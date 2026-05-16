# mux-bb

![vibe: slopped](.github/badges/vibe-slopped.svg)

*Disclaimer:* I use this as a shared lib for helper scripts in my agentic flows. Ideas
and guidelines are mine, implementation: clankers. The text below is LLM-generated.

---

Babashka library for talking to terminal multiplexers (tmux and cmux).

Provides a uniform protocol for creating windows/workspaces, sending commands, and capturing output — regardless of whether the agent runs inside tmux or cmux.

## Usage

Add as a local dependency in your `bb.edn`:

```clojure
{:deps {mux-bb/mux-bb {:local/root "../mux-bb"}}}
```

### Detect backend

```clojure
(require '[mux.protocol :as mp])

;; Reads CMUX_SOCKET_PATH and TMUX from environment
(mp/detect-mux (into {} (System/getenv)))
;; => :cmux or :tmux
```

### Create backend and use it

```clojure
(require '[mux.protocol :as mp]
         '[mux.tmux :as tmux])

;; tmux
(let [info (tmux/derive-session-info "myproject" "main")
      be   (mp/make-backend :tmux info)]
  ((:new-window! be) "test")
  ((:send! be) "test" "echo hello")
  (Thread/sleep 500)
  (println ((:capture! be) "test")))

;; cmux
(require '[mux.cmux :as cmux])
(let [be (mp/make-backend :cmux {:cmux-bin "/path/to/cmux"})]
  ((:new-window! be) "test")
  ((:send! be) "test" "echo hello")
  (Thread/sleep 500)
  (println ((:capture! be) "test")))
```

### Protocol

All backends return a map with these core keys:

| Key | Signature | Description |
|-----|-----------|-------------|
| `:new-window!` | `(fn [name] → id)` | Create or find a window/workspace |
| `:send!` | `(fn [name text])` | Send text + Enter to a window |
| `:capture!` | `(fn [name] → string)` | Read terminal scrollback |
| `:list!` | `(fn [] → [name ...])` | List known windows |

Extended keys (backend-specific):

| Key | Signature | Description |
|-----|-----------|-------------|
| `:spawn-pane!` | `(fn [opts] → pane-meta)` | Spawn a tmux split pane and optionally run a command |
| `:wait-for!` | `(fn [signal timeout])` | Block until a named signal (cmux) |
| `:signal-cmd` | `(fn [signal] → string)` | Shell command to fire a signal (cmux) |
| `:notify!` | `(fn [name title body])` | Sidebar notification (cmux) |
| `:set-description!` | `(fn [name text])` | Set workspace description (cmux) |
| `:set-color!` | `(fn [name color])` | Set workspace tab color (cmux) |

`(:spawn-pane! be opts)` options:
- `:direction` => `:right | :left | :below | :above` (default `:below`)
- `:size` => tmux size string like `"30%"` or `"20"` (optional)
- `:command` => shell command string (optional)
- `:target` => tmux pane/window/session target (optional)
- `:cwd` => working directory for spawned pane (optional)

Returns: `{:session :window :pane-id :target :launch-command}`

## Testing

```bash
# Unit tests (no tmux needed, runs in podman)
bb test:unit

# Unit tests on host (quick iteration)
bb test:unit:host

# E2E tests (needs tmux, runs in podman)
bb build:test-image
bb test:e2e

# All tests
bb test
```

## Dependencies

- [Babashka](https://github.com/babashka/babashka) (runtime)
- tmux (for tmux backend)
- [cmux](https://cmux.app) (for cmux backend, macOS only)

