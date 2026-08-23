# Canonical Engine Performance and Durability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound Discover and Search foreground convergence, remove catalog read amplification, and make canonical background work transactionally durable and safe for concurrent consumers.

**Architecture:** Discover and Search retain feature-specific foreground selection but share one batch orchestration contract. Room supplies bulk catalog snapshots, a transactional catalog-change outbox, and leased canonical work; the processor performs compare-and-set batch transitions while WorkManager remains a bounded wake-up mechanism.

**Tech Stack:** Kotlin, coroutines/Flow, Android Room 2.x, WorkManager, JUnit/kotlin-test, kotlinx-coroutines-test, AndroidX MigrationTestHelper.

**Spec:** `docs/superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md`

## Global Constraints

- Preserve reconciliation policy version, fusion policy version, primary-selection policy version, ranking, and canonical field semantics.
- Search foreground convergence is limited to 20 distinct durable Story IDs.
- Database schema moves from version 9 to version 10 through a non-destructive `MIGRATION_9_10`.
- CancellationException is always rethrown.
- New queue/outbox operations use compare-and-set or idempotent coalescing; stale work may not delete newer facts.
- Unit tests must pass without an Android device; Room migration and concurrency tests run as connected instrumentation tests.
- The deliverable is a portable patch; do not commit or rewrite unrelated user changes.

---

### Task 1: Scope Discover canonical observations

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticContent.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogStoryProjectionRepository.observeForStories(Set<StoryId>)` and `discoverCanonicalBootstrapStoryIds(...)`.
- Produces: a Discover content Flow that observes only its current semantic Story IDs and publishes structurally distinct content.

- [ ] **Step 1: Write the failing scoped-observation test**

Add a recording projection repository to `DiscoverViewModelTest` and assert that homes containing semantic IDs `{story:a, story:b}` cause exactly that set to be passed to `observeForStories`; make `observe()` fail the test if called.

```kotlin
assertEquals(setOf(StoryId("story:a"), StoryId("story:b")), projections.observedStoryIds.value)
assertEquals(0, projections.observeAllCalls)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModelTest*scoped*' --no-daemon
```

Expected: failure because `DiscoverViewModel` still calls `projections.observe()`.

- [ ] **Step 3: Implement the dynamic scoped Flow**

Build `visibleStoryIds` from `homes + selectedContentType`, apply `distinctUntilChanged`, then `flatMapLatest` to `observeForStories`. Combine that restricted projection Flow with homes and content type. Apply `distinctUntilChanged()` to `DiscoverSemanticContent` before UI-state composition.

```kotlin
private val visibleStoryIds = combine(homes, selectedContentType) { currentHomes, contentType ->
    discoverCanonicalBootstrapStoryIds(currentHomes, contentType).toSet()
}.distinctUntilChanged()
```

- [ ] **Step 4: Run all Discover unit tests and verify GREEN**

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*Discover*' --no-daemon
```

- [ ] **Step 5: Record the checkpoint**

```bash
git add feature/catalog/src/main feature/catalog/src/test
git commit -m "perf(discover): observe only visible canonical stories"
```

### Task 2: Replace catalog-record N+1 reads with bulk snapshots

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalProjectionQueryTest.kt`

**Interfaces:**
- Produces: `CatalogDao.allIdentifiers(): List<CatalogEntryIdentifierEntity>` and fixed-query-count `RoomCatalogRepository.sourceRecords()`.
- Preserves: exact `CatalogSourceRecord` values and fingerprints.

- [ ] **Step 1: Write a failing 1,000-record equivalence test**

Seed 1,000 entries with identifiers, call `sourceRecords()`, and assert every source key, Story ID, identifier set, identity fingerprint, and fusion fingerprint matches the seeded metadata.

```kotlin
assertEquals(1_000, records.size)
assertEquals(expectedIdentifiers, records.associate { it.key to it.summary.identifiers })
```

- [ ] **Step 2: Run the instrumentation test and verify RED for the missing bulk DAO API**

```bash
./gradlew :storage:room:compileDebugAndroidTestKotlin --no-daemon
```

Expected: compilation failure for `allIdentifiers()` referenced by the test fixture/query spy.

- [ ] **Step 3: Add and use the bulk identifier query**

Add:

```kotlin
@Query("SELECT * FROM catalog_entry_identifiers ORDER BY plugin_id, source_id, namespace, scope, value")
suspend fun allIdentifiers(): List<CatalogEntryIdentifierEntity>
```

In `sourceRecords()`, load `allEntries()` and `allIdentifiers()` once inside the existing transaction, group by `(pluginId, sourceId)`, and construct records without calling `identifierModels` in the loop. Use `identifiersForStories` for story-scoped reads.

- [ ] **Step 4: Run Room unit compilation and connected query tests**

```bash
./gradlew :storage:room:testDebugUnitTest :storage:room:compileDebugAndroidTestKotlin --no-daemon
./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalProjectionQueryTest --no-daemon
```

- [ ] **Step 5: Record the checkpoint**

```bash
git add storage/room/src/main storage/room/src/androidTest
git commit -m "perf(room): bulk load catalog identifiers"
```

### Task 3: Batch identity and source-availability reads

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryIdentityRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolver.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogSourceAvailabilityResolver.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionService.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/fusion/CanonicalFusionServiceTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun resolveAll(storyIds: Collection<StoryId>): Map<StoryId, StoryId>` with a default implementation and a one-redirect-snapshot Room implementation.
- Produces: `CatalogSourceAvailabilityResolver.resolve(records: List<CatalogSourceRecord>)` that calls `registry.enabled()` once.

