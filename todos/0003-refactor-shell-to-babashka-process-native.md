---
title: refactor shell boundary to native babashka.process primitives
status: open
priority: high
type: refactor
labels: []
created: 2026-05-16
parent: null
blocked-by: []
blocks: [0004]
---

## Context

`mux-bb` currently shells out through blocking helpers (`sh`/`sh?`) in `src/mux/shell.clj`. We want native `babashka.process/process` usage as the default execution model so mux operations can support non-blocking behavior cleanly.

## Acceptance Criteria

- [ ] Add process-native helpers in `src/mux/shell.clj` for spawning and optional waiting.
- [ ] Preserve existing error data shape (`:exit`, `:cmd`, `:out`, `:err`) or document migration.
- [ ] Keep backward compatibility for callers that still require blocking calls.
- [ ] Unit tests cover process helper behavior and failure mapping.
- [ ] README documents the new shell execution model.

## Affected Files

- `src/mux/shell.clj`
- `test/unit/shell_test.clj`
- `README.md`

## Notes

- FCIS rule: keep shell process management at I/O boundary; pure layers unchanged.
- This is prerequisite for making backend operations fully non-blocking (todo 0004).
