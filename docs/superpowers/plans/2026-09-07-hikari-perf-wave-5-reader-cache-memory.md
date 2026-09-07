# Hikari Performance Wave 5 — Reader Cache and Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove global cache accounting and broad lock contention from Reader hot paths, isolate explicit-download invalidation from automatic cache, and bound encoded-page allocation/memoization without weakening Reader integrity/security semantics.

**Architecture:** Split reactive metadata domains, maintain an in-process incremental automatic-cache ledger recovered from durable metadata, evict from bounded candidate pages with point currentness, separate fast state locking from publication ordering, expose immutable Reader payload streams instead of repeated full copies, and bound process memoization.

**Tech Stack:** Kotlin Coroutines/Mutex, Room v13, file blob stores/leases, Coil integration, Reader RICC contracts, Macrobenchmark.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- SHA-256/checksum verification remains mandatory.
- Security/account/plugin invalidation outranks stale in-flight publication exactly as RICC specifies.
- Durable metadata remains recovery truth; the in-memory byte ledger is an optimization rebuilt/repaired from durable state.
- Physical deletion targets generation-addressed blobs and respects active read leases.
- No writable backing `ByteArray` is exposed to consumers.

---

### Task 1: Split explicit-download and automatic-cache Room metadata domains

**Files:**
- Replace/retire: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/ChapterStorageEntryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/ExplicitDownloadEntryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/AutomaticChapterCacheEntryEntity.kt`
- Split/modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/DownloadMigrationTest.kt`

**Interfaces:**
- `DownloadRepository` reads/writes explicit table only.
- `CacheRepository` reads/writes automatic table only.
- `StorageReconciliationRepository` explicitly combines both when reconciliation semantics require both namespaces.

- [ ] **Step 1: Add reactive regression test**: subscribe to explicit downloads/completed count, mutate automatic cache, assert no explicit observer emission/query; then mutate explicit download and assert normal emission.
- [ ] **Step 2: Add v12→v13 migration test** that copies `EXPLICIT_DOWNLOAD` rows to the explicit table and `AUTOMATIC_CACHE` rows to automatic table with every metadata field preserved.
- [ ] **Step 3: Define two entities/DAOs** with indexes matched to each domain instead of a shared namespace primary key.
- [ ] **Step 4: Refactor `RoomDownloadRepository`** so each interface delegates to the correct DAO; do not re-create a hidden union for normal explicit/automatic operations.
- [ ] **Step 5: Keep reconciliation union explicit** and test missing/orphan repair across both tables.
- [ ] **Step 6: Run download/cache/Reader storage/migration tests and Room schema verification**.
- [ ] **Step 7: Commit** `perf(storage): split explicit downloads from automatic cache metadata`.

---

### Task 2: Make automatic-cache accounting incremental after initialization

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinator.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinatorTest.kt`
- Test: `reader/src/test/kotlin/app/openstory/reader/assets/ReaderAssetCoordinatorTest.kt`

**Interfaces:**
- `snapshot()` becomes O(1) with respect to `AC/RA` after `ensureInitializedLocked()`.
- Explicit repair/reconciliation may recompute durable usage.

- [ ] **Step 1: Add a fake-repository call-count test**: initialize coordinator once, call `snapshot()`/Reader `cachePressure()` repeatedly, assert automatic-document and Reader-asset usage queries run once rather than once per call.
- [ ] **Step 2: Add ledger invariant tests** for document publish/replace, image publish/replace, detach, invalidate, clear, quota change, reservation release, failed publish, and process recreation.
- [ ] **Step 3: Change `snapshot()`** to return `committedBytes` plus reservations without `recomputeCommittedBytesLocked()`.
- [ ] **Step 4: Audit every successful logical publish/detach path** and adjust `committedBytes` exactly once; include replacement bytes.
- [ ] **Step 5: Keep `reconcile()`/explicit repair able to recompute durable usage** and emit diagnostics if repaired value differs from ledger.
- [ ] **Step 6: Run budget/Reader integration/process-recreation tests**.
- [ ] **Step 7: Run Wave-0 Reader viewport/cache-pressure scaling fixture** and verify repeated viewport planning no longer scales with `AC + RA` metadata count.
- [ ] **Step 8: Commit** `perf(reader): maintain automatic cache usage incrementally`.

---

### Task 3: Replace global eviction rereads with bounded candidates and point detach

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetMetadataRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/readerassets/RoomReaderAssetMetadataRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinator.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinatorTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepositoryTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/readerassets/RoomReaderAssetMetadataRepositoryTest.kt`

**Interfaces:**

