# Documentation

Use this directory instead of hunting through dated plans or checkpoint history.

## Start here

1. [PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md) - project orientation, ownership, and contributor rules.
2. [project/current-state.md](project/current-state.md) - exact implemented repository boundary.
3. [implementation/current-roadmap.md](implementation/current-roadmap.md) - current execution position.
4. [implementation/waves/wave-10-background-sync-auth-and-notifications.md](implementation/waves/wave-10-background-sync-auth-and-notifications.md) - active next-wave plan, rebased to schema 10 entry and `10 -> 11` notification persistence.
5. [superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md](superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md) - current schema-10 durability authority.
6. [internal/checkpoints/wave-10-entry-readiness-2026-08-24.md](internal/checkpoints/wave-10-entry-readiness-2026-08-24.md) - accepted Wave 10 entry evidence.
7. [internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md) - verified historical schema-9 CCE closeout evidence.

## Normative product and architecture documents

- [Approved product design and current amendments](project/approved-product-design.md)
- [Architecture Baseline 2 design](superpowers/specs/2026-08-09-architecture-baseline-2-design.md)
- [Post-Baseline Wave 06-11 architecture](superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md)
- [Canonical catalog reconciliation and metadata fusion engine](superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md)
- [Canonical engine performance and durability](superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md)
- [Discover semantic-feed redesign](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)
- [Design-system rules](ui/design-system.md)

## Current execution records

The Product UI, Discover semantic-feed, and Canonical Catalog Reconciliation & Fusion Engine plans
are completed implementation records. CCE Phases 0-7 / Tasks 1-42 remain verified and closed on
their Room schema-9 boundary.

The later canonical performance/durability patch advances current source to schema 10 with queue
leases and a transactional catalog-change outbox. Its policy, full host, and API 26/API 37 entry
verification is accepted. Wave 10 is ready to start from its active plan.

Wave 01-09 plans are historical delivery records. Wave 10-11 remain planned future capability work
under the approved post-Baseline-2 architecture. Canonical foundation owns `8 -> 9`; canonical
durability owns `9 -> 10`; Wave 10 notification persistence owns `10 -> 11`; Wave 11 enters on
schema 11 unless another reviewed migration intervenes.

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
