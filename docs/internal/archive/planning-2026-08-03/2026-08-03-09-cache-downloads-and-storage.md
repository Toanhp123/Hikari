# Wave 09 — Cache, Downloads, and Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide reliable offline reading by separating disposable cache from explicit downloads and managing both atomically under device storage limits.

**Architecture:** Sanitized reader documents are stored as versioned verified blobs in app-private cache/download namespaces. Room indexes retention and download state. Reader resolution uses download → cache → network order, while reconciliation repairs interrupted writes and low-storage failures.

**Tech Stack:** Kotlin, app-private filesystem, Room, WorkManager-neutral download engine, Compose storage/download UI, coroutines.

## Global Constraints

- Android-only MVP; no account, cloud sync, remote chapter service, or push backend.
- Package namespace: `app.openstory`.
- Minimum SDK: 26. Compile and target SDK: 37.
- Build runtime: JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0.
- Language/UI: Kotlin 2.4.10, Jetpack Compose BOM 2026.06.00, Navigation 3 version 1.1.4.
- Persistence/background: Room 2.8.4 and WorkManager 2.11.2.
- Concurrency/serialization: Kotlin coroutines 1.11.0 and kotlinx.serialization 1.11.0.
- Dependency injection: Hilt 2.60.1.
- JavaScript plugins execute only through AndroidX JavaScriptEngine 1.1.0 with host-controlled capabilities.
- Catalog metadata and readable content remain separate plugin responsibilities.
- Reading progress belongs to `CanonicalChapter`; exact `ChapterRelease` and reader position are also retained.
- No native-code plugins, unrestricted filesystem access, arbitrary Android APIs, or undeclared network domains.
- Every persistence change needs a migration test; every plugin contract needs deterministic fixtures.
- TDD is mandatory: demonstrate the focused failure, implement the smallest behavior, run focused tests, then run the module suite.
- Commit after each task. Do not combine tasks across checkpoints.
- Any deterministic `*Fixture`, fake, or test assertion helper shown in a test block is created in that task’s listed test file or `:test:fixtures`; it must not call live websites.


## Role of This Wave

This wave makes local-first reading real. Its central rule is that cache cleanup is safe and explicit downloads are durable user data with distinct lifecycle and integrity states.

## Entry Dependencies

- Wave 08 checkpoint is approved.
- Reader loads exact sanitized documents and persists progress.
- Room schema can add cache/download tables with migration tests.

## Exit Deliverables

- Atomic chapter blob storage.
- Quota-managed automatic cache.
- Explicit download queue/state machine.
- Offline-first reader repository.
- Download/storage management UI.
- Filesystem/database reconciliation and low-space handling.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Create app-private atomic chapter blob storage with integrity metadata

**Files:**
- Create: core/files/build.gradle.kts
- Create: core/files/src/main/kotlin/app/openstory/files/ChapterBlobStore.kt
- Create: core/files/src/main/kotlin/app/openstory/files/AtomicFileChapterBlobStore.kt
- Create: core/files/src/main/kotlin/app/openstory/files/BlobMetadata.kt
- Create: core/files/src/main/kotlin/app/openstory/files/StoragePaths.kt
- Test: core/files/src/test/kotlin/app/openstory/files/AtomicFileChapterBlobStoreTest.kt

**Interfaces:**
- Consumes: Sanitized `ReaderDocument`, release IDs/fingerprints, app-private files directory adapter, dispatchers.
- Produces: Atomic serialized document blob store with checksum, schema version, source fingerprint, byte count, and separate cache/download roots.

**Acceptance:**
- Writes use temp file, fsync/close, atomic rename, then metadata commit.
- Read verifies checksum/schema/release identity before deserializing.
- Path is derived from hashed stable ID, never raw title/URL.
- Cache and download paths are physically and logically distinct.

**Implementation notes:**
- Serialize a versioned host `ReaderDocument` DTO, not Java object serialization or plugin raw HTML.
- Use app-private no-backup directories for downloaded chapter bodies unless device-transfer behavior is explicitly changed.
- Corrupt blobs are quarantined/deleted and reported; they never crash reader.

- [ ] **Step 1: Write the failing test**