```kotlin
suspend fun automaticEvictionCandidates(limit: Int): List<CacheEntry>
suspend fun detachAutomaticIfCurrent(expected: CacheEntry): CacheEntry?
```

Add analogous bounded candidate/point-detach methods to `ReaderAssetMetadataRepository` keyed by logical asset hash + generation/blob ID.

- [ ] **Step 1: Add coordinator test** with many unrelated metadata rows and multiple victims; assert one candidate query per pass and one point-currentness call per attempted victim, not full `entries()` rereads.
- [ ] **Step 2: Add Room point-detach transaction tests** where metadata changes between candidate snapshot and eviction attempt; stale expected victim must not detach the newer generation.
- [ ] **Step 3: Implement bounded LRU candidate queries** with explicit limit/order and no full-table materialization beyond the requested candidate page.
- [ ] **Step 4: Implement atomic point detach** comparing current key/generation/checksum/size fields required by currentness semantics.
- [ ] **Step 5: Refactor `candidatesLocked()`/`detachDocumentIfCurrentLocked()`** to use the new APIs; remove global metadata reread from victim loop.
- [ ] **Step 6: Run cache/Room/reconciliation tests**.
- [ ] **Step 7: Commit** `perf(downloads): evict automatic cache through bounded point operations`.

---

### Task 4: Split fast quota state locking from publication/invalidation ordering

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinator.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/cache/AutomaticCacheBudgetCoordinatorTest.kt`
- Test: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetSecurityInvalidationObserverTest.kt`
- Test: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetIntegrationTest.kt`

**Interfaces:**
- `stateGate`: in-memory quota/epoch/reservation/ledger/protection state only.
- `publicationOrderGate`: minimal metadata-publication vs invalidation linearization only.

- [ ] **Step 1: Add concurrency test**: block a fake filesystem deletion/metadata maintenance call, concurrently request `snapshot()/captureWriteAuthority()/reserve()`, and assert foreground fast-state operation is not blocked by the physical I/O.
- [ ] **Step 2: Add security-order tests** covering invalidation during prepared write, publication during scoped invalidation, and newer generation winning while old physical deletion is delayed.
- [ ] **Step 3: Introduce `stateGate`** and move only mutable in-memory state under it.
- [ ] **Step 4: Introduce minimal `publicationOrderGate`** around metadata publication/invalidation critical transitions; no broad list query or blob delete while held.
- [ ] **Step 5: Detach logical metadata first**, capture detached generation, release locks, then delete generation-addressed physical blob through existing lease-aware maintenance.
- [ ] **Step 6: Audit `clearAutomatic`, normal quota eviction, emergency pressure, image invalidation and publication** for accidental lock reentry/order inversion.
- [ ] **Step 7: Run RICC security/invalidation/process recreation suites**.
- [ ] **Step 8: Commit** `perf(reader): shorten automatic cache critical sections`.

---

### Task 5: Replace repeated Reader payload copies with immutable encoded-source access

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/assets/ReaderAssetDeliveryPort.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetBlobStore.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStore.kt`
- Modify: `storage/files/src/main/kotlin/app/openstory/storage/files/AtomicFileReaderAssetBlobStore.kt`
- Modify: `app/src/main/kotlin/app/openstory/reader/assets/ReaderAssetCoilFetcher.kt`
- Test: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetCoilFetcherTest.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStoreTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/metadata/CatalogMetadataCoordinator.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/metadata/CatalogMetadataCoordinatorTest.kt`
- Test: `storage/files/src/test/kotlin/app/openstory/storage/files/AtomicFileReaderAssetBlobStoreTest.kt`

**Interfaces:**

```kotlin
interface ReaderAssetEncodedSource {
    val sizeBytes: Long
    fun openStream(): InputStream
}
```

`ReaderAssetPayload` owns one immutable encoded backing store and implements/exposes a read-only source. Blob write accepts a source + declared bound instead of a newly copied `ByteArray`.

- [ ] **Step 1: Add allocation/identity tests** using a counting source: Coil read and blob write must not call `payload.bytes()`/make a second defensive copy of the full payload.
- [ ] **Step 2: Add size-bound tests** proving stream-based write rejects declared/actual payload beyond `MAX_READER_ASSET_BYTES` and cleans partial temp files.
- [ ] **Step 3: Add `openStream()` to immutable payload** while retaining exactly one defensive copy at external byte-array ingestion; do not expose the backing array.
- [ ] **Step 4: Evolve `ReaderAssetBlobStore.writeAtomic`** to accept the encoded source and stream to the atomic temp file while computing/checking size/checksum.
- [ ] **Step 5: Refactor `DownloadReaderAssetStore.prepareCommit`** to reserve from `payload.sizeBytes` and write from the source without storing another `ByteArray` in `PreparedReaderAssetCommit`.
- [ ] **Step 6: Refactor Coil remote fetcher** to consume a buffered source/stream directly.
- [ ] **Step 7: For local verified reads**, hash the leased stream then reopen through the same physical read lease instead of retaining a second verified full `ByteArray`, if the blob-store lease contract supports repeat `openStream()`; otherwise introduce a read-only verified source that owns the one necessary buffer and document the exact copy bound.
- [ ] **Step 8: Run Reader asset/Coil/blob-store/security tests and Wave-0 memory benchmark**.
- [ ] **Step 9: Commit** `perf(reader): stream immutable encoded asset payloads`.

---

### Task 6: Bound confirmed process-lifetime operational memoization

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/assets/ReaderAssetTouchMemo.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStore.kt`
- Create: `downloads/src/test/kotlin/app/openstory/downloads/assets/ReaderAssetTouchMemoTest.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/assets/DownloadReaderAssetStoreTest.kt`

