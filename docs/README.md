# Documentation

Use this directory instead of hunting through dated plans or checkpoint history.

## Start here

1. **[PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md)** — project orientation, ownership, and contributor rules.
2. **[project/current-state.md](project/current-state.md)** — exact implemented repository boundary for the current snapshot.
3. **[implementation/current-roadmap.md](implementation/current-roadmap.md)** — current execution position and the next capability wave.
4. **[internal/checkpoints/discover-semantic-feed-redesign.md](internal/checkpoints/discover-semantic-feed-redesign.md)** — accepted Discover semantic-feed redesign evidence, including Room schema 7 and benchmark results.
5. **[superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)** — normative current Discover composition and semantic-feed contract.
6. **[internal/checkpoints/product-ui-redesign.md](internal/checkpoints/product-ui-redesign.md)** — accepted Product UI checkpoint that established the current shell and broader presentation baseline.
7. **[implementation/waves/wave-10-background-sync-auth-and-notifications.md](implementation/waves/wave-10-background-sync-auth-and-notifications.md)** — next planned capability wave; rebased to enter on Room schema 8.

## Normative product and architecture documents

- [Approved product design and current amendments](project/approved-product-design.md)
- [Architecture Baseline 2 design](superpowers/specs/2026-08-09-architecture-baseline-2-design.md)
- [Post-Baseline Wave 06-11 architecture](superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md)
- [Discover semantic-feed redesign](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)
- [Design-system rules](ui/design-system.md)

## Current execution records

The ReDantotsu-inspired Product UI plan is completed and retained as an implementation
record. The 2026-08-19 Discover semantic-feed plan is also completed and retained as the
execution record for the current Discover screen. Neither plan is the next-work entry point;
use `implementation/current-roadmap.md`.

Wave 01-09 plans are historical delivery records. Wave 10-11 remain planned future
capability work under the approved post-Baseline-2 architecture. Wave 10 now enters on
Room schema 8 and, if its notification-delivery persistence is implemented as planned,
advances the database to schema 9.

## Public/plugin documentation

`plugin-sdk/` contains the SDK-facing protocol/versioning, JavaScript runtime, package,
repository-index, and contract-testing rules. It describes the current Baseline-2
JavaScript-only plugin contract; historical selector/declarative-runtime material is not
an active SDK contract.

## Evidence and history

- `internal/checkpoints/` — reviewed checkpoint evidence; do not rewrite historical `NOT RUN` into `PASS` from later source changes.
- `internal/archive/` — raw historical planning/review packages retained for provenance.
- `superpowers/specs/` — accepted design records. A later scoped spec may supersede only part of an earlier design; read the supersession notices.
- `superpowers/plans/` — implementation plans and execution records. A completed plan is not automatically the current roadmap.

## One rule

If two documents seem to disagree, read `project/document-governance.md` before acting.
