# Hikari Performance Wave 3 — Canonical Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make canonical reconciliation/fusion consume coherent bounded inputs and guarantee one durable execution owner or join path per work item, while separating interactive settlement from historical recovery backlog.

**Architecture:** Introduce coherent fusion snapshots/currentness tokens, bulk reconciliation reads, a process single-flight layered on the durable lease repository, and a unified `CanonicalWorkExecutor` used by both foreground orchestrator and workers. Coalesce final fusion by dirty revision and keep historical outbox materialization in the maintenance lane.

**Tech Stack:** Kotlin Coroutines, Room transactions, existing canonical durable work/outbox model, WorkManager/scheduler.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Durable queue/lease transitions remain the crash-recovery source of truth.
- Stale completion checks remain defense-in-depth; they are not a substitute for execution ownership.
- Reconciliation processes all required evidence changes; only final fusion may coalesce.
- Foreground waits are cancellable and bounded by lease semantics; no busy-spin.

---

### Task 1: Add coherent fusion input and thin promotion expectation

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalCatalogRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionInput.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionService.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/fusion/CanonicalFusionServiceTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`

**Interfaces:**

```kotlin
suspend fun fusionInput(storyId: StoryId): CanonicalFusionInput?
suspend fun persistCandidateIfCurrent(
    expectation: CanonicalPromotionExpectation,
    candidate: CanonicalGenerationCandidate,
): CanonicalPromotionResult
```

- [ ] **Step 1: Add service test** counting repository calls and requiring one coherent fusion-input load for a successful rebuild.
- [ ] **Step 2: Add Room test** that updates multiple canonical/source tables transactionally and verifies `fusionInput()` returns one coherent generation/revision/source snapshot.
- [ ] **Step 3: Implement `CanonicalFusionInput` mapping** in one Room transaction, reusing compact/persisted evidence fingerprints where semantically identical.
- [ ] **Step 4: Define `CanonicalPromotionExpectation`** with identity/canonical/source-evidence revisions needed for stale-currentness validation; keep it deliberately thinner than full sources.
- [ ] **Step 5: Refactor `CanonicalFusionService.rebuild()`** to consume one input and one thin promotion call; remove repeated `state()/sourceRecords()/activeGeneration()/sourcePreference()/identityRevision()` reads where redundant.
- [ ] **Step 6: Preserve obsolete-generation cleanup** but do not rehydrate full sources to decide it.
- [ ] **Step 7: Run fusion/Room canonical suites**.
- [ ] **Step 8: Commit** `perf(canonical): fuse from one coherent input`.

---

### Task 2: Replace canonical trigger-plus-double-read observation with coherent reads

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`

**Interfaces:**
- `observeStory` and bounded ready-state observations use invalidation flow only as trigger; each emission is built by one transactional resolved read.

- [ ] **Step 1: Add regression test** performing one transaction that changes Story/canonical/source/generation state and assert no externally visible mixed-generation state.
- [ ] **Step 2: Add read-count characterization** proving one invalidation cycle does not perform trigger payload queries plus duplicate full rehydration.
- [ ] **Step 3: Refactor observers** to watch exact relevant tables through `InvalidationTracker.createFlow(...)`, then `mapLatest { database.withTransaction { readResolved... } }`.
- [ ] **Step 4: Apply semantic `distinctUntilChanged()` after the coherent read**.
- [ ] **Step 5: Run canonical Room/Discover/Library affected tests**.
- [ ] **Step 6: Commit** `perf(canonical): publish coherent reactive snapshots`.

---

### Task 3: Add bulk reconciliation evidence access and remove batch-via-point loops

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationMaintenance.kt`
- Consumes interface from Wave 2: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEvidenceRepository.kt` from Wave 2
- Test: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationServiceTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/RetroactiveReconciliationTest.kt`

**Interfaces:**
- Uses `evidenceForStories(storyIds)` once per candidate/reevaluation batch.

- [ ] **Step 1: Add call-count tests** showing `K` candidates do not cause `K` source-record repository round-trips and reevaluating `S` sources does not reload `S` sources inside each outer iteration.
- [ ] **Step 2: Load candidate Story identities/evidence in bulk** and construct the pure reconciliation inputs in memory.
- [ ] **Step 3: For Story reevaluation**, load all source evidence once and reuse it across per-source decisions.
- [ ] **Step 4: Preserve review-case generation and correction semantics exactly**.
- [ ] **Step 5: Run reconciliation/retroactive/merge suites**.
- [ ] **Step 6: Commit** `perf(canonical): reconcile story evidence in bulk`.

---

### Task 4: Add specific durable claim support and in-process single-flight

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineWorkRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalWorkSingleFlight.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineStateTest.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalWorkSingleFlightTest.kt`

**Interfaces:**

```kotlin
suspend fun claimSpecific(key: CanonicalWorkKey, nowEpochMillis: Long): CanonicalEngineWorkItem?
```

- [ ] **Step 1: Add durable-state tests** for claim-specific success, already-leased result, lease expiry/reclaim, dirtied-while-leased, stale completion, and two repository instances racing.
- [ ] **Step 2: Implement one atomic Room transaction/query path** for `claimSpecific` using the same lease rules as `claimReady`.
- [ ] **Step 3: Implement `CanonicalWorkSingleFlight`** keyed by work key; concurrent in-process callers share one `Deferred` and cancellation of a waiter does not cancel the owner unless owner scope is cancelled.
- [ ] **Step 4: Add cleanup tests** proving completed/failed keys are removed from the single-flight map.
- [ ] **Step 5: Run durable state/single-flight suites**.
- [ ] **Step 6: Commit** `perf(canonical): add durable specific claims and single flight`.

