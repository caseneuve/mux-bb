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

Core API remains synchronous, with optional async command variants:

| Key | Signature | Description |
|-----|-----------|-------------|
| `:new-window-async!` | `(fn [name] → future)` | Async variant of `:new-window!` |
| `:send-async!` | `(fn [name text] → future)` | Async variant of `:send!` |

### Contract table (completion/error semantics)

| Key | Return | Completion point | Error channel | Timeout/cancel |
|---|---|---|---|---|
| `:new-window!` | tmux/cmux command output (usually string) | backend command exits | throws `ex-info` | no builtin timeout/cancel |
| `:send!` | backend command output | backend command exits | throws `ex-info` | no builtin timeout/cancel |
| `:spawn-pane!` (tmux) | `{:session :window :pane-id :target :launch-command}` | split command exits and metadata parsed | throws `ex-info` with `:cause` | no builtin timeout/cancel |
| `:capture!` | string | capture command exits | throws `ex-info` | n/a |
| `:list!` | vector of names (possibly empty) | list command exits | returns `[]` on list failure in tmux backend | n/a |
| `:new-window-async!` | `future` yielding same value as `:new-window!` | future realized | exception rethrown on deref | caller controls deref timeout |
| `:send-async!` | `future` yielding same value as `:send!` | future realized | exception rethrown on deref | caller controls deref timeout |
| `:spawn-pane-async!` (tmux) | `future` yielding same value as `:spawn-pane!` | future realized | exception rethrown on deref | caller controls deref timeout |

Extended keys (backend-specific):

| Key | Signature | Description |
|-----|-----------|-------------|
| `:spawn-pane!` | `(fn [opts] → pane-meta)` | Spawn a tmux split pane and optionally run a command |
| `:spawn-pane-async!` | `(fn [opts] → future)` | Async variant of `:spawn-pane!` (tmux) |
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

### Backend parity matrix

| Key | tmux | cmux | Notes |
|---|---|---|---|
| `:new-window!` | ✅ | ✅ | core command |
| `:send!` | ✅ | ✅ | core command |
| `:capture!` | ✅ | ✅ | core query |
| `:list!` | ✅ | ✅ | core query |
| `:new-window-async!` | ✅ | ✅ | async wrapper over sync command |
| `:send-async!` | ✅ | ✅ | async wrapper over sync command |
| `:spawn-pane!` | ✅ | ➖ | tmux-only split-pane capability |
| `:spawn-pane-async!` | ✅ | ➖ | tmux-only async split-pane |
| `:wait-for!` | ➖ | ✅ | cmux signaling API |
| `:signal-cmd` | ➖ | ✅ | cmux signaling API |
| `:notify!` | ➖ | ✅ | cmux UX API |
| `:set-description!` | ➖ | ✅ | cmux UX API |
| `:set-color!` | ➖ | ✅ | cmux UX API |

### Compatibility + migration checklist

- ✅ Current release is non-breaking: sync keys unchanged.
- ✅ Async path is additive via `*-async!` keys.
- ✅ Query ops (`:capture!`, `:list!`) remain synchronous.
- Call sites can migrate incrementally:
  1. Keep current sync usage.
  2. Switch command ops to `*-async!` where non-blocking is needed.
  3. Deref with bounded timeout at orchestration boundary.
  4. Preserve existing exception handling (same `ex-info` parity on deref).
- Future deprecation path (if desired): announce first, then phase sync wrappers in a major release only.

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

## Shell execution model

`mux-bb` uses `babashka.process` at the shell boundary.

- Blocking path: `mux.shell/sh` (throws on non-zero exit)
- Non-blocking path: `mux.shell/spawn` + `mux.shell/wait`
- Lifecycle helpers: `mux.shell/alive?`, `mux.shell/kill!`

Non-blocking contract:
- `spawn` returns immediately with a process handle; caller owns lifecycle.
- `wait` with timeout does **not** stop the process; it returns `{:status :timeout ...}` and caller must `kill!` or `wait` later.
- `wait` completion returns normalized metadata map: `:cmd :exit :out :err`.
- timeout results include `:cmd` plus `:status :timeout` and `:timeout-ms`.

## Dependencies

- [Babashka](https://github.com/babashka/babashka) (runtime)
- tmux (for tmux backend)
- [cmux](https://cmux.app) (for cmux backend, macOS only)

