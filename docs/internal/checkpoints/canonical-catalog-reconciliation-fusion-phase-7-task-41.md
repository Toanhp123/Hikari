# Canonical Catalog Reconciliation & Fusion — Phase 7 Task 41 Checkpoint

Date: 2026-08-23
Status: **VERIFIED — TASK 41 CLOSED; PHASE 7 REMAINS OPEN**
Scope: implementation-plan Task 41 only; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Prior Task-40 evidence: `canonical-catalog-reconciliation-fusion-phase-7-task-40.md`

## Accepted implementation boundary

### Pure policy, boundary-owned diagnostics

- The accepted implementation follows the service-boundary observability design: Fusion/Reconciliation policy stays deterministic and does not own a diagnostics sink.
- `CatalogFusionEngine` exposes bounded primary-selection decision metadata, while `CanonicalFusionService` records primary-selection, field-provenance, and generation-validation outcomes after policy evaluation. Reconciliation services record selection/correction decisions using the reason codes, policy version, and evidence fingerprints already owned by reconciliation.
- Room owners emit state-transition diagnostics only where persistence history makes the transition knowable: `RoomStoryGraphMergeCoordinator` owns merge-blocked/merge-committed traces and `RoomReconciliationCaseRepository` owns resolved-case reopen traces.
- Maintenance diagnostics consume bounded Room queries through `CanonicalEngineMaintenanceReader`; SQL/persistence facts remain in `:storage:room`, while `:catalog` owns invariant classification/orchestration.

### Bounded, fail-open observability

- `CanonicalDecisionTrace` is intentionally small: trace kind, bounded Story/source IDs, policy versions, reason codes, evidence fingerprints, and optional `CanonicalFieldKey`. It does not serialize raw descriptions, full metadata, progress state, mapping payloads, plugin JSON, cookies, cursor values, or other private/raw content.
- `CanonicalDiagnostics` sanitizes and bounds IDs/tokens before forwarding them and catches sink exceptions. Diagnostics failure therefore cannot change reconciliation/fusion decisions, generation promotion, Room merge results, or maintenance outcomes.
- App composition owns the Android sink. Debug builds emit the already-sanitized trace to Logcat; non-debug builds bind `NoOpCanonicalDiagnosticsSink`. `:catalog` remains free of Android logging dependencies.
- Cases, canonical generations, and merge/reversal audit records remain the persistent domain truth. Task 41 adds no diagnostics table, no event-sourced canonical state, and no Room migration.

### Covered decision/invariant questions

The accepted traces cover all Task-41 diagnostic kinds:

- `RECONCILIATION`: why a candidate became auto-merge/review/separate/no-match, including eligibility and bounded evidence fingerprints.
- `PRIMARY_SELECTION`: pinned/fallback/initial/previous-missing/previous-ineligible/best-unchanged/materially-better/hysteresis-retained reasoning.
- `FIELD_FUSION`: selected/union contributors and field identity from immutable canonical provenance.
- `GENERATION_FAILED`: validation/promotion failure context without dumping the candidate payload.
- `MERGE_BLOCKED` / `MERGE_COMMITTED`: merge disposition at the Room coordinator boundary.
- `CASE_REOPENED`: transition from a resolved reconciliation revision to a new pending revision when evidence/policy changes.
- `INVARIANT_VIOLATION`: bounded maintenance findings for invalid redirects, invalid source ownership, impossible duplicate ownership, provenance outside the Story source set, and orphaned redirect work.

Invariant reason codes keep dynamic Story/source IDs out of the code string; contextual IDs live in the bounded ID fields instead.

## Verification evidence

The developer checkout passed the focused Task-41 gates after the final test/lint corrections:

```text
:catalog:testDebugUnitTest
  CanonicalDiagnosticsTest
  app.openstory.catalog.reconciliation.*
  app.openstory.catalog.fusion.*
  CanonicalEngineMaintenanceServiceTest
  CanonicalEngineMaintenanceDiagnosticsTest
  BUILD SUCCESSFUL

:storage:room:connectedDebugAndroidTest
  RoomCanonicalCatalogRepositoryTest
  RoomCanonicalEngineMaintenanceReaderTest
  RoomStoryGraphMergeDiagnosticsTest
  17/17 tests PASS
  device: Redmi Note 9S - 15
  BUILD SUCCESSFUL
```

The canonical repository gate was then rerun after the final Task-41 Detekt-only control-flow cleanup and the developer confirmed it green:

```text
./scripts/verify.sh
  repository/static architecture policies PASS
  Detekt PASS (warnings/review candidates only)
  module-boundary/lint/build verification PASS
  Room schema remains 1..9 / current schema 9
  BUILD SUCCESSFUL
```

No Detekt suppression or threshold relaxation was added for Task 41. Existing structural-review candidates remain review signals rather than Task-41 hard-policy failures.

## Verification-fix history

The first focused Catalog run exposed one test-expectation regression in `CatalogFusionEnginePrimaryTest`: the test incorrectly expected `CHALLENGER_MATERIALLY_BETTER` for a previous source that was already `UNAVAILABLE`. Production policy correctly classifies that path as `PREVIOUS_INELIGIBLE`; the fixture was split into its own assertion and production semantics were not changed. The rerun passed.

The first canonical `./scripts/verify.sh` then found exactly three Task-41 Detekt `ReturnCount` errors: two in the extracted primary-selection policy and one in Room invariant accumulation. They were removed with behavior-preserving single-return/guarded-accumulation refactors. No suppression was introduced, and the focused Catalog/Room gates plus canonical repository gate were rerun on the corrected tree.

## Accepted residual boundary

Task 41 is an observability/explainability layer, not a repair engine or telemetry subsystem. It does not persist traces, add analytics transport, expose a UI diagnostics console, automatically repair ambiguous canonical corruption, refactor the catalog module graph, alter controlled reversal policy, or consume schema 10. Existing deterministic transactions remain the only places allowed to repair state automatically; otherwise invariants are reported/degraded according to the owning maintenance policy.

## Exit

Task 41 is accepted. **Phase 7 remains open.** Room schema 9 remains current. The active next canonical-engine step is **Task 42: update governance/docs and run the final acceptance, migration, UI, and performance matrix**.
