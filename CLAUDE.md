# CLAUDE.md

Before proposing or implementing anything in this repository, read `ARCHITECTURE.md` (the design doc) and, if present at the repo root, `PROJECT_CONTEXT.md` (personal context and working preferences — intentionally gitignored, not always present).

## Workflow

- One branch per implementation step, branched from an up-to-date `main` (e.g. `feature/flyway-initial-schema`).
- Never commit directly to `main`.
- Propose the scope of a step and wait for confirmation before implementing it. Keep steps small.
- End each step with a Pull Request describing what changed and why; `ci.yml` must pass before merge.
- Before proposing the next step, check the repository's actual state (`git log`, `git branch -a`, open PRs) rather than assuming what's already done.

## Conventions

- All code, comments, configuration, commit messages, and documentation are in English — the one exception is data captured from emails, kept in its original language.
- Hexagonal architecture: `domain` and `application` stay free of framework/vendor dependencies; integrations live behind `ports` interfaces, implemented in `adapters`.
- Explain the reasoning behind non-trivial technical decisions, not just the resulting code — this is a study project, understanding matters as much as the result.
