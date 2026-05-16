---
title: tmux pane spawn for agent communication
status: open
priority: medium
type: feature
labels: []
created: 2026-05-16
parent: null
blocked-by: []
blocks: []
---

## Context

`mux-bb` currently exposes window/workspace-level primitives (`:new-window!`, `:send!`, `:capture!`) but no pane-level spawn primitive for tmux. Agent orchestration workflows need a deterministic way to open a child process in a split pane in the current window so parent/child are visible together.

This task adds tmux pane creation APIs and metadata so downstream tools can launch `pi` children for channel-based collaboration.

## Acceptance Criteria

- [ ] Tmux backend supports creating a split pane in a target window/session with configurable direction/size and command execution.
- [ ] New pane API returns inspectable metadata (`session`, `window`, `pane-id`, `target`, launch command).
- [ ] Errors are explicit (`tmux missing`, invalid target, split failure, command failure) and include attempted args.
- [ ] Existing window-level API remains backward compatible.
- [ ] Unit tests cover pure planning/arg shaping and executor error mapping.
- [ ] E2E coverage verifies pane is created, command runs, output can be captured from the new pane.

## Affected Files

- `src/mux/tmux.clj` — add pane split primitives and helpers.
- `src/mux/protocol.clj` — optional protocol extension key(s) for pane support.
- `src/mux/runner/preflight.clj` — optional pane-aware preflight helpers.
- `test/unit/tmux_test.clj` — unit tests for pane API and errors.
- `test/e2e/cases.edn` — tmux pane lifecycle scenario.
- `README.md` — document pane API and example usage.

## E2E Spec

GIVEN an existing tmux session/window managed by `mux-bb`
WHEN caller creates a split pane and runs a command in that pane
THEN pane metadata is returned and command output is observable via capture.

GIVEN an invalid tmux target or split failure
WHEN pane creation is attempted
THEN the call fails with actionable error details and no ambiguous success result.

## Notes

- Keep API tmux-first; cmux parity can be introduced in a follow-up (workspace analogue or explicit unsupported signal).
- Prefer pure planning functions for command/arg generation; isolate shell execution at boundaries.
- This item enables downstream `dotagents` todo `0008.3` (spawn child Pi in mux communication mode).
