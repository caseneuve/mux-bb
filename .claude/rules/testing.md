---
description: Testing conventions and TDD workflow
globs: ["test/**/*.clj", "test/**/*.edn"]
---

# Testing

## TDD: RED → GREEN → REFACTOR

Never write implementation before a failing test. Never write tests that pass immediately.

## Small iterations

1. Write one focused failing test (RED)
2. Run it immediately — prove it fails
3. Implement only what is needed (GREEN)
4. Run it immediately — prove it passes
5. Refactor while green
6. Commit checkpoint, next slice

Never batch implementation ahead of tests.
Never postpone RED/GREEN verification to end-of-session runs.

## Test categories

### Unit tests (`test/unit/`)

- Test all pure functions: arg building, parsing, detection, session derivation, md5
- No tmux or cmux binary needed
- Fast, run on host: `bb test:unit:host`
- Isolated in podman: `bb test:unit`

### E2E tests (`test/e2e/`)

- end2edn declarative format (EDN)
- tmux backend lifecycle only (real tmux in podman)
- cmux E2E not possible (macOS GUI app — pure functions covered by unit tests)
- Run in podman: `bb test:e2e` (build image first: `bb build:test-image`)

## end2edn conventions

- Prefer declarative fixtures (`:given :fixtures`) over shell setup
- Keep `:when :run` for actual behavior under test, not fixture construction
- Use `:tags` for focused runs during development
- Use `--explain` to debug compiled test structure

## Containerized testing

- E2E tests run in podman, never on host
- Container has tmux installed, read-only source mount, tmpfs for `/tmp`
- `END2EDN_SAFE_ENV=1` set in container image

## What to test

Good unit test targets in this project:
- `build-cmux-args` — every operation, every parameter combination
- `parse-workspaces` — normal output, edge cases, empty/nil
- `escape-send-text` — newlines, tabs, mixed
- `detect-mux` — all env combinations
- `derive-session-info` — determinism, uniqueness, format
- `build-target` — format
- `md5-hex`, `md5-short` — known digests, length
- `sh`, `sh?` — success, failure, missing command
- `make-backend` structure — protocol keys present, error on unregistered windows
