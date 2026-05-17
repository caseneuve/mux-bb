---
title: refactor shell boundary to native babashka.process primitives
status: done
priority: high
type: refactor
labels: []
created: 2026-05-16
parent: null
blocked-by: []
blocks: [0004]
---

## Context

`mux-bb` currently shells out through blocking helpers (`sh`/`sh?`) in `src/mux/shell.clj`.
This todo is **shell-boundary only**: introduce native `babashka.process/process` primitives and lifecycle rules without changing backend protocol semantics yet.

## Acceptance Criteria

- [x] Add process-native helpers in `src/mux/shell.clj` (spawn + wait/check adapters) while keeping existing blocking behavior available.
- [x] Define a process-handle abstraction and lifecycle ownership rules:
  - who owns stdout/stderr consumption,
  - who closes streams,
  - who performs wait/timeout/kill.
- [x] Define normalized result/error data shape for both blocking and non-blocking paths (including `:exit`, `:cmd`, `:out`, `:err`, timeout/cancel metadata).
- [x] Preserve backward compatibility for existing blocking callers in this todo (no protocol contract change here).
- [x] Unit tests cover success/failure mapping, timeout behavior, and lifecycle cleanup guarantees.
- [x] README documents shell execution model and lifecycle ownership expectations.

## Affected Files

- `src/mux/shell.clj`
- `test/unit/shell_test.clj`
- `README.md`

## Notes

- FCIS rule: keep process management at I/O boundary; pure layers unchanged.
- This todo is prerequisite infrastructure for 0004 and must ship without backend API breakage.
