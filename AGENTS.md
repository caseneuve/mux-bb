# AGENTS.md

## Project

mux-bb is a Babashka library for talking to terminal multiplexers (tmux and cmux).
It provides a uniform protocol for creating windows/workspaces, sending commands,
and capturing output — regardless of whether the agent runs inside tmux or cmux.

This is a **Babashka (`bb`) project**. Read this file first, then `bb.edn` for tasks
and paths.

Consumers: `dotagents` (personal agent config), `agentic-stuff/piotr` (work agent tooling).

## Architecture

FCIS (Functional Core, Imperative Shell):

- `src/mux/protocol.clj` — pure detection and dispatch (zero I/O)
- `src/mux/cmux.clj` — pure arg builders and parsers + imperative backend constructor
- `src/mux/tmux.clj` — pure session derivation + imperative backend constructor
- `src/mux/shell.clj` — thin I/O boundary (sh, sh?, md5)

Pure functions (arg building, parsing, detection, session derivation) are the bulk
of the code and are fully unit-testable without tmux or cmux.

Backend constructors return protocol maps with imperative closures — these are tested
via E2E (tmux in podman) or structurally (cmux, since it requires macOS GUI).

## Protocol contract

All backends return a map with these core keys:

| Key | Signature | Description |
|-----|-----------|-------------|
| `:new-window!` | `(fn [name] → id)` | Create or find a window/workspace |
| `:send!` | `(fn [name text])` | Send text + Enter to a window |
| `:capture!` | `(fn [name] → string)` | Read terminal scrollback |
| `:list!` | `(fn [] → [name ...])` | List known windows |

cmux backends also provide extended keys: `:wait-for!`, `:signal-cmd`, `:notify!`,
`:set-description!`, `:set-color!`.

## Testing

### Commands

```bash
bb test:unit:host   # Unit tests on host (quick iteration)
bb test:unit        # Unit tests in podman (isolated)
bb test:e2e         # E2E tests in podman (needs tmux, build image first)
bb build:test-image # Build podman image with tmux
bb test             # All tests in podman
```

### Strategy

- **Unit tests**: all pure functions. No tmux/cmux binary needed. Fast, run on host.
- **E2E (end2edn)**: tmux backend lifecycle only. Runs in podman with real tmux.
- **cmux E2E**: not possible (macOS GUI app). Pure functions covered by unit tests;
  imperative wrapper is 2 lines.

### TDD workflow

- Red → green → refactor per slice.
- Run the failing test immediately after writing it.
- Run the passing test immediately after implementation.
- Never batch implementation ahead of tests.

## Babashka specifics

- No `deftype` / `defrecord` — use maps and multimethods
- Prefer `babashka.process`, `babashka.fs`, `babashka.cli`
- `*command-line-args*` instead of Java main args

## Commits

```
[category] short description
```

Categories: `mux`, `tmux`, `cmux`, `shell`, `test`, `docs`

## Safety

- E2E tests run in podman containers, never on host
- tmux sockets use `/tmp` inside containers
- No destructive filesystem operations in this library
