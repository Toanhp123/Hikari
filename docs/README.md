# Documentation

Use this directory instead of hunting through dated plans or checkpoint history.

## Start here

1. [PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md) - project orientation, ownership, and contributor rules.
2. [project/current-state.md](project/current-state.md) - exact implemented repository boundary.
3. [implementation/current-roadmap.md](implementation/current-roadmap.md) - canonical current execution position and resume route.
4. [project/document-governance.md](project/document-governance.md) - source precedence when records disagree.
5. [project/approved-product-design.md](project/approved-product-design.md) - approved product/domain baseline.

## Normative product and architecture documents

- [Approved product design and current amendments](project/approved-product-design.md)
- [Architecture Baseline 2 design](superpowers/specs/2026-08-09-architecture-baseline-2-design.md)
- [Post-Baseline Wave 06-11 architecture](superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md)
- [Canonical catalog reconciliation and metadata fusion engine](superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md)
- [Canonical engine performance and durability](superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md)
- [Whole-app performance big update](superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md)
- [Discover semantic-feed redesign](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)
- [Design-system rules](ui/design-system.md)

## Current execution records

The canonical execution position is `implementation/current-roadmap.md`. Read only its `Current
position` section first, then follow the checkpoint and owning plan named there. Do not infer next work
from the newest dated file, a completed Wave, or an older readiness record.

Historical Wave, CCE, HES, Reader, performance, and other completed plans/checkpoints remain audit
evidence; load them only when the active task or a concrete root-cause trail requires them.

## Public/plugin documentation

`plugin-sdk/` contains the SDK-facing protocol/versioning, JavaScript runtime, package,
repository-index, and contract-testing rules. It describes the current Baseline-2 JavaScript-only
plugin contract; historical selector/declarative-runtime material is not an active SDK contract.

## Evidence and history

- `internal/checkpoints/` - reviewed checkpoint evidence; do not rewrite historical `NOT RUN` into `PASS`.
- `internal/archive/` - raw historical planning/review packages retained for provenance.
- `superpowers/specs/` - accepted design records; later scoped amendments may supersede only part of an older design.
- `superpowers/plans/` - implementation plans and execution records; a completed plan is not automatically the current roadmap.

## One rule

If two documents seem to disagree, read `project/document-governance.md` before acting.