**Interfaces:**
- Encapsulates Reader touch throttling with explicit `maxEntries` + `ttlNanos` and `remove(key)`.
- `CatalogMetadataCoordinator.suppressions` gains explicit maximum cardinality plus global lazy expiry/pruning; plugin-version suppression invalidation remains correct. `inFlight` is explicitly excluded because completion removes entries.

- [ ] **Step 1: Add Reader memo tests** for throttle hit, TTL expiry, LRU/cap eviction, explicit removal, monotonic clock movement, and thousands of unique keys never exceeding cap.
- [ ] **Step 2: Add Catalog metadata tests** that generate thousands of distinct cooldown/version-suppressed keys, advance the clock, and assert retained suppression cardinality stays within the declared cap while expired cooldowns can be reclaimed without revisiting the same key.
- [ ] **Step 3: Implement focused Reader memo class** rather than embedding another mutable map in the store.
- [ ] **Step 4: Replace `lastAccessTouches`** and call `remove` when asset metadata is invalidated/detached where the store has the event.
- [ ] **Step 5: Bound `CatalogMetadataCoordinator.suppressions`** under its existing mutex with deterministic pruning: remove expired cooldowns opportunistically, preserve same-key plugin-version invalidation, and hard-cap total entries. Evicting operational suppression may only permit a later retry; it must never mutate durable metadata/canonical truth.
- [ ] **Step 6: Preserve current Reader touch-write throttling and Catalog same-key single-flight/suppression semantics**; do not serialize metadata loads under the suppression mutex.
- [ ] **Step 7: Search production singleton/process-scope mutable maps** and document why each remaining map is completion-bounded, installed-plugin-bounded, explicitly capped/TTL'd, or an explicit unresolved risk; do not silently expand X12 to correct self-pruning maps.
- [ ] **Step 8: Run downloads + catalog tests and a process-aging memory fixture**.
- [ ] **Step 9: Commit** `perf(memory): bound process-lifetime operational memoization`.

---

### Task 7: Close Reader/cache benchmark and RICC regression gate

**Files:**
- Benchmark: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Create: `docs/superpowers/checkpoints/2026-09-07-hikari-perf-wave-5-reader-cache.md`

- [ ] **Step 1: Run focused tests**: `./gradlew :downloads:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :storage:files:test :storage:room:connectedDebugAndroidTest --no-daemon` with relevant filters if full connected suite is impractical.
- [ ] **Step 2: Run package/schema/structural verification scripts**.
- [ ] **Step 3: Run image scroll cold/warm memory benchmarks** across at least low/high `RA/AC` with same visible pages and compare slope.
- [ ] **Step 4: Verify `cachePressure()` query count is independent of `Fv × (RA+AC)` after first initialization.**
- [ ] **Step 5: Verify max memory/transient allocation improves for large `Y` without checksum/security regressions.**
- [ ] **Step 6: Record checkpoint and commit** `test(perf): close reader cache performance wave`.

---

## Wave 5 acceptance gate

- [ ] automatic-cache writes no longer invalidate explicit-download observers.
- [ ] repeated viewport planning does not recompute global usage.
- [ ] eviction is one bounded candidate read + point detaches.
- [ ] no global/file I/O is held under the fast cache state lock.
- [ ] production remote asset path has bounded full-payload copy count.
- [ ] Reader asset touch memoization and Catalog metadata suppression retention have explicit cap/TTL/invalidation contracts.
- [ ] all RICC security/integrity/process-recreation tests pass.
