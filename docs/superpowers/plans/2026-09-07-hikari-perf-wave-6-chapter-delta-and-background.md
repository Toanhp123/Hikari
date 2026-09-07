# Hikari Performance Wave 6 — Chapter Delta and Background Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make multi-page Chapter synchronization and periodic background dispatch scale with page/batch deltas rather than repeatedly recomputing cumulative/full Story/Library state.

**Architecture:** Introduce a sync-local incremental aggregation session with the current full aggregation engine as differential oracle, persist only Chapter mutation deltas and derive notification evidence from those deltas, then replace repeated global Library candidate scans with a materialized `chapter_sync_schedule` and indexed keyset pagination.

**Tech Stack:** Kotlin pure Chapter engine, Room v13 transactions, WorkManager, property/differential tests.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Current `ChapterAggregationEngine.plan()` defines final semantic correctness until differential equivalence is proven.
- Authoritative FULL sync deletion/tombstone behavior must remain exact.
- Notification event identity/order must remain contract-compatible.
- Materialized scheduling state must be repairable/backfillable and may not become an unverified second source of truth.

---

### Task 1: Define Chapter mutation delta model and differential oracle harness

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterMutationDelta.kt`
- Create: `chapters/src/test/kotlin/app/openstory/chapters/aggregation/ChapterAggregationDifferentialTest.kt`
- Reuse: `chapters/src/main/kotlin/app/openstory/chapters/aggregation/ChapterAggregationEngine.kt`

**Interfaces:**

```kotlin
data class ChapterMutationDelta(
    val storyId: StoryId,
    val creates: List<CanonicalChapter>,
    val releaseUpserts: List<ChapterRelease>,
    val linkChanges: List<ChapterReleaseLink>,
    val unlinks: Set<ChapterReleaseId>,
    val restores: Set<CanonicalChapterId>,
    val tombstones: Set<CanonicalChapterId>,
    val syncState: ChapterSyncState?,
    val commitFingerprint: String?,
)
```

- [ ] **Step 1: Add test helpers** that apply a `ChapterMutationDelta` to an in-memory graph and compare the resulting graph with full `AggregationPlan` application.
- [ ] **Step 2: Add generated/deterministic fixtures** covering page splits, duplicate releases, language variants, existing overrides, restores, tombstones, and FULL authoritative removals.
- [ ] **Step 3: Run differential test and verify RED** until the delta model/apply helper exists.
- [ ] **Step 4: Implement the data model only**; no production synchronizer switch yet.
- [ ] **Step 5: Commit** `perf(chapters): define chapter mutation delta contract`.

---

### Task 2: Implement incremental ChapterAggregationSession

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/aggregation/ChapterAggregationSession.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizer.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizerTest.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/aggregation/ChapterAggregationDifferentialTest.kt`

**Interfaces:**

```kotlin
interface ChapterAggregationSession {
    fun applyPage(releases: List<ChapterRelease>): ChapterMutationDelta
    fun finishAuthoritativeSource(fullReleaseIds: Set<ChapterReleaseId>): ChapterMutationDelta
    fun snapshot(): ChapterGraphSnapshot
}
```

- [ ] **Step 1: Add synchronizer call-count test** asserting `ChapterRepository.snapshot(storyId)` happens once per sync and full `aggregation.plan(...)` is not called once per page after migration.
- [ ] **Step 2: Build session indexes once** from initial `ChapterGraphSnapshot`: releases by ID/source, canonical Chapters by ID, current release→chapter links, overrides.
- [ ] **Step 3: Implement page-local normalization/aggregation delta** by reusing/extracting pure matching rules from the existing engine rather than duplicating them ad hoc.
- [ ] **Step 4: Update session indexes after each accepted delta** so later pages see earlier current-sync work without recomputing the whole prefix.
- [ ] **Step 5: Implement authoritative finish**: for FULL sync started from beginning, compare source-owned prior release IDs with accumulated `fullReleaseIds` and emit removals/tombstone effects once at completion.
- [ ] **Step 6: Switch `ChapterPageSynchronizer`** to initialize one session, apply each page delta, and persist the delta through the repository API introduced in Task 3.
- [ ] **Step 7: Run differential tests across all page split/order fixtures**; final graph must equal current full-engine oracle.
- [ ] **Step 8: Commit** `perf(chapters): aggregate multi-page sync incrementally`.

