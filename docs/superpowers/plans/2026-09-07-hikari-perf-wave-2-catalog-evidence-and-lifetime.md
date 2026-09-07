# Hikari Performance Wave 2 — Catalog Evidence and Lifetime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove historical-wide catalog reconstruction from Search/Refresh/Details while preserving reconciliation semantics and durable identity continuity.

**Architecture:** Persist compact reconciliation evidence plus searchable postings, resolve candidates through bounded repository queries, replace full-index `fork()` with request/provider overlays, allocate Story IDs with point reservations, batch Room commits, and separate durable identity evidence lifetime from prunable wide presentation metadata.

**Tech Stack:** `:catalog:engine` pure reconciliation models, `:catalog` runtime orchestration, Room v13, Coroutines, migration/backfill WorkManager lane.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Existing reconciliation evidence/ranking semantics are the oracle; no candidate cap/selectivity behavior change in this wave.
- Durable compact evidence must be updated atomically with the source mutation that makes it current.
- Historical derived-data backfill is resumable and never completed synchronously on Search/Refresh.
- Wide Search-only metadata may be compacted only under explicit reachability; identity evidence remains durable.

---

### Task 1: Define the compact reconciliation evidence repository contract

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEvidenceRepository.kt`
- Reuse: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/reconciliation/ReconciliationModels.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEvidenceRepositoryContractTest.kt`

**Interfaces:**

```kotlin
interface CatalogReconciliationEvidenceRepository {
    suspend fun candidatesFor(incoming: ReconciliationEvidence): List<ReconciliationEvidence>
    suspend fun evidenceForSources(keys: Collection<CatalogSourceKey>): List<ReconciliationEvidence>
    suspend fun evidenceForStories(storyIds: Set<StoryId>): Map<StoryId, List<ReconciliationEvidence>>
    suspend fun upsert(evidence: Collection<ReconciliationEvidence>)
}
```

- [ ] **Step 1: Write repository contract tests** for identifier/title/author/lineage candidate parity with `InMemoryCatalogCandidateIndex` on the same evidence corpus.
- [ ] **Step 2: Run** `./gradlew :catalog:test --tests '*CatalogReconciliationEvidenceRepositoryContractTest*' --no-daemon` and verify RED.
- [ ] **Step 3: Add the interface only in `:catalog`**, depending on pure `ReconciliationEvidence`; do not expose Room entities upward.
- [ ] **Step 4: Add deterministic ordering requirements** matching current candidate strength/story ordering so callers do not gain hidden nondeterminism.
- [ ] **Step 5: Commit** `perf(catalog): define compact reconciliation evidence boundary`.

---

### Task 2: Persist compact evidence and postings in Room v13

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogReconciliationEvidenceEntities.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogReconciliationEvidenceDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogReconciliationEvidenceRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeApplier.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReversalWriter.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogReconciliationEvidenceRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReversalCoordinatorTest.kt`

**Interfaces:**
- Implements Task-1 repository.
- Uses `catalog_reconciliation_evidence` + `catalog_reconciliation_terms` with indexed term lookup.
- Provides transactional ownership-maintenance operations for forward merge (`retiredStoryId -> survivorStoryId`) and controlled reversal (exact audited source keys `survivor -> retired` with expected-owner checks).

- [ ] **Step 1: Write Room tests** for candidate parity, source replacement, Story reassignment, deletion/compaction behavior, and large unrelated corpus not returned by selective terms. Add forward-merge and controlled-reversal integration cases that query candidates/evidence after ownership moves and fail if evidence/posting `story_id` disagrees with `catalog_entries.story_id`. Include partially backfilled state: already-indexed rows move atomically, missing evidence does not block merge/reversal, and later bounded backfill derives ownership from the post-operation `catalog_entries` truth.
- [ ] **Step 2: Add v13 entities and indexes** for source key, Story ID, and `(term_kind, term_value)` lookup.
- [ ] **Step 3: Implement candidate lookup** by collecting matching terms, selecting candidate source/Story keys, then bulk-loading compact evidence; never join/deserialize full `catalog_entries` metadata for the query.
- [ ] **Step 4: Implement bulk upsert transaction** that replaces the postings for changed sources atomically with their evidence row.
- [ ] **Step 5: Implement ownership-maintenance DAO operations** so forward merge updates evidence + posting Story ownership for all retired sources, while controlled reversal updates exactly `retired.sourceKeys` back to the retired Story and checks `expectedStoryId = survivorId`.
- [ ] **Step 6: Invoke those operations from `RoomStoryMergeApplier` and `RoomStoryMergeReversalWriter` inside their existing outer Room transactions**, adjacent to `catalogDao.moveEntries/moveEntry`; never schedule asynchronous repair after commit.
- [ ] **Step 7: Wire Hilt repository binding** in `StorageModule`.
- [ ] **Step 8: Run Room repository, merge/reversal, migration tests and schema verification**.
- [ ] **Step 9: Inspect `EXPLAIN QUERY PLAN`** for identifier/title/author term lookup and add a plan regression test if practical.
- [ ] **Step 10: Commit** `perf(storage): persist compact catalog reconciliation evidence`.

---

### Task 3: Add resumable compact-evidence backfill without foreground blocking

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogEvidenceBackfillService.kt`
- Create: `app/src/main/kotlin/app/openstory/work/CatalogEvidenceBackfillWorker.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/maintenance/PerformanceBackfillStateEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/maintenance/PerformanceBackfillStateDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogEvidenceBackfillServiceTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CatalogEvidenceBackfillIntegrationTest.kt`

