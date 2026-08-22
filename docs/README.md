# Documentation

Use this directory instead of hunting through dated plans or checkpoint history.

## Start here

1. **[PROJECT-HANDBOOK.md](PROJECT-HANDBOOK.md)** — project orientation, ownership, and contributor rules.
2. **[project/current-state.md](project/current-state.md)** — exact implemented repository boundary for the current snapshot.
3. **[implementation/current-roadmap.md](implementation/current-roadmap.md)** — current execution position.
4. **[superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md](superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md)** — normative canonical catalog identity/fusion architecture.
5. **[superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md](superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md)** — active task-by-task implementation plan.
6. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md)** — verified Phase-7 / Task-42 final certification checkpoint; CCE rollout closed on schema 9.
7. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7-task-41.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7-task-41.md)** — verified Task-41 structured diagnostics checkpoint.
8. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7-task-40.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7-task-40.md)** — verified Task-40 controlled-reversal checkpoint.
9. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7-task-39.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7-task-39.md)** — verified Task-39 background-safety checkpoint.
10. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-6.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-6.md)** — verified Phase-6 Tasks 36–38 checkpoint.
11. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-6-task-36.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-6-task-36.md)** — retained Task-36 orchestration sub-checkpoint.
12. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-5.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-5.md)** — verified Phase-5 Tasks 33–35 durable review checkpoint.
13. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-4.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-4.md)** — verified Phase-4 Tasks 26–32 guarded production auto-merge checkpoint.
14. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-3.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-3.md)** — verified Phase-3 Tasks 22–25 observe-only reconciliation checkpoint.
15. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md)** — verified Phase-1 Tasks 5–11 checkpoint.
16. **[internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md](internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md)** — verified Phase-0 Tasks 1–4 checkpoint.
17. **[internal/checkpoints/discover-semantic-feed-redesign.md](internal/checkpoints/discover-semantic-feed-redesign.md)** — accepted Discover semantic-feed redesign evidence, including Room schema 7 and benchmark results.
18. **[superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md](superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md)** — normative Discover semantic-feed composition; canonical presentation is now supplied by the accepted canonical read path.
19. **[internal/checkpoints/product-ui-redesign.md](internal/checkpoints/product-ui-redesign.md)** — accepted Product UI checkpoint that established the current shell and broader presentation baseline.
20. **[implementation/waves/wave-10-background-sync-auth-and-notifications.md](implementation/waves/wave-10-background-sync-auth-and-notifications.md)** — active next capability wave after CCE closeout.

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

The ReDantotsu-inspired Product UI plan and the 2026-08-19 Discover semantic-feed plan are
completed implementation records. The 2026-08-21 Canonical Catalog Reconciliation & Fusion Engine plan is a completed rollout record.
Phases 0–7 / Tasks 1–42 are verified and closed on Room schema 9. Wave 10 is the active next
capability boundary.

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