- [ ] **Step 1: Write failing bulk-resolution and availability tests**

Assert that duplicate and redirected Story IDs resolve deterministically from one snapshot. In the fusion test, provide three source records and assert the registry's `enabled()` counter is one after rebuild.

```kotlin
assertEquals(1, registry.enabledCalls)
assertEquals(mapOf(retired to survivor, survivor to survivor), identity.resolveAll(listOf(retired, survivor)))
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CanonicalFusionServiceTest*availability*' --no-daemon
```

Expected: more than one `enabled()` call because fusion maps `availability.resolve(record)`.

- [ ] **Step 3: Implement bulk resolution and one-snapshot availability**

Change `CanonicalFusionService` from `sources.map { availability.resolve(it) }` to `availability.resolve(sources)`. Implement list resolution by loading enabled sources once and looking them up by plugin ID. Implement Room `resolveAll` by loading `dao.redirects()` once and resolving every requested ID against that map.

- [ ] **Step 4: Run catalog and Room tests**

```bash
./gradlew :catalog:testDebugUnitTest :storage:room:testDebugUnitTest :storage:room:compileDebugAndroidTestKotlin --no-daemon
```

- [ ] **Step 5: Record the checkpoint**

```bash
git add catalog/src storage/room/src
git commit -m "perf(canonical): snapshot identity and availability reads"
```

### Task 4: Give Search a bounded foreground policy

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchModels.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `CanonicalEngineEventSink.onEvidenceChanges(changes, immediateStoryIds)`.
- Produces: deterministic `SEARCH_FOREGROUND_STORY_LIMIT = 20` selection and provisional `CatalogSearchStory` values for deferred results.

- [ ] **Step 1: Write the failing 25-Story Search test**

Return 25 distinct results, record orchestration calls, and assert one batch call, 20 immediate IDs in result order, zero per-item calls, and 25 published Story cards.

```kotlin
assertEquals(1, sink.batchCalls.size)
assertEquals(expectedStoryIds.take(20).toSet(), sink.batchCalls.single().immediateStoryIds)
assertEquals(25, result.stories.size)
```

- [ ] **Step 2: Run the focused Search test and verify RED**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CatalogSearchServiceTest*foreground*' --no-daemon
```

Expected: 25 per-item `onEvidenceChanged` calls and no batch call.

- [ ] **Step 3: Accumulate changes and route once**

Collect committed changes across all source commits. Build durable cards first, select the first 20 distinct Story IDs deterministically, and call `onEvidenceChanges` once. Bootstrap only immediate IDs. For deferred IDs without a ready canonical state, build a provisional projection from the stable best source card instead of dropping the result.

- [ ] **Step 4: Run Search tests and verify GREEN**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CatalogSearchServiceTest*' :feature:catalog:testDebugUnitTest --tests '*SearchViewModelTest*' --no-daemon
```

- [ ] **Step 5: Record the checkpoint**

```bash
git add catalog/src feature/catalog/src/test
git commit -m "perf(search): bound foreground canonical convergence"
```

### Task 5: Define leased queue and batch-transition contracts

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWorkProcessor.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceServiceTest.kt`

**Interfaces:**
- Produces: `CanonicalEngineWorkLease(token: String, expiresAtEpochMillis: Long)` attached to claimed items.
- Produces: `CanonicalEngineWorkTransition` sealed values `Complete`, `Retry`, and `BlockInvariant`.
- Produces: `suspend fun transitionClaimed(transitions: List<CanonicalEngineWorkTransition>): CanonicalEngineTransitionReport`.

- [ ] **Step 1: Write failing processor contract tests**

Test that the processor sends one transition batch, counts only `applied` transitions, and preserves reconciliation-before-fusion ordering returned by the repository.

```kotlin
assertEquals(1, work.transitionBatches.size)
assertEquals(1, report.succeeded)
assertEquals(listOf(RECONCILIATION_REEVALUATION, FUSION_REBUILD), processedTypes)
```

- [ ] **Step 2: Run focused orchestration tests and verify RED**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CanonicalEngineMaintenanceServiceTest*batch*' --no-daemon
```