**Interfaces:**

```kotlin
interface CatalogEvidenceBackfillSource {
    suspend fun nextBatch(after: CatalogSourceKey?, limit: Int): CatalogEvidenceBackfillBatch
}
```

- [ ] **Step 1: Write tests** for resumable cursor, process interruption, idempotent replay, source mutation during backfill, and bounded batch size.
- [ ] **Step 2: Implement generic durable backfill state** keyed by `backfill_key`, recording schema/algorithm version, opaque cursor, completion flag, and update timestamp so later bounded derived projections (including Chapter schedule) can reuse the same mechanism without a second migration-state subsystem.
- [ ] **Step 3: Implement one bounded batch** that reads only the selected source rows, derives current `ReconciliationEvidence` with existing factory semantics, and upserts compact evidence/postings.
- [ ] **Step 4: Add WorkManager worker** that performs bounded batches and reschedules/continues without blocking app startup.
- [ ] **Step 5: Define compatibility rule**: callers may use legacy evidence fallback only for sources/DB state not yet indexed; no caller may trigger full backfill synchronously.
- [ ] **Step 6: Run process-recreation/idempotency tests and aged-DB migration fixture**.
- [ ] **Step 7: Commit** `perf(catalog): backfill compact evidence incrementally`.

---

### Task 4: Replace runtime global ingest index with request/provider overlays

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogIngestSession.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Keep pure oracle: `catalog/engine/src/main/kotlin/app/openstory/catalog/engine/reconciliation/CatalogIngestReconciliationIndex.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogRefreshServiceTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsLoaderTest.kt`

**Interfaces:**
- `CatalogIngestSession` queries durable compact evidence and keeps only current-request deltas.
- `beginProvider()` returns a child overlay with `commitIntoParent()` / `discard()` semantics.

- [ ] **Step 1: Add parity tests** reproducing current `fork()` semantics: earlier successful provider evidence visible later; failed provider-local mutations invisible after discard; within-provider local resolutions visible to later items.
- [ ] **Step 2: Add a scaling characterization test** whose fake evidence repository contains large historical `N` but asserts session creation performs no full-corpus read.
- [ ] **Step 3: Implement parent/child overlay maps** keyed by source/Story only for current-request changes.
- [ ] **Step 4: Reconcile an incoming item** by merging candidates from compact repository + parent overlay + child overlay, then feeding the existing pure reconciliation engine.
- [ ] **Step 5: Switch Search, Refresh and unowned Details** to the session and remove runtime `sourceRecords()`/global-index initialization from those paths.
- [ ] **Step 6: Retain `CatalogIngestReconciliationIndex` and its engine tests as a pure reference/oracle, but remove all production `catalog` module imports and runtime callers so foreground Search/Refresh/Details cannot rebuild it.**
- [ ] **Step 7: Run Catalog Search/Refresh/Details/reconciliation suites**.
- [ ] **Step 8: Commit** `perf(catalog): reconcile through request-local evidence overlays`.

---