Create `core/files/src/test/kotlin/app/openstory/files/AtomicFileChapterBlobStoreTest.kt`:

```kotlin
package app.openstory.files

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicFileChapterBlobStoreTest {
    @Test fun interruptedWriteNeverReplacesValidBlob() = runTest {
        val fixture = blobStoreFixture()
        fixture.store.write(CacheNamespace, ReleaseId("r1"), fixture.document("old"))
        fixture.files.failNextRename = true
        fixture.store.write(CacheNamespace, ReleaseId("r1"), fixture.document("new"))
        assertEquals("old", fixture.store.read(CacheNamespace, ReleaseId("r1")).value().document.title)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:files:test --tests app.openstory.files.AtomicFileChapterBlobStoreTest.interruptedWriteNeverReplacesValidBlob
```

Expected: **FAIL** because atomic blob storage and namespace separation are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/files/src/main/kotlin/app/openstory/files/ChapterBlobStore.kt`:

```kotlin
package app.openstory.files

interface ChapterBlobStore {
    suspend fun write(namespace: BlobNamespace, releaseId: ReleaseId, document: ReaderDocument): AppResult<BlobMetadata>
    suspend fun read(namespace: BlobNamespace, releaseId: ReleaseId): AppResult<StoredReaderDocument?>
    suspend fun delete(namespace: BlobNamespace, releaseId: ReleaseId): AppResult<Unit>
    suspend fun list(namespace: BlobNamespace): List<BlobMetadata>
}

sealed interface BlobNamespace { data object Cache : BlobNamespace; data object Downloads : BlobNamespace }
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:files:test --tests app.openstory.files.AtomicFileChapterBlobStoreTest.interruptedWriteNeverReplacesValidBlob
./gradlew :core:files:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/files/build.gradle.kts core/files/src/main/kotlin/app/openstory/files/ChapterBlobStore.kt core/files/src/main/kotlin/app/openstory/files/AtomicFileChapterBlobStore.kt core/files/src/main/kotlin/app/openstory/files/BlobMetadata.kt core/files/src/main/kotlin/app/openstory/files/StoragePaths.kt core/files/src/test/kotlin/app/openstory/files/AtomicFileChapterBlobStoreTest.kt
git commit -m "files: add atomic chapter blob storage"
```

### Task 2: Implement automatic cache index, access tracking, and quota eviction

**Files:**
- Create: core/files/src/main/kotlin/app/openstory/files/cache/ChapterCache.kt
- Create: core/files/src/main/kotlin/app/openstory/files/cache/RoomChapterCache.kt
- Create: core/database/src/main/kotlin/app/openstory/database/entity/CacheEntryEntity.kt
- Create: core/database/src/main/kotlin/app/openstory/database/dao/CacheEntryDao.kt
- Create: core/files/src/main/kotlin/app/openstory/files/cache/CacheEvictionPolicy.kt
- Test: core/files/src/test/kotlin/app/openstory/files/cache/CacheEvictionPolicyTest.kt

**Interfaces:**
- Consumes: Atomic blob store, Room cache index, clock, storage settings.
- Produces: Disposable reader cache with max bytes/max age, last-access tracking, pin-while-open protection, and LRU-like deterministic eviction.

**Acceptance:**
- Eviction never targets explicit download namespace.
- Currently open/pinned release is skipped.
- Missing/corrupt files reconcile out of index.
- Quota enforcement deletes oldest eligible entries until under target, not all cache.

**Implementation notes:**
- Default quota is a product setting with a conservative device-space-aware value; user can change/clear it.
- Touch last-access at most once per bounded interval to avoid Room writes on every reader composition.
- Schedule eviction after successful cache write and on explicit storage cleanup, not on the UI main thread.

- [ ] **Step 1: Write the failing test**

Create `core/files/src/test/kotlin/app/openstory/files/cache/CacheEvictionPolicyTest.kt`:

```kotlin
package app.openstory.files.cache

import kotlin.test.Test
import kotlin.test.assertEquals

