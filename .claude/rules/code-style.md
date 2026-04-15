---
description: Clojure style and architecture conventions
globs: ["src/**/*.clj", "test/**/*.clj"]
---

# Code Style

## Data, Calculations, Actions

Classify all code into three categories:

- **Data**: Immutable values (maps, vectors, keywords). Design data shapes first.
- **Calculations**: Pure functions — same input, same output, no side effects. Bulk of the codebase.
- **Actions**: Functions that depend on or affect the outside world (I/O, shell, state). Minimize and isolate.

FCIS: push actions to the edges, keep the core pure.

In this project:
- `protocol.clj` and the pure helpers in `cmux.clj`/`tmux.clj` are **Calculations**
- `shell.clj` (`sh`, `sh?`) and backend constructors' closures are **Actions**
- Protocol maps, arg vectors, parsed workspace data are **Data**

## Functions

- Short: 5–15 lines preferred
- Flat: no deep nesting
- Early returns via `cond` / `when` / guard clauses
- Separate calculations from actions

## Threading and naming

- Threading macros (`->`, `->>`, `some->`) for transformation pipelines
- `let` for naming meaningful intermediate values
- Use whichever reads best

## Naming conventions

- Pure functions: descriptive verbs (`build-cmux-args`, `parse-workspaces`, `derive-session-info`)
- Imperative functions: `!` suffix (`tmux!`, `cmux!`, `sh`)
- Predicates: `?` suffix or `-?` suffix (`tmux?`, `sh?`)
- Private helpers: `defn-` or `^:private`

## Extensibility

- Protocol backends are maps of functions — adding a new backend means adding a new `make-backend`
- New cmux operations: add a case to `build-cmux-args`
- No inheritance, no classes, no macros — data and functions
