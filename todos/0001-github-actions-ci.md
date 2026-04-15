---
title: Add GitHub Actions CI workflow
status: open
priority: medium
type: chore
labels: []
created: 2026-04-15
parent: null
blocked-by: [end2edn public release]
blocks: []
---

## Context

mux-bb is on GitHub (caseneuve/mux-bb) but has no CI. The `test:ci` bb task
already exists and runs unit + e2e tests directly (no podman). However, e2e
tests require end2edn as a dependency (`END2EDN_ROOT`), and end2edn is
currently pre-MVP and private. CI can't clone it.

**Blocked until end2edn is publicly released** (or at minimum available as a
public git dep).

## Acceptance Criteria

- [ ] `.github/workflows/test.yml` runs on push and PR
- [ ] Installs babashka and tmux in the runner
- [ ] Clones end2edn (public git dep by then) and sets `END2EDN_ROOT`
- [ ] Runs `bb test:ci` (unit + e2e)
- [ ] Badge in README.md

## Affected Files

- `.github/workflows/test.yml` — new workflow
- `README.md` — add CI badge

## Notes

- `test:ci` task already guards with `CI` env var — ready for GitHub Actions
- Runner needs: babashka, tmux, end2edn source on disk
- Consider caching babashka install between runs
- Once end2edn has a stable git sha/tag, reference it in the workflow clone step
- Could add a unit-only interim workflow that doesn't need end2edn — but the
  full suite is only 4 e2e cases, not worth splitting the CI story