class CacheEvictionPolicyTest {
    @Test fun evictionSkipsPinnedAndDownloadedEntries() {
        val policy = CacheEvictionPolicy(maxBytes = 100)
        val candidates = listOf(
            cacheEntry("old", bytes = 80, lastAccess = 1),
            cacheEntry("pinned", bytes = 80, lastAccess = 2, pinned = true),
            cacheEntry("new", bytes = 80, lastAccess = 3),
        )
        assertEquals(listOf("old", "new"), policy.selectForEviction(candidates, currentBytes = 240).map { it.releaseId.value })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:files:test --tests app.openstory.files.cache.CacheEvictionPolicyTest.evictionSkipsPinnedAndDownloadedEntries
```

Expected: **FAIL** because cache index and eviction policy are undefined.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/files/src/main/kotlin/app/openstory/files/cache/CacheEvictionPolicy.kt`:

```kotlin
package app.openstory.files.cache

class CacheEvictionPolicy(private val maxBytes: Long) {
    fun selectForEviction(entries: List<CacheEntry>, currentBytes: Long): List<CacheEntry> {
        var remaining = currentBytes
        return buildList {
            for (entry in entries.filterNot { it.pinned }.sortedBy { it.lastAccessEpochMillis }) {
                if (remaining <= maxBytes) break
                add(entry)
                remaining -= entry.bytes
            }
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:files:test --tests app.openstory.files.cache.CacheEvictionPolicyTest.evictionSkipsPinnedAndDownloadedEntries
./gradlew :core:files:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/files/src/main/kotlin/app/openstory/files/cache/ChapterCache.kt core/files/src/main/kotlin/app/openstory/files/cache/RoomChapterCache.kt core/database/src/main/kotlin/app/openstory/database/entity/CacheEntryEntity.kt core/database/src/main/kotlin/app/openstory/database/dao/CacheEntryDao.kt core/files/src/main/kotlin/app/openstory/files/cache/CacheEvictionPolicy.kt core/files/src/test/kotlin/app/openstory/files/cache/CacheEvictionPolicyTest.kt
git commit -m "cache: add quota and deterministic eviction"
```

### Task 3: Implement explicit offline download state machine and worker-neutral engine

**Files:**
- Create: core/files/src/main/kotlin/app/openstory/files/download/ChapterDownloadManager.kt
- Create: core/files/src/main/kotlin/app/openstory/files/download/DownloadQueue.kt
- Create: core/files/src/main/kotlin/app/openstory/files/download/DownloadState.kt
- Create: core/database/src/main/kotlin/app/openstory/database/entity/DownloadEntity.kt
- Create: core/database/src/main/kotlin/app/openstory/database/dao/DownloadDao.kt
- Test: core/files/src/test/kotlin/app/openstory/files/download/ChapterDownloadManagerTest.kt

**Interfaces:**
- Consumes: Reader content repository network loader, blob store download namespace, Room download table, plugin host/version pin.
- Produces: Resumable logical queue for single/range/all-known release downloads with QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED and integrity metadata.

**Acceptance:**
- Download targets exact release IDs chosen by policy/user.
- Completed means verified blob written and database state committed.
- Retry is idempotent and does not duplicate blobs/rows.
- Cancellation stops future items and leaves completed items valid.
- Source removal does not remove completed downloads.

**Implementation notes:**
- Keep pure manager independent of WorkManager; Wave 10 supplies scheduling adapter.
- For “all”, snapshot known canonical chapters/releases at request time and show count; later new chapters are not silently included unless user opts into auto-download after MVP.
- Store source fingerprint/plugin version so stale downloaded content can be labeled without forcing replacement.

- [ ] **Step 1: Write the failing test**

Create `core/files/src/test/kotlin/app/openstory/files/download/ChapterDownloadManagerTest.kt`:

```kotlin
package app.openstory.files.download

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterDownloadManagerTest {
    @Test fun retryAfterCommitDoesNotDuplicateCompletedDownload() = runTest {
        val fixture = downloadManagerFixture()
        fixture.manager.download(fixture.request("r1"))
        fixture.manager.download(fixture.request("r1"))
        assertEquals(1, fixture.blobWrites)
        assertEquals(DownloadState.COMPLETED, fixture.downloadDao.state("r1"))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:files:test --tests app.openstory.files.download.ChapterDownloadManagerTest.retryAfterCommitDoesNotDuplicateCompletedDownload
```

Expected: **FAIL** because offline download state and idempotency are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/files/src/main/kotlin/app/openstory/files/download/DownloadState.kt`:

```kotlin
package app.openstory.files.download

enum class DownloadState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }

data class DownloadRequest(
    val id: DownloadId,
    val storyId: StoryId,
    val chapterId: ChapterId,
    val releaseId: ReleaseId,
    val requestedAtEpochMillis: Long,
)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:files:test --tests app.openstory.files.download.ChapterDownloadManagerTest.retryAfterCommitDoesNotDuplicateCompletedDownload
./gradlew :core:files:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/files/src/main/kotlin/app/openstory/files/download/ChapterDownloadManager.kt core/files/src/main/kotlin/app/openstory/files/download/DownloadQueue.kt core/files/src/main/kotlin/app/openstory/files/download/DownloadState.kt core/database/src/main/kotlin/app/openstory/database/entity/DownloadEntity.kt core/database/src/main/kotlin/app/openstory/database/dao/DownloadDao.kt core/files/src/test/kotlin/app/openstory/files/download/ChapterDownloadManagerTest.kt
git commit -m "downloads: add explicit chapter queue and state machine"
```

### Task 4: Resolve reader content in download, cache, then network order

**Files:**
- Modify: core/reader/src/main/kotlin/app/openstory/reader/PluginReaderContentRepository.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/CompositeReaderContentRepository.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReaderContentPolicy.kt
- Test: core/reader/src/test/kotlin/app/openstory/reader/CompositeReaderContentRepositoryTest.kt

**Interfaces:**
- Consumes: Exact release reader repository, chapter blob store, cache index, download DAO, sanitizer.
- Produces: Composite repository that reads verified explicit download first, disposable cache second, then plugin network; successful network reads populate cache.

**Acceptance:**
- Airplane-mode open succeeds from explicit download or cache without plugin invocation.
- Corrupt download is reported and may fall back to valid cache/network only with clear source state.
- Network success writes cache after returning/within controlled coroutine without blocking progress restore excessively.
- Exact release identity remains unchanged across layers.

**Implementation notes:**
- Distinguish not-found from corrupt/read-error so fallback and diagnostics are accurate.
- Never overwrite explicit download automatically when network fingerprint changes; label update availability and require user choice/explicit policy.
- Pin cache entry while the document is open.

- [ ] **Step 1: Write the failing test**

Create `core/reader/src/test/kotlin/app/openstory/reader/CompositeReaderContentRepositoryTest.kt`:

```kotlin
package app.openstory.reader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeReaderContentRepositoryTest {
    @Test fun offlineDownloadPreventsPluginCall() = runTest {
        val fixture = compositeReaderFixture(downloaded = true, networkAvailable = false)
        val loaded = fixture.repository.load(fixture.chapterId, fixture.releaseId).value()
        assertEquals(ReaderContentSource.DOWNLOAD, loaded.source)
        assertEquals(0, fixture.pluginCalls)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.CompositeReaderContentRepositoryTest.offlineDownloadPreventsPluginCall
```

Expected: **FAIL** because reader has no storage-layer precedence or offline path.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/reader/src/main/kotlin/app/openstory/reader/CompositeReaderContentRepository.kt`:

```kotlin
package app.openstory.reader

class CompositeReaderContentRepository(
    private val downloads: ReaderBlobSource,
    private val cache: ReaderBlobSource,
    private val network: ReaderContentRepository,
    private val cacheWriter: ReaderCacheWriter,
) : ReaderContentRepository {
    override suspend fun load(chapterId: ChapterId, releaseId: ReleaseId): AppResult<LoadedReaderDocument> {
        downloads.load(chapterId, releaseId).getOrNull()?.let { return AppResult.Success(it) }
        cache.load(chapterId, releaseId).getOrNull()?.let { return AppResult.Success(it) }
        return network.load(chapterId, releaseId).tapSuccess { cacheWriter.store(it) }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.CompositeReaderContentRepositoryTest.offlineDownloadPreventsPluginCall
./gradlew :core:reader:test :core:files:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/reader/src/main/kotlin/app/openstory/reader/PluginReaderContentRepository.kt core/reader/src/main/kotlin/app/openstory/reader/CompositeReaderContentRepository.kt core/reader/src/main/kotlin/app/openstory/reader/ReaderContentPolicy.kt core/reader/src/test/kotlin/app/openstory/reader/CompositeReaderContentRepositoryTest.kt
git commit -m "reader: prefer downloads and cache before network"
```

### Task 5: Build download actions and storage management UI

**Files:**
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/download/DownloadActionSheet.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/download/DownloadViewModel.kt
- Create: feature/settings/src/main/kotlin/app/openstory/settings/storage/StorageViewModel.kt
- Create: feature/settings/src/main/kotlin/app/openstory/settings/storage/StorageScreen.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/DownloadIndicator.kt
- Test: feature/story/src/test/kotlin/app/openstory/story/ui/download/DownloadViewModelTest.kt
- Test: feature/settings/src/androidTest/kotlin/app/openstory/settings/storage/StorageScreenTest.kt

**Interfaces:**
- Consumes: Download queue/state, canonical chapter/release selection, cache stats/cleanup, settings navigation.
- Produces: UI for one chapter/range/all-known downloads, per-item state/retry/cancel, story storage size, cache quota, clear-cache, and explicit download removal.

**Acceptance:**
- User sees exact release/language/source selection before multi-download begins.
- Clear cache action reports estimated bytes and never selects downloads.
- Deleting downloads is distinct and confirms count/bytes.
- Reader/story rows reflect completed/in-progress/failed states.

**Implementation notes:**
- Show app-private storage sizes as estimates that update after reconciliation.
- Use canonical chapter list ordering and release selection policy; allow per-item override before enqueue.
- Do not add automatic download of future chapters to MVP.

- [ ] **Step 1: Write the failing test**

Create `feature/story/src/test/kotlin/app/openstory/story/ui/download/DownloadViewModelTest.kt`:

```kotlin
package app.openstory.story.ui.download

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadViewModelTest {
    @Test fun allKnownUsesPreferredReleasePerCanonicalChapter() = runTest {
        val fixture = downloadViewModelFixture(chapterCount = 3, releasesPerChapter = 2)
        fixture.viewModel.prepareAllKnown()
        assertEquals(3, fixture.viewModel.state.value.confirmation!!.items.size)
        assertEquals(listOf("vi", "vi", "vi"), fixture.viewModel.state.value.confirmation!!.items.map { it.language.value })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:story:test --tests app.openstory.story.ui.download.DownloadViewModelTest.allKnownUsesPreferredReleasePerCanonicalChapter
```

Expected: **FAIL** because download preparation and storage-management UI state are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/settings/src/main/kotlin/app/openstory/settings/storage/StorageViewModel.kt`:

```kotlin
package app.openstory.settings.storage

@HiltViewModel
class StorageViewModel @Inject constructor(
    stats: ObserveStorageStats,
    private val clearCache: ClearChapterCache,
    private val removeDownloads: RemoveDownloads,
) : ViewModel() {
    val state = stats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState.Empty)
    fun clearDisposableCache() = viewModelScope.launch { clearCache() }
    fun removeConfirmed(ids: Set<DownloadId>) = viewModelScope.launch { removeDownloads(ids) }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:story:test --tests app.openstory.story.ui.download.DownloadViewModelTest.allKnownUsesPreferredReleasePerCanonicalChapter
./gradlew :feature:story:test :feature:settings:test :feature:settings:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/story/src/main/kotlin/app/openstory/story/ui/download/DownloadActionSheet.kt feature/story/src/main/kotlin/app/openstory/story/ui/download/DownloadViewModel.kt feature/settings/src/main/kotlin/app/openstory/settings/storage/StorageViewModel.kt feature/settings/src/main/kotlin/app/openstory/settings/storage/StorageScreen.kt feature/reader/src/main/kotlin/app/openstory/reader/ui/DownloadIndicator.kt feature/story/src/test/kotlin/app/openstory/story/ui/download/DownloadViewModelTest.kt feature/settings/src/androidTest/kotlin/app/openstory/settings/storage/StorageScreenTest.kt
git commit -m "downloads: add chapter and storage management ui"
```

### Task 6: Reconcile database/filesystem state and verify low-storage behavior

**Files:**
- Create: core/files/src/main/kotlin/app/openstory/files/StorageReconciler.kt
- Create: core/files/src/main/kotlin/app/openstory/files/AvailableSpaceGuard.kt
- Create: core/files/src/main/kotlin/app/openstory/files/ClearChapterCache.kt
- Create: core/files/src/test/kotlin/app/openstory/files/StorageReconcilerTest.kt
- Create: core/files/src/androidTest/kotlin/app/openstory/files/LowStorageIntegrationTest.kt
- Modify: scripts/verify.sh

**Interfaces:**
- Consumes: Blob store, cache/download indexes, download state machine, storage stats.
- Produces: Idempotent reconciliation and space guard that recover orphan temp/index files, prevent unsafe writes, and provide deterministic cleanup behavior.

**Acceptance:**
- Orphan temp files older than active-write window are removed.
- Missing completed-download blob becomes FAILED_INTEGRITY, not silently completed.
- Orphan valid blob is adopted only when metadata proves release identity; otherwise quarantined/deleted.
- Download begins only when available bytes exceed payload estimate plus safety reserve.
- Cache write failure never prevents current network document from being read.

**Implementation notes:**
- Run reconciliation at startup after database open, after abnormal write detection, and from diagnostics—not continuously.
- Test with fake filesystem for deterministic unit coverage and one Android integration test for actual private directories/atomic rename.
- Add storage tests to verification script and keep fixture files small.

- [ ] **Step 1: Write the failing test**

Create `core/files/src/test/kotlin/app/openstory/files/StorageReconcilerTest.kt`:

```kotlin
package app.openstory.files

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageReconcilerTest {
    @Test fun missingDownloadBlobBecomesIntegrityFailure() = runTest {
        val fixture = storageReconcilerFixture(completedRowWithoutFile = true)
        fixture.reconciler.reconcile()
        assertEquals(DownloadState.FAILED, fixture.downloadState())
        assertEquals("download.integrity_missing", fixture.downloadErrorCode())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:files:test --tests app.openstory.files.StorageReconcilerTest.missingDownloadBlobBecomesIntegrityFailure
```

Expected: **FAIL** because storage reconciliation and low-space guard do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/files/src/main/kotlin/app/openstory/files/AvailableSpaceGuard.kt`:

```kotlin
package app.openstory.files

class AvailableSpaceGuard(private val reserveBytes: Long) {
    fun canWrite(availableBytes: Long, estimatedPayloadBytes: Long): Boolean =
        availableBytes > reserveBytes && estimatedPayloadBytes >= 0 && availableBytes - reserveBytes >= estimatedPayloadBytes
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:files:test --tests app.openstory.files.StorageReconcilerTest.missingDownloadBlobBecomesIntegrityFailure
./gradlew :core:files:test :core:files:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/files/src/main/kotlin/app/openstory/files/StorageReconciler.kt core/files/src/main/kotlin/app/openstory/files/AvailableSpaceGuard.kt core/files/src/main/kotlin/app/openstory/files/ClearChapterCache.kt core/files/src/test/kotlin/app/openstory/files/StorageReconcilerTest.kt core/files/src/androidTest/kotlin/app/openstory/files/LowStorageIntegrationTest.kt scripts/verify.sh
git commit -m "files: reconcile storage and handle low space"
```

## Wave Checkpoint

Do not begin `2026-08-03-10-background-sync-auth-and-notifications.md` until every item below is demonstrated on a clean checkout:

- [ ] Networking disabled: downloaded and cached releases open successfully.
- [ ] Clear cache removes no explicit download.
- [ ] Interrupted/corrupt writes do not replace valid blobs or crash reader.
- [ ] Retry/cancel is idempotent and storage states remain consistent.
- [ ] Migration schema and API 26/37 storage tests pass.

## Full Verification

```bash
./gradlew clean testDebugUnitTest lintDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**, no ignored failing tests, no unresolved lint errors, and no generated database schema drift.

## Review Packet

Attach to the checkpoint review:

- Commit range for this wave.
- Focused test output for every task.
- Full verification output.
- Any deliberate deviations from the approved design, with rationale and updated spec text.
- Screenshots or screen recordings only when the wave changes visible UI.
