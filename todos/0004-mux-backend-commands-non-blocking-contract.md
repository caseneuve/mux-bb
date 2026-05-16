---
title: make mux backend commands non-blocking by contract
status: open
priority: high
type: feature
labels: []
created: 2026-05-16
parent: null
blocked-by: [0003]
blocks: []
---

## Context

After pane spawn support, we want all mux-backed operations to be non-blocking by default. This includes tmux (and cmux where applicable), with explicit API contract updates for result retrieval and completion semantics.

## Acceptance Criteria

- [ ] Define and document non-blocking contract for backend keys (`:new-window!`, `:send!`, `:capture!`, `:list!`, `:spawn-pane!`).
- [ ] Implement tmux backend changes to satisfy the contract.
- [ ] Provide compatibility path (or migration note) for existing synchronous callers.
- [ ] Unit tests verify async return behavior and error propagation.
- [ ] E2E tests verify practical non-blocking workflows (fire command, poll/capture later).
- [ ] Protocol and README updated with examples.

## Affected Files

- `src/mux/protocol.clj`
- `src/mux/tmux.clj`
- `src/mux/cmux.clj` (as applicable / documented unsupported gaps)
- `test/unit/*`
- `test/e2e/cases.edn`
- `README.md`

## Notes

- Depends on todo 0003 shell-boundary refactor.
- Keep changes incremental with checkpoint commits and explicit migration guidance.
