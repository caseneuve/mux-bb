---
title: make mux backend commands non-blocking by contract
status: in_progress
priority: high
type: feature
labels: []
created: 2026-05-16
parent: null
blocked-by: [0003]
blocks: []
---

## Context

After pane spawn support, we want mux-backed operations to support non-blocking workflows with explicit completion semantics.
This todo defines protocol/backend contract evolution on top of 0003 shell primitives.

## Acceptance Criteria

- [ ] Add a per-key contract table in `mux.protocol` docs covering, for each key:
  - invocation return type,
  - completion signal/point,
  - error channel,
  - timeout/cancel behavior.
- [ ] Resolve contract ambiguity between command ops and query ops:
  - command ops: `:new-window!`, `:send!`, `:spawn-pane!`
  - query ops: `:capture!`, `:list!`
  and document whether each is async, sync, or dual-path.
- [ ] Compatibility gate (must pick one and document explicitly):
  - backward-compatible async introduction (e.g. new `*-async!` keys + deprecations), or
  - explicit breaking-release marker + migration guide + call-site checklist.
- [ ] Implement tmux backend to match the chosen contract.
- [ ] Add backend parity matrix (key × backend × status) with explicit cmux rationale for unsupported/degraded paths.
- [ ] Unit tests verify return semantics and error propagation parity between sync/async wrappers.
- [ ] E2E async tests use deterministic completion markers + bounded polling (no sleep-only assertions).
- [ ] E2E includes negative paths: invalid target, spawn failure, timeout, cancellation/cleanup.
- [ ] Protocol + README examples updated to reflect final contract.

## Affected Files

- `src/mux/protocol.clj`
- `src/mux/tmux.clj`
- `src/mux/cmux.clj` (status documented even if not implemented)
- `test/unit/*`
- `test/e2e/cases.edn`
- `README.md`

## Notes

- Depends on todo 0003 shell-boundary refactor.
- Keep checkpoint commits and explicit migration guidance.
- Async test plan should be stable under repeated runs (flake budget near zero).
