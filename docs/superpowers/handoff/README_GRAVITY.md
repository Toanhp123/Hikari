# Hikari UI Rebuild — Gravity execution handoff (reviewed v4.2)

**Status:** Planning documents audited against the **2026-10-21 source ZIP**; **no Flutter execution/build or live branch validation has been performed**. This package is a reviewed execution input, not a report that the app or plans already pass.

Live Phase 00 evidence: [baseline runs](../../ui/BASELINE_RUNS.md),
[migration matrix](../../ui/MIGRATION_MATRIX.md),
[accepted ADR-026](../../decisions/ADR-026-ui-foundation-rebuild.md).
The historical package status above is not the current execution result.
On 2026-10-23 the owner **accepted G0–G2 for continuation** and authorized
Phase 03 with a focused Escape/MenuAnchor preflight. Phase 03 subsequently
completed implementation; **G3 was OWNER-ACCEPTED for continuation** and
Phase 04 Task 01 authorized after checking the zero-usable-width Grid regression.
See [G3 decision](../../roadmap/UI_FOUNDATION_G3_OWNER_ACCEPTANCE_2026-10-23.md)
and [Phase 03 execution](../../roadmap/UI_FOUNDATION_PHASE_03.md). The original G0 physical
evidence exception and execution-time incomplete G1/G2 checks are preserved in
[dated acceptance](../../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md).
Do not convert historical missing evidence into technical PASS.

## Package placement

The archive has one top-level wrapper folder. Its inner `docs/` paths are **repository-root relative**. Before use, compare the inner `docs/` with live repository documentation; copy or merge deliberately instead of nesting a second `Hikari_UI_Rebuild_Reviewed_...` folder under the project or overwriting unrelated files. Keep `README_GRAVITY.md`, `AUDIT_REPORT.md` and `VALIDATION_RESULTS.md` accessible alongside the plans.

## Read these files in order

1. `docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md` — scope, dependency graph, canonical type ownership and G0–G7 gates.
2. `docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md` — target architecture, design principles, UI contracts (v4.2).
3. `docs/superpowers/plans/BASELINE_ROUTE_INVENTORY.md` — **snapshot** list of 19 page files and known Source Picker paths; MUST rescan live checkout.
4. `docs/superpowers/plans/2026-10-22-hikari-ui-00-baseline-and-adr.md` — Phase 00 execution record; owner-authorized Phase 01 is the next active plan.
5. Active next plan: `docs/superpowers/plans/2026-10-22-hikari-ui-04-browse-detail-migration.md`.
   G0–G3 are owner-accepted for continuation; G4 is the next separate gate.
6. `AUDIT_REPORT.md` — confirmed repairs, residual limits and review rationale.

## Authority and conflict rules

- Target UI architecture: v4.2 **spec**. Execution dependencies, task boundaries and checkpoints: **master + active phase plan**. Once accepted, superseding ADR and updated canonical project docs must stay aligned with them.
- An existing UI ADR may be superseded, but **domain/persistence/source identity/reader progress and native plugin trust/OS consent are not implicitly authorized to change**.
- If two documents disagree, **STOP** for the affected task. Do not silently pick the convenient instruction; update the spec, master, phase plan and ADR together. Write a dated entry in the decision ledger.
- API names in the docs are **target signatures until validated against actual Flutter/Dart**. `useRootNavigator` is a required explicit argument to both modal families and the adaptive host. Framework defaults differ; they are forbidden as implicit selection. Unknown dismissal causes must be `routeDismissed`, not guessed. Do not invent public APIs beyond the stated contracts. Lock a public API with compile/widget tests before downstream consumers migrate. Changes to cross-phase public API require explicit owner review.
- `HikariDrawer` is a navigation component. `HikariDialog` and `HikariBottomSheet` are independent transient surface families. `sidePanel` is **not a v4.2 presenter enum mode**; implement it in a future reviewed change only when a real use case and tests exist.

## Working conventions (user constraints)

- Work directly in the existing current branch. **Do not** create branches, worktrees, isolated agents, commits, pushes or merges automatically. Do not discard user's uncommitted edits.
- First inspect actual source/versions/working tree/ADR numbers; the ZIP is not current Git truth. Record exact path changes if live source differs. If the project differs substantially, report the mismatch before modifying the affected task.
- Implement **one task at a time**, one scoped diff and an explicit self-review. For changed behaviors/API, verify focused RED→GREEN; for characterization/manual evidence, record real baseline results rather than invent RED.
- Format **only changed Dart files** in implementation tasks; final tree formatting check must be read-only (`fvm dart format --output=none --set-exit-if-changed lib test`). Run focused tests/analyzer plus the indicated phase gates. If a file listed in a plan is missing, determine whether it's a new test or a code-movement path and document the resolution.
- Do not add packages or state-management frameworks without a decision. Do not replace system consent or plugin trust with a UI boolean.
- Re-run inventory and update `docs/ui/MIGRATION_MATRIX.md` with actual callers and target owners after each phase. Transitional token export-forwarders can only re-export authoritative tokens and must be removed no later than Phase 07 Task 01.
- At every task end give a short report: task ID, changed files, tested contract, RED evidence (or why not applicable), GREEN evidence, analyzer result, remaining callers, blockers, platform checks not performed, ownership self-review.

## Gates and acceptance

- G0–G7 remain **technical acceptance gates**. G0–G2 have **owner acceptance for continuation**, not retrospective independent
  technical PASS for undocumented hardware checks. Phase 03 has subsequently
  been completed and G3 owner-accepted for continuation; Phase 04 Task 01 is
  authorized after new focused grid tests. G4–G7 remain separate gates. Keep
  absent logs/traces `NOT RUN`/`NOT MEASURED` in dated execution records.
- Android Redmi Note 9S native workflows, Android TalkBack/installer, Windows keyboard/resize and matched real-device performance require device-level evidence. Widget tests cannot prove them.
- Do not auto-approve a new UI ADR on behalf of the product owner. Prepare it, show the changed decision clauses, obtain approval, then build against it.
- If the full scope cannot be completed, preserve compilability, report the exact incomplete migration, and do not declare G7.

## First instruction to Gravity

> Review this handoff against the *current* Hikari checkout and the Phase 01/02 execution reports.
> Confirm Phase 03's Escape regression remains GREEN and run the scoped Grid
> regression for unusable local width. Then execute **Phase 04 Tasks 01–08**
> only as authorized, preserve domain/cache/source identity, and stop at G4. Never create agents,
> commits or worktrees automatically.
