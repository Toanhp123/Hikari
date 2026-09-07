# Hikari Performance Wave 1 — Bounded Storage Foundations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Story identity, canonical projection, progress protection and Home observation operate through point/bounded/coherent storage APIs instead of global materialization and unrelated table invalidation.

**Architecture:** Add explicit point/bulk APIs at repository boundaries and Room overrides using indexed queries plus invalidation-triggered transactional reads. Establish the Room-v13 migration ledger, but defer expensive derived-data backfills to the waves that own them.

**Tech Stack:** Kotlin, Coroutines/Flow, Room, androidTest migrations/query plans, Hilt.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Preserve Story redirect cycle detection and canonical resolution semantics.
- Bounded Story-ID APIs must define chunking; do not depend on SQLite's maximum bind count implicitly.
- Projection repositories may duplicate read-model SQL, not canonical fusion business logic.
- Room reactive APIs use invalidation as a trigger and publish coherent transactional snapshots.
- `MIGRATION_12_13` must not perform expensive catalog normalization/hashing.

---

### Task 1: Establish Room v13 migration ownership and schema test

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/Migration12To13Test.kt`

**Interfaces:**
- Produces DB version 13 and a single `RoomMigrations.MIGRATION_12_13` entry that later unshipped waves may extend before release.

- [ ] **Step 1: Write migration test** that opens a version-12 fixture, migrates to 13, and validates Room schema without expecting any later-wave derived backfill to have completed.
- [ ] **Step 2: Run** `./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.Migration12To13Test --no-daemon` and verify RED.
- [ ] **Step 3: Bump `OpenStoryDatabase` to 13**, add `MIGRATION_12_13`, and register it in the DB builder.
- [ ] **Step 4: Add only Wave-1 cheap structural SQL** in this task; later waves extend the migration while the schema is unreleased.
- [ ] **Step 5: Run migration test and** `bash scripts/verify-room-schema-stability.sh`.
- [ ] **Step 6: Self-review** that no normalization/hash/backfill loop executes during migration/open.
- [ ] **Step 7: Commit** `perf(storage): establish room v13 performance schema`.

---

### Task 2: Replace full redirect-table bounded decisions with indexed chain/EXISTS lookup

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryIdentityRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolver.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReversalPlanner.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolverTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReversalCoordinatorTest.kt`

**Interfaces:**
- Produces `suspend fun resolve(storyId)` using point-chain DAO access.
- Adds `CanonicalCatalogDao.hasIncomingRedirectOtherThan(canonicalStoryId, excludedRetiredStoryId): Boolean` (or equivalent indexed `EXISTS` contract) for merge-reversal lineage checks.
- Leaves existing `resolveAll()` temporarily callable but replaces its Room implementation in Task 3.

- [ ] **Step 1: Add a test with many unrelated redirects plus a short target chain** and a DAO/query spy or deterministic helper assertion proving target resolution does not call the full `redirects()` path.
- [ ] **Step 2: Add cycle and long-chain regression cases** so optimization cannot weaken invariant checks.
- [ ] **Step 3: Add merge-reversal regression** with many unrelated redirects, one allowed retired→survivor redirect, and zero/one additional incoming redirect; assert the planner result matches current semantics without calling the full `redirects()` materializer.
- [ ] **Step 4: Run resolver + merge-reversal tests and verify RED** for bounded-work assertions.
- [ ] **Step 5: Reuse/add `CanonicalCatalogDao.redirect(storyId)` point query** and resolve the chain inside one `database.withTransaction` boundary; inject `OpenStoryDatabase` if the internal constructor needs it for transaction ownership.
- [ ] **Step 6: Add indexed SQL `EXISTS`/`LIMIT 1` query** for `canonical_story_id = :survivor AND retired_story_id != :excludedRetired`; switch `RoomStoryMergeReversalPlanner.prepareLineage()` to it.
- [ ] **Step 7: Keep a per-resolution visited set** and throw `StoryIdentityInvariantException` on cycle exactly as before.
- [ ] **Step 8: Run focused androidTests and Catalog identity unit tests**, including query-plan/index verification for the incoming-redirect predicate.
- [ ] **Step 9: Commit** `perf(storage): bound story redirect decisions`.

---

### Task 3: Add bulk/frontier Story identity resolution and one bounded observer

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryIdentityRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolver.kt`
- Delete: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/ResolvedStoryIds.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolverTest.kt`
- Create: `storage/room/src/test/kotlin/app/openstory/storage/room/catalog/StoryIdentityQueryChunkingTest.kt`
- Reference only: `storage/room/src/test/kotlin/app/openstory/storage/room/readerassets/ReaderAssetQueryChunkingTest.kt`

**Interfaces:**
- Add `fun observeResolvedSet(storyIds: Set<StoryId>): Flow<Set<StoryId>>` to `StoryIdentityRepository`.
- Room override implements `resolveAll()` and `observeResolvedSet()` with chunked frontier resolution.

- [ ] **Step 1: Add tests** for empty set, duplicates, mixed redirected/nonredirected IDs, shared canonical target, chain depth >1, cycle, and Story sets larger than the chosen chunk size.
- [ ] **Step 2: Add DAO query** `redirectsFor(retiredStoryIds: List<String>)` and a named chunk constant local to Room storage.
- [ ] **Step 3: Implement frontier rounds** so each round bulk-queries only current unresolved IDs and advances chains until no redirect remains.
- [ ] **Step 4: Implement `observeResolvedSet`** as one redirect-table invalidation trigger followed by the coherent bulk resolution, `distinctUntilChanged()` after the bounded read.
- [ ] **Step 5: Replace internal extension users** of `ResolvedStoryIds.kt` with the repository API; remove the extension once no caller remains.
- [ ] **Step 6: Run storage Room tests plus affected Library/Reader/Chapter repository tests**.
- [ ] **Step 7: Run package-boundary verification** to ensure the Room implementation detail has not leaked upward.
- [ ] **Step 8: Commit** `perf(storage): bulk resolve story identities`.

