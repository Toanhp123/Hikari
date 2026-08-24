# Canonical Catalog Reconciliation & Fusion — Phase 7 Task 39 Checkpoint

Date: 2026-08-22
Status: **VERIFIED — TASK 39 CLOSED; PHASE 7 REMAINS OPEN**
Scope: implementation-plan Task 39 only; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Prior phase evidence: `canonical-catalog-reconciliation-fusion-phase-6.md`

## Accepted implementation boundary

### Durable queue and retry safety

- `canonical_engine_work` remains the single durable source of truth for background retry timing; WorkManager only wakes and serializes execution.
- Queue completion/retry/block operations are conditional on the exact persisted snapshot so stale foreground/background work cannot delete or overwrite a newer dirty event.
- Runnable `next_attempt_at_epoch_millis` also provides a schema-free monotonic revision for otherwise-identical dirty events. This closes the same-millisecond ABA/lost-dirty case without consuming Room schema 10.
- Retry backoff is explicit and bounded at 5/10/20 minutes onward with a six-hour cap. Parked invariant rows remain non-runnable and normal dirty coalescing does not silently reopen them.
- Merge rekeying preserves parked failures, unions encoded `POST_MERGE_DERIVED` requirements, and fails closed as `canonical.maintenance.coalesced_invariant` if different parked invariant classes coalesce.

### Background maintenance and policy safety

- `CanonicalEngineMaintenanceService` / `CanonicalEngineWorkProcessor` drain bounded ready work for Fusion rebuild, reconciliation reevaluation, policy reevaluation, and post-merge derived repair using Android-free domain boundaries.
- Unsupported required-policy versions and persisted future policy versions fail closed before an older binary can mutate canonical state. Bounded safety logic can requeue only the policy parks that become supported by a later binary; semantically unchanged Fusion can repair stale degraded health.
- Safety scans are bounded to stale policy state, pending reconciliation cases whose evidence may have changed, recoverable policy parks, and redirect inconsistencies (missing target, chain, or self-redirect). They do not perform an all-pairs Story scan.
- Semantic/invariant failures park durable work and mark canonical health degraded best-effort; one parked Story does not poison the periodic safety chain.

### App-owned scheduling and derived repair

- `WorkManagerCanonicalEngineWorkScheduler` keeps Android scheduling in `:app`. The canonical domain modules do not import `androidx.work`.
- Drain wakeups use one serial unique chain with an in-process coalescing gate so a dirty event arriving while a drain is finishing is not lost. Future retry wakeups are named by durable retry timestamp and use `KEEP`, preventing an older report from delaying an earlier wake.
- Initial drain and daily-safety registration are deferred until after the first Compose frame in `MainActivity`; Task 39 does not add work to `Application.onCreate()` and preserves the repository cold-start policy.
- `POST_MERGE_DERIVED` reasons durably encode the exact required operations while retaining legacy parsing. Local chapter reaggregation completes before network work; when both network stages are required, Mapping is chained before Chapter Sync.
- Foreground Fusion and durable reconciliation results wake the same drain path when background work may remain, while the queue snapshot remains authoritative if scheduling itself fails.

## Verification evidence

The final developer-checkout verification on 2026-08-22 passed the focused Task-39 gates:

```text
:catalog:testDebugUnitTest
  CanonicalEngineMaintenanceServiceTest
  CanonicalEngineOrchestratorTest
  CanonicalEngineWorkTest
  CanonicalFusionServiceTest
  CatalogReconciliationServiceTest
  BUILD SUCCESSFUL

:app:testDebugUnitTest
  CanonicalEngineWorkerTest
  PostMergeDerivedWorkDispatcherTest
  BUILD SUCCESSFUL

:storage:room:connectedDebugAndroidTest
  RoomCanonicalEngineStateTest
  RoomCanonicalEngineMaintenanceReaderTest
  17/17 tests PASS
  device: Redmi Note 9S - 15
  BUILD SUCCESSFUL
```

`ChapterReaggregationServiceTest` had already passed in the preceding Task-39 verification pass; the final Detekt-only cleanup did not modify `:chapters`.

The canonical repository gate then passed end-to-end:

```text
./scripts/verify.sh
  Canonical engine orchestration policy verified
  Structural hard policies verified
  Current architecture verified: 14 production modules, 1 android-test module, Room schema 1..9
  :verifyApplicationIdentity PASS
  :verifyModuleBoundaries PASS
  :detekt PASS (warnings only; no Task-39 errors)
  Android lint reports generated successfully
  BUILD SUCCESSFUL in 1m 44s
  628 actionable tasks: 125 executed, 503 up-to-date
  Room schema export remained stable during verification
```

The structural report continues to list existing review candidates/threshold warnings, including `CanonicalEngineMaintenanceService` and `RoomCanonicalEngineWorkRepository` size/length review candidates, but no hard policy fails and no Detekt suppression or threshold relaxation was introduced for Task 39.

## Verification-fix history

The first developer pass was initially blocked by a Windows lock on `catalog/.../classes.jar`; after the stale process/build lock was cleared, real Task-39 tests ran. Those runs exposed three fixture/contract defects rather than a production architectural failure: a dispatcher assertion still expected only `StoryId`, a maintenance DTO rejected persisted legacy policy version `0`, and an identical-dirty race test queried at timestamp `0` even though the queue intentionally advanced its monotonic revision to `1ms`. The fixes were applied narrowly and the focused gates passed.

The first canonical `verify.sh` after those fixes then found 12 Task-39 Detekt errors (`FunctionParameterNaming`/`UnusedParameter`, `ReturnCount`, `LoopWithTooManyJumpStatements`, and `MagicNumber`). They were removed by behavior-preserving control-flow/constant cleanup with no suppression. The focused Catalog/App/Room gates were rerun on that final tree before the successful canonical repository gate above.

## Accepted residual boundary

Task 39 deliberately does **not** add a transactional outbox or Room migration. It drains/retries durable work already recorded by the canonical engine and performs bounded safety checks, but it does not claim to make every arbitrary post-commit callback atomically durable. The narrow commit-to-callback process-death residual documented in Phase 6 therefore remains explicit unless a later separately reviewed boundary closes it. Task 39 also does not implement automatic detach/split/reversal, observability truth storage, or Wave-10 background/auth/notification behavior.

## Exit

Task 39 is accepted. **Phase 7 remains open.** Room schema 9 remains current. The active next canonical-engine step is **Task 40: controlled reverse planning and atomic split for provably safe historical merges**.