---

### Task 3: Add delta commit API and batch Room writes

**Files:**
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterRepositoryTest.kt`

**Interfaces:**
- `suspend fun commit(delta: ChapterMutationDelta): ChapterCommitResult` becomes the production page-sync write path.
- Legacy `commit(ChapterMutation)` may remain temporarily for benchmark fixture/tests until all callers migrate, then remove.

- [ ] **Step 1: Add Room regression** with large existing graph + one small page delta; assert commit does not call full `snapshotResolved(before)`/`snapshotResolved(after)` and writes only changed releases/links/chapters.
- [ ] **Step 2: Add bulk DAO operations** for creates/release upserts/link updates/unlinks/restores/tombstones/sync state. Prefer entity batch upsert/update where mapping can be expressed without per-link query loops.
- [ ] **Step 3: Apply all delta writes in one Room transaction** and preserve commit fingerprint/currentness semantics.
- [ ] **Step 4: Update `ChapterPageSynchronizer` and benchmark fixture caller** to use delta API or an explicit compatibility adapter.
- [ ] **Step 5: Run Room Chapter repository/migration/source-sync suites**.
- [ ] **Step 6: Commit** `perf(chapters): persist chapter sync deltas in batches`.

---

### Task 4: Derive notification events from semantic Chapter delta

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomNotificationEventRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/NotificationClaimRecoveryTest.kt`
- Test: `app/src/test/kotlin/app/openstory/notifications/NotificationDeliveryWorkerTest.kt`

**Interfaces:**
- Notification evidence derives from `creates`, `restores`, `linkChanges`, and removals/tombstones needed by existing event semantics; no full before/after graph read.

- [ ] **Step 1: Add dual-oracle test helper**: given current graph + delta, compute old snapshot-diff events and new delta-derived events; assert exact event key/type/order equivalence.
- [ ] **Step 2: Cover edge cases**: relink existing release, restore tombstoned chapter, new canonical chapter, no-op page, source removal, duplicate replay.
- [ ] **Step 3: Implement delta event derivation** inside commit transaction using already-known changed IDs plus only point rows required to populate event payload.
- [ ] **Step 4: Remove before/after full snapshot event detection** after equivalence suite passes.
- [ ] **Step 5: Run notification + Room Chapter suites**.
- [ ] **Step 6: Commit** `perf(chapters): derive notifications from chapter deltas`.

---

### Task 5: Add materialized Chapter sync schedule in Room v13

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterSyncScheduleEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterSyncScheduleDao.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncScheduleBackfillService.kt`
- Create: `app/src/main/kotlin/app/openstory/work/ChapterSyncScheduleBackfillWorker.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/RoomLibraryRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeApplier.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterSyncCandidateSourceTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/ChapterMigrationTest.kt`
- Create: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncScheduleBackfillServiceTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/ChapterSyncScheduleBackfillIntegrationTest.kt`

**Interfaces:**

```text
chapter_sync_schedule(
  story_id PRIMARY KEY,
  eligible,
  last_successful_sync_at_epoch_millis,
  schedule_revision
)
```

- [ ] **Step 1: Add migration test** proving v12→v13 creates the schedule table/indexes and generic backfill-state schema without deriving the user's Library schedule during database open.
- [ ] **Step 2: Add indexed ordering** for eligibility + null/oldest success + Story ID tie-break semantics matching current `ChapterSyncBatchPlanner` ordering.
- [ ] **Step 3: Add transactional maintenance hooks**: Library membership changes insert/delete eligibility rows, successful Chapter sync updates `last_successful_sync_at_epoch_millis`, and Story merge moves/coalesces schedule ownership in the merge transaction.
- [ ] **Step 4: Implement `ChapterSyncScheduleBackfillService`** using the generic Wave-2 `PerformanceBackfillStateDao`; each invocation reads a bounded Library/Chapter-state batch, upserts schedule rows idempotently, advances its cursor, and never scans the full Library in one call.
- [ ] **Step 5: Add `ChapterSyncScheduleBackfillWorker`** that drains bounded batches in background and can resume after process death; persist a dedicated `chapter-sync-schedule-v1` completion key in the generic Wave-2 backfill-state table.
- [ ] **Step 6: Add repair/reconciliation entry point** that can rebuild stale/missing schedule rows in bounded batches; the materialized schedule remains recoverable derived state, not semantic truth.
- [ ] **Step 7: Run migration, process-recreation, schedule invariant, merge, Library, and Chapter tests**.
- [ ] **Step 8: Commit** `perf(chapters): materialize periodic sync schedule`.

