# Documentation

Use this directory instead of hunting through dated plans or checkpoint history.

## Start here

1. **[PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md)** — project orientation, ownership, and contributor rules.
2. **[project/current-state.md](project/current-state.md)** — exact implemented repository boundary for the current snapshot.
3. **[implementation/current-roadmap.md](implementation/current-roadmap.md)** — current execution position.
4. **[superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md](superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md)** — normative canonical catalog identity/fusion architecture.
5. **[superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md](superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md)** — active task-by-task implementation plan.
6. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-2.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-2.md)** — active Phase-2 Tasks 12–21 patch/verification-open checkpoint.
7. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md)** — verified Phase-1 Tasks 5–11 checkpoint evidence; Phase 1 is closed.
7. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md)** — verified Phase-0 Tasks 1–4 checkpoint evidence; Phase 0 is closed.
8. **[internal/checkpoints/discover-semantic-feed-redesign.md](internal/checkpoints/discover-semantic-feed-redesign.md)** — accepted Discover semantic-feed redesign evidence, including Room schema 7 and benchmark results.
9. **[superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)** — normative current Discover composition and semantic-feed contract until the later canonical-read cutover tasks replace feature-local provider selection.
10. **[internal/checkpoints/product-ui-redesign.md](internal/checkpoints/product-ui-redesign.md)** — accepted Product UI checkpoint that established the current shell and broader presentation baseline.
11. **[implementation/waves/wave-10-background-sync-auth-and-notifications.md](implementation/waves/wave-10-background-sync-auth-and-notifications.md)** — planned capability wave after the active canonical-engine track reaches a compatible boundary.

## Normative product and architecture documents

- [Approved product design and current amendments](project/approved-product-design.md)
- [Architecture Baseline 2 design](superpowers/specs/2026-08-09-architecture-baseline-2-design.md)
- [Post-Baseline Wave 06-11 architecture](superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md)
- [Canonical catalog reconciliation and metadata fusion engine](superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md)
- [Discover semantic-feed redesign](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)
- [Requirement coverage](project/requirement-coverage.md)
- [Documentation precedence/governance](project/document-governance.md)
- [Design-system rules](ui/design-system.md)

## Current execution records

The ReDantotsu-inspired Product UI plan is completed and retained as an implementation
record. The 2026-08-19 Discover semantic-feed plan is also completed and retained as the
execution record for the current Discover screen. The active next-work entry is now the
2026-08-21 Canonical Catalog Reconciliation & Fusion Engine plan; Phase 0 Tasks 1–4 and
Phase 1 Tasks 5–11 are verified and closed. Phase 2 Tasks 12–21 are patched with developer Gradle/device/macrobenchmark verification open; Task 22 must not start until Phase 2 is closed.

Wave 01-09 plans are historical delivery records. Wave 10-11 remain planned future
capability work under the approved post-Baseline-2 architecture. The canonical-engine
Phase-1 foundation owns Room schema 9 via `8 -> 9`; Wave 10 notification persistence is therefore
rebased to `9 -> 10`, and Wave 11 enters on schema 10 unless another reviewed migration intervenes.

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
