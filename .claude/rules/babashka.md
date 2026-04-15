---
description: Babashka project guidance
globs: ["bb.edn", "src/**/*.clj", "test/**/*.clj"]
---

# Babashka Project

This is a **Babashka (`bb`)** project, not generic JVM Clojure.

Read `AGENTS.md` first for project architecture and workflow.
Then read `bb.edn` for task definitions and paths.

## Key differences from JVM Clojure

- No `deftype` / `defrecord` — prefer maps, multimethods, and plain data
- Java interop is limited to what Babashka supports
- Use `*command-line-args*` rather than Java main args patterns
- No AOT compilation or `gen-class`

## Idiomatic libraries

Prefer Babashka-specific libraries over manual alternatives:

- `babashka.process` — shell/process execution
- `babashka.fs` — filesystem operations
- `babashka.cli` — CLI argument parsing (if we add a CLI later)

## API documentation

Do not guess Babashka library APIs. Fetch docs before using unfamiliar bb libraries.

| Library          | API docs URL                                                                  |
|------------------|-------------------------------------------------------------------------------|
| babashka.fs      | `https://raw.githubusercontent.com/babashka/fs/refs/heads/master/API.md`      |
| babashka.process | `https://raw.githubusercontent.com/babashka/process/refs/heads/master/API.md` |
| babashka.cli     | `https://raw.githubusercontent.com/babashka/cli/refs/heads/main/API.md`       |
| babashka builtins| `https://book.babashka.org/`                                                  |