Expected: missing leased-item and `transitionClaimed` APIs.

- [ ] **Step 3: Implement domain contracts and processor accumulation**

Give a claimed item a non-null lease token/expiry. Keep dirty/unclaimed snapshots lease-free. Process items sequentially, accumulate typed transitions, commit once, and derive report counters from the returned applied transition identities rather than optimistic loop counters.

- [ ] **Step 4: Run orchestration tests**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CanonicalEngine*' --no-daemon
```

- [ ] **Step 5: Record the checkpoint**

```bash
git add catalog/src/main catalog/src/test
git commit -m "refactor(canonical): define leased batch queue transitions"
```

### Task 6: Implement atomic Room claims and schema migration

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogEntities.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineWorkRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineStateTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CanonicalEngineMigrationTest.kt`

**Interfaces:**
- Consumes: leased queue contracts from Task 5.
- Produces: transactional `claimReady(now, limit)` and `transitionClaimed(...)` implementations plus `MIGRATION_9_10`.

- [ ] **Step 1: Write failing concurrent-claim, expiry, boundary-order, and migration tests**

Use two repository instances against one in-memory database. Assert disjoint live claims, recovery after lease expiry, reconciliation before fusion when `limit = 1`, and preservation of a version-9 work row after migration.

```kotlin
assertTrue(firstClaim.map { it.key }.intersect(secondClaim.map { it.key }.toSet()).isEmpty())
assertEquals(RECONCILIATION_REEVALUATION, repository.claimReady(now, 1).single().type)
```

- [ ] **Step 2: Compile instrumentation tests and verify RED**

```bash
./gradlew :storage:room:compileDebugAndroidTestKotlin --no-daemon
```

Expected: schema/API failures for lease columns and migration 9→10.

- [ ] **Step 3: Implement schema version 10 and migration**

Add nullable `lease_token` and `lease_expires_at_epoch_millis` columns, replace the single-column runnable index with the migration-defined composite indexes, register `MIGRATION_9_10`, and preserve existing rows with null leases.

- [ ] **Step 4: Implement transactional claim and batch transitions**

Inside `withTransaction`, select eligible rows using SQL `CASE work_type` priority, set one generated lease token/expiry on the selected primary keys, and re-query by token. Apply transitions only when story ID, type, queue revision fields, and lease token match. `markDirty` clears leases.

- [ ] **Step 5: Run Room unit and connected tests**

```bash
./gradlew :storage:room:testDebugUnitTest :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalEngineStateTest,app.openstory.storage.room.catalog.CanonicalEngineMigrationTest --no-daemon
```

- [ ] **Step 6: Record the checkpoint**

```bash
git add storage/room/src
git commit -m "feat(room): lease canonical work atomically"
```

### Task 7: Add the transactional catalog-change outbox

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CatalogChangeOutbox.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogChangeOutboxRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogEntities.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogChangeOutboxTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CanonicalEngineMigrationTest.kt`

**Interfaces:**
- Produces: `CatalogChangeOutboxEvent` and `CatalogChangeOutboxRepository.pending(limit)` / `materialize(events)`.
- `materialize(events)` coalesces canonical work and deletes exactly those outbox rows in one Room transaction.

- [ ] **Step 1: Write failing atomicity and crash-window tests**

Commit a home mutation without invoking orchestration and assert pending outbox rows remain. Materialize them and assert queue rows exist while outbox rows disappear. Inject a failure between queue insertion and acknowledgment and assert the transaction rolls both operations back.

```kotlin
assertEquals(changes.size, outbox.pending(100).size)
assertTrue(outbox.pending(100).isEmpty())
assertTrue(work.claimReady(now, 100).isNotEmpty())
```

- [ ] **Step 2: Compile instrumentation tests and verify RED**

```bash
./gradlew :storage:room:compileDebugAndroidTestKotlin --no-daemon
```

Expected: missing outbox entity/repository and schema mismatch.

- [ ] **Step 3: Add entity, DAO, and migration SQL**

Create `catalog_change_outbox` with an auto-generated numeric event ID, durable Story/source fields, boolean change flags, evidence level, reason, and creation timestamp. Add an index on `(event_id)` for ordered pending reads and update fresh schema validation.

- [ ] **Step 4: Write outbox rows inside catalog commit transactions**

Convert each effective `CatalogCommitChange` into an entity before `commitHomeRefresh`, `commitSearchSummaries`, or `commitDetails` returns from its existing `withTransaction`. Do not create rows for unchanged fingerprints/availability.

- [ ] **Step 5: Implement atomic materialization**

Translate events through the existing `toDeferredCanonicalWorkRequests` semantics, batch-coalesce queue rows, and delete represented outbox IDs in the same transaction. Ensure a thrown exception leaves both tables unchanged.

- [ ] **Step 6: Run outbox and migration tests**

```bash
./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogChangeOutboxTest,app.openstory.storage.room.catalog.CanonicalEngineMigrationTest --no-daemon
```

- [ ] **Step 7: Record the checkpoint**

```bash
git add catalog/src storage/room/src app/src/main/kotlin/app/openstory/di
git commit -m "feat(catalog): persist canonical changes through an outbox"
```

### Task 8: Wire outbox recovery and bounded worker drains

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceService.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/CanonicalEngineWorker.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceServiceTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/work/CanonicalEngineWorkerTest.kt`