---

### Task 6: Replace full candidate scan/sort with keyset nextBatch

**Files:**
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncCandidateSource.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterSyncCandidateSource.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/PeriodicChapterDispatchWorker.kt`
- Delete: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncBatchPlanner.kt`
- Delete: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncBatchPlannerTest.kt`
- Create: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncCandidateSourceContractTest.kt`
- Test: `app/src/test/kotlin/app/openstory/work/PeriodicChapterDispatchTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/work/PeriodicChapterDispatchIntegrationTest.kt`

**Interfaces:**

```kotlin
sealed interface ChapterSyncCandidateRead {
    data class Ready(val batch: ChapterSyncBatch) : ChapterSyncCandidateRead
    data object BackfillPending : ChapterSyncCandidateRead
}

fun interface ChapterSyncCandidateSource {
    suspend fun nextBatch(cursor: ChapterSyncBatchCursor?, limit: Int): ChapterSyncCandidateRead
}
```

- [ ] **Step 1: Add source contract tests** for `BackfillPending`, first ready batch, continuation, null-first order, tie-break by Story ID, exactly batch-size rows, batch-size+1 continuation detection, and stale cursor.
- [ ] **Step 2: Implement Room source** so incomplete `chapter-sync-schedule-v1` backfill returns `BackfillPending`; once complete, use the indexed `chapter_sync_schedule` keyset query with `LIMIT 21` for current batch size 20 and encode continuation from the last selected row.
- [ ] **Step 3: Change `PeriodicChapterDispatchWorker.dispatch()`** to request one `nextBatch(cursor, 20)` directly; on `BackfillPending`, return retry/defer without interpreting the schedule as empty, and on `Ready` enqueue exactly the returned batch/continuation. Remove `eligibleCandidates()` + in-memory full sort/filter from the worker path.
- [ ] **Step 4: Delete `ChapterSyncBatchPlanner` after the worker and tests consume `ChapterSyncCandidateSource.nextBatch`; verify production source search has zero planner callers.**
- [ ] **Step 5: Run periodic worker/integration/schedule query-plan tests**, including upgraded v12 state before and after the durable backfill completion bit.
- [ ] **Step 6: Commit** `perf(chapters): page periodic sync candidates by keyset`.

---

### Task 7: Close Chapter scaling and background throughput gate

**Files:**
- Create: `docs/superpowers/checkpoints/2026-09-07-hikari-perf-wave-6-chapters.md`

- [ ] **Step 1: Run** `./gradlew :chapters:test :app:testDebugUnitTest :storage:room:connectedDebugAndroidTest --no-daemon` with focused class filters if required.
- [ ] **Step 2: Run architecture/schema verification scripts**.
- [ ] **Step 3: Compare multi-page sync** across `Pg/J/Z` fixture sizes and verify per-page query/work slope is tied to delta rather than cumulative prefix.
- [ ] **Step 4: Compare periodic continuation** at `Lb > 20, >200, >2000`; each worker invocation must fetch one bounded page rather than full Library candidates.
- [ ] **Step 5: Record differential-equivalence coverage and notification-event parity** in checkpoint.
- [ ] **Step 6: Commit** `test(perf): close chapter delta performance wave`.

---

## Wave 6 acceptance gate

- [ ] one Chapter sync snapshots the existing graph once, not once per page.
- [ ] page aggregation uses delta/session state, with full-engine differential equivalence.
- [ ] Room commit does not full-snapshot graph before/after every page.
- [ ] notification events are delta-derived with parity tests.
- [ ] periodic dispatch is true indexed keyset `LIMIT` over materialized schedule.
- [ ] Library size no longer multiplies continuation batch work through repeated global scans/sorts.