---

### Task 4: Implement true point/bounded Catalog Story projection queries

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/projection/CatalogStoryProjectionRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogStoryProjectionRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogStoryProjectionRepositoryTest.kt`

**Interfaces:**
- `find(storyId)` becomes a true Room point read.
- `observeForStories(storyIds)` uses bounded chunked projection rows and one coherent transaction after invalidation.
- `observe()` remains explicit global API for consumers that truly need it.

- [ ] **Step 1: Write a test** that seeds many unrelated canonical Stories and asserts `find(target)` returns exactly the target without invoking/global-reading source evidence.
- [ ] **Step 2: Write bounded observer tests** for multiple IDs, redirects, generation changes, deletion/readiness transitions, and chunk-size boundary.
- [ ] **Step 3: Define a Room row/read-model** containing only fields required by `CatalogStoryProjection`; do not include source records/identifiers/fingerprints.
- [ ] **Step 4: Add point and bounded DAO queries** and mapping functions in `RoomCatalogStoryProjectionRepository`.
- [ ] **Step 5: Use invalidation tracker over the exact projection tables** as trigger, then execute one transactional bounded read.
- [ ] **Step 6: Run projection/Discover/Mapping tests** to prove API behavior remains compatible.
- [ ] **Step 7: Commit** `perf(catalog): add bounded canonical story projections`.

---

### Task 5: Add query-specific incomplete-release progress observation

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepository.kt`
- Modify: `app/src/main/kotlin/app/openstory/cache/AutomaticCachePolicyCoordinator.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressEntity.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepositoryTest.kt`
- Test: `app/src/test/kotlin/app/openstory/cache/AutomaticCachePolicyCoordinatorTest.kt`

**Interfaces:**
- Add `fun observeIncompleteReleaseIds(): Flow<Set<ChapterReleaseId>>`.

- [ ] **Step 1: Add repository test** with large completed history plus a small incomplete set; assert only distinct non-null incomplete release IDs are emitted.
- [ ] **Step 2: Add coordinator test** proving cache-protection updates from the new projection and no longer require full `ReadingProgress` objects.
- [ ] **Step 3: Add the predicate/projection-aligned Room query and index** `(completed_at_epoch_millis, chapter_release_id)` through v13 schema.
- [ ] **Step 4: Switch `AutomaticCachePolicyCoordinator`** to the new Flow and retain semantic `distinctUntilChanged` only after the cheap projection.
- [ ] **Step 5: Run `EXPLAIN QUERY PLAN` test** to verify the global temp sort from the old observer is absent for this path.
- [ ] **Step 6: Run Reader progress/app cache-policy tests and migration tests**.
- [ ] **Step 7: Commit** `perf(reader): observe incomplete release ids directly`.

---

### Task 6: Isolate Home observation from unrelated catalog-entry invalidation

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntities.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogHomeDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Produces a Home-specific materialized read projection/table updated with Home commits.
- `observeHomes()` no longer observes generic `catalog_entries`.

- [ ] **Step 1: Add regression test**: seed Home, subscribe, commit an unrelated Search-only catalog entry, assert Home does not execute/publish a new coherent read; then update a Home-referenced entry and assert Home does update.
- [ ] **Step 2: Add a Home projection entity/table** containing exactly the summary fields needed by Home membership rows.
- [ ] **Step 3: Update Home refresh commit transaction** to upsert projection rows for current Home references and delete stale Home projection rows according to existing Home replacement semantics.
- [ ] **Step 4: Change `readCoherentHomes()`/`observeHomes()`** to consume/observe only Home-domain tables.
- [ ] **Step 5: Add v12→v13 migration population** using bounded direct SQL from current Home membership + catalog entries; this copy is structural and does not normalize/hash wide corpus state.
- [ ] **Step 6: Run all Room Catalog/Home/Discover tests plus migration tests**.
- [ ] **Step 7: Commit** `perf(catalog): isolate home reactive projection`.

---

## Wave 1 acceptance gate

- [ ] `RoomStoryIdentityResolver.resolve()` contains no full `dao.redirects()` materialization on the point path.
- [ ] `RoomStoryMergeReversalPlanner` answers nested incoming-redirect lineage through indexed bounded SQL, not `redirects().any { ... }`.
- [ ] production search confirms no other bounded redirect decision materializes the full redirect table; any legitimate explicit global maintenance scan is documented separately.
- [ ] Story-set observation has one bounded bulk contract and chunk tests.
- [ ] `CatalogStoryProjectionRepository.find()` is point/bounded in Room.
- [ ] cache policy observes incomplete release IDs directly.
- [ ] unrelated Search entry writes do not wake Home semantic observation.
- [ ] v12→v13 migration remains bounded and schema-stable.
- [ ] Run: `./gradlew :catalog:test :reader:testDebugUnitTest :app:testDebugUnitTest :storage:room:connectedDebugAndroidTest --no-daemon` scoped as necessary for device availability.
- [ ] Run architecture/schema scripts and record Wave-1 checkpoint.