**Interfaces:**
- Consumes: `CatalogChangeOutboxRepository.materializePending(limit)` and leased queue transitions.
- Produces: startup/safety recovery that materializes outbox before queue drain and a worker elapsed-time budget of 5 seconds.

- [ ] **Step 1: Write failing recovery-order and time-budget tests**

Assert maintenance calls outbox materialization before `claimReady`. Feed multiple non-empty drain pages and a fake monotonic clock crossing 5 seconds; assert the worker stops and schedules one continuation.

```kotlin
assertEquals(listOf("materialize", "claim"), calls.take(2))
assertEquals(1, scheduler.drainCalls)
```

- [ ] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CanonicalEngineMaintenanceServiceTest*outbox*' :app:testDebugUnitTest --tests '*CanonicalEngineWorkerTest*budget*' --no-daemon
```

Expected: maintenance never accesses outbox and worker drains only one page.

- [ ] **Step 3: Materialize outbox before foreground/deferred scheduling**

Have orchestrator treat already-persisted outbox rows as the source of durable deferred work. Preserve immediate foreground convergence, but complete a foreground durable row only through its compare-and-set queue transition. Maintenance startup and safety paths call `materializePending` before `drainReady`.

- [ ] **Step 4: Drain pages within the worker budget**

Loop maintenance drains while ready work remains and elapsed monotonic time is below 5 seconds. Stop immediately for retry timestamps in the future. Schedule exactly one drain continuation when ready work remains.

- [ ] **Step 5: Run orchestration and worker tests**

```bash
./gradlew :catalog:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

- [ ] **Step 6: Record the checkpoint**

```bash
git add catalog/src app/src
git commit -m "feat(canonical): recover outbox and budget worker drains"
```

### Task 9: Add scale contracts and perform full verification

**Files:**
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineStateTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogChangeOutboxTest.kt`
- Modify: `docs/superpowers/plans/2026-08-24-canonical-engine-performance-and-durability.md`

**Interfaces:**
- Verifies all interfaces produced by Tasks 1–8.

- [ ] **Step 1: Add 1,000-Story scale-contract assertions**

Assert 1,000 changes are persisted in one outbox commit, materialized into at most two work rows per Story, claimed without duplicate live leases, and drained in priority order. Record elapsed time only as diagnostic output; do not use a flaky wall-clock pass threshold.

- [ ] **Step 2: Run the JVM verification suite**

```bash
./gradlew :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest :storage:room:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 3: Compile all instrumentation tests**

```bash
./gradlew :storage:room:compileDebugAndroidTestKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the connected Room suite on an available device**

```bash
./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalProjectionQueryTest,app.openstory.storage.room.catalog.RoomCanonicalEngineStateTest,app.openstory.storage.room.catalog.RoomCatalogChangeOutboxTest,app.openstory.storage.room.catalog.CanonicalEngineMigrationTest --no-daemon
```

Expected: all selected instrumentation tests pass. If no device is attached, report this command as pending rather than claiming connected verification.

- [ ] **Step 5: Generate and inspect the portable patch**

```bash
git diff --check
git diff --binary -- docs/superpowers/specs docs/superpowers/plans catalog feature/catalog storage/room app > hikari-canonical-engine-performance-durability.patch
sha256sum hikari-canonical-engine-performance-durability.patch
```

Confirm the patch contains no build outputs, local database files, credentials, or unrelated changes.