### Task 5: Replace per-new-Story global ID set construction with point allocation

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogStoryIdAllocator.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogStoryIdAllocatorTest.kt`

**Interfaces:**

```kotlin
interface CatalogStoryIdAllocator {
    suspend fun allocate(incoming: ReconciliationEvidence): StoryId
}
```

- [ ] **Step 1: Add tests** for no collision, deterministic collision suffixes, multiple allocations in one request, concurrent DB collision, and no full existing-ID scan.
- [ ] **Step 2: Add point `storyExists(storyId)` repository/DAO API** and request-local reserved-ID set.
- [ ] **Step 3: Reuse the deterministic base/suffix semantics** from `CatalogStoryIdFactory`; do not change external IDs.
- [ ] **Step 4: Integrate allocation into ingest session** and ensure final Story insert/commit handles a last-moment uniqueness collision by retrying the deterministic suffix rather than scanning all IDs.
- [ ] **Step 5: Run existing `CatalogStoryIdFactoryTest` plus new allocator and service suites**.
- [ ] **Step 6: Commit** `perf(catalog): allocate story ids with point reservations`.

---

### Task 6: Batch Search/Refresh catalog persistence with compact evidence

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/repository/CatalogRepositoryContractTest.kt`

**Interfaces:**
- Commit operations accept resolved Story ownership and write Story/canonical state/source entries/identifiers/compact evidence in grouped lists inside one transaction.

- [ ] **Step 1: Add a Room test** with `M > 1` that instruments DAO statement groups or repository fake calls and proves commit is batched rather than invoking list-of-one upserts per entry.
- [ ] **Step 2: Add/extend batch DAO methods** for Stories, entries, identifiers and compact evidence maintenance.
- [ ] **Step 3: Refactor `commitSearchSummaries()`** to build all rows first, then execute batched statements in deterministic order inside one transaction.
- [ ] **Step 4: Apply the same batching primitive to Refresh where semantics match** without conflating Home replacement semantics with Search append semantics.
- [ ] **Step 5: Run Room Catalog contract/outbox/canonical-init tests**.
- [ ] **Step 6: Commit** `perf(storage): batch catalog ingest commits`.

---

### Task 7: Add safe wide-metadata compaction while retaining identity evidence

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/maintenance/CatalogPresentationCompactionPolicy.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/maintenance/CatalogPresentationCompactionService.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Create: `app/src/main/kotlin/app/openstory/work/CatalogPresentationCompactionWorker.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/maintenance/CatalogPresentationCompactionPolicyTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CatalogPresentationCompactionIntegrationTest.kt`

**Interfaces:**
- Policy classifies wide source presentation as protected vs prunable using explicit reachability/provenance.
- Compact evidence/Story identity rows are never deleted merely because presentation is pruned.

- [ ] **Step 1: Write policy tests** covering Home, Library, active mapping, Reader/open-story, download/progress protection, search-only unreachable, and merged/redirected Stories.
- [ ] **Step 2: Add a bounded candidate DAO** for old/prunable presentation rows; do not scan/delete all Search rows synchronously.
- [ ] **Step 3: Implement compaction** that removes only approved wide presentation payload and leaves compact reconciliation evidence/postings + canonical identity continuity intact.
- [ ] **Step 4: Add integration test**: compact a search-only source, later ingest matching evidence, and prove reconciliation still resolves to the same Story.
- [ ] **Step 5: Add bounded background worker**; no Search/Refresh call invokes it synchronously.
- [ ] **Step 6: Run Catalog/Library/Mapping/Reader continuity tests**.
- [ ] **Step 7: Commit** `perf(catalog): compact unreachable presentation metadata safely`.

---

## Wave 2 acceptance gate

- [ ] Search/Refresh/unowned Details runtime path contains no global `sourceRecords()` index rebuild.
- [ ] provider session creation/fork is independent of historical `N`.
- [ ] Story allocation does not rebuild all existing IDs.
- [ ] candidate evidence reads compact rows only; wide `B` does not scale selective candidate lookup.
- [ ] forward merge and controlled reversal leave `catalog_entries`, compact evidence and postings with identical Story ownership in the same transaction; post-operation candidate lookup resolves to the correct Story.
- [ ] Search commit is batched.
- [ ] aged Search-only presentation can be compacted while identity/reconciliation continuity remains.
- [ ] Wave-0 Search scaling benchmark shows materially flatter slope as `N/B` grow.