---

### Task 5: Introduce unified CanonicalWorkExecutor for foreground and worker

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalWorkExecutor.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWorkProcessor.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Modify: DI module providing canonical orchestration if required
- Test: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceServiceTest.kt`

**Interfaces:**

```kotlin
interface CanonicalWorkExecutor {
    suspend fun executeOrJoin(key: CanonicalWorkKey): CanonicalWorkExecutionResult
    suspend fun executeReady(limit: Int): CanonicalWorkDrainResult
}
```

- [ ] **Step 1: Add concurrency regression** reproducing current X5 race: foreground and worker target the same fusion work; expected new result is one processor invocation.
- [ ] **Step 2: Implement executor**: single-flight owner attempts durable claim, processes claimed work, completes durable transition, and waiters join the result.
- [ ] **Step 3: If the durable row is leased by another process/owner**, observe/poll durable state with bounded cancellable delay until completion or lease expiry, then retry claim under repository rules; never run unclaimed duplicate work.
- [ ] **Step 4: Make existing worker processor delegate to `executeReady()`** and foreground orchestrator use `executeOrJoin()`.
- [ ] **Step 5: Keep stale transition handling and diagnostics** as correctness checks.
- [ ] **Step 6: Run orchestrator/maintenance/Room state tests**.
- [ ] **Step 7: Commit** `perf(canonical): unify foreground and worker execution ownership`.

---

### Task 6: Remove historical outbox materialization from the interactive path

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Keep/modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceService.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceServiceTest.kt`

**Interfaces:**
- Foreground orchestration marks/executes only work keys caused by the current evidence changes.
- Maintenance retains bounded `outbox.materializePending(limit)` responsibility.

- [ ] **Step 1: Add regression test** with `Q` historical outbox events plus one current Search evidence change; assert foreground orchestration does not materialize historical events before settling the current key.
- [ ] **Step 2: Remove `outbox.materializePending(OUTBOX_MATERIALIZE_LIMIT)` from interactive orchestration**.
- [ ] **Step 3: Ensure current transaction/outbox event creates or marks the directly affected durable work key** through an explicit current-change path; do not rely on global backlog materialization for correctness.
- [ ] **Step 4: Keep historical outbox recovery exclusively in maintenance/worker service** with bounded batch size.
- [ ] **Step 5: Test crash/recovery scenario** proving an event not processed foreground is still materialized later by maintenance.
- [ ] **Step 6: Commit** `perf(canonical): decouple foreground settlement from recovery backlog`.

---

### Task 7: Coalesce final fusion and bound Search settlement concurrency

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalForegroundPolicy.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`

**Interfaces:**
- Default foreground canonical concurrency = 4.
- Work keys are deduplicated/coalesced by final resolved Story + work type + revision semantics.

- [ ] **Step 1: Add test** where multiple evidence changes in one logical batch affect the same Story and assert one final fusion execution for the final dirty revision.
- [ ] **Step 2: Preserve per-evidence reconciliation** but collect/deduplicate resulting fusion work keys before execute/join.
- [ ] **Step 3: Add bounded-concurrency helper/policy** and update Search settlement from serial loop to max 4 in-flight canonical settlements.
- [ ] **Step 4: Add test** proving max concurrency never exceeds 4 and result ordering/publication remains deterministic.
- [ ] **Step 5: Run Search/orchestrator/fusion tests**.
- [ ] **Step 6: Commit** `perf(canonical): coalesce fusion and bound foreground settlement`.

---

### Task 8: Unify Discover bootstrap/manual refresh single-flight

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipelineTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipelineTest.kt`

**Interfaces:**
- One refresh owner represents automatic bootstrap and manual refresh pipeline execution.

- [ ] **Step 1: Add regression**: bootstrap is in flight, user triggers manual refresh, fake service must observe one refresh execution rather than two.
- [ ] **Step 2: Replace separate job guards** with one refresh single-flight/join state; define whether manual intent marks UI refresh state while joining the same underlying work.
- [ ] **Step 3: Preserve supersession/current-generation rules** from existing Discover bootstrap fixes; do not reintroduce `Pending → Ready(empty)` behavior.
- [ ] **Step 4: Run all Discover canonical bootstrap/projection/refresh/ViewModel tests**.
- [ ] **Step 5: Commit** `perf(discover): share bootstrap and manual refresh flight`.

---

## Wave 3 acceptance gate

- [ ] Fusion success path uses one coherent input and thin currentness validation.
- [ ] canonical reactive tests expose no mixed-generation snapshot.
- [ ] reconciliation reevaluation no longer performs N+1/O(S²) source reloads.
- [ ] foreground and worker cannot execute the same claimed work concurrently.
- [ ] interactive work does not materialize historical `Q` backlog.
- [ ] one logical batch coalesces final fusion per Story/work revision.
- [ ] Search canonical settlement is bounded concurrent, not serial.
- [ ] Discover refresh is single-flight.
- [ ] Wave-0 `Q/R/S` scaling and Search benchmarks improve without correctness regression.
