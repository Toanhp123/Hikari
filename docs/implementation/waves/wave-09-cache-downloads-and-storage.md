<!-- DOCUMENT LIFECYCLE: COMPLETED / CHECKPOINT VERIFIED 2026-08-11 -->

# Wave 09 - Cache, Downloads, and Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`; use TDD and commit each task.

**Goal:** Add integrity-checked cache and explicit offline downloads without coupling Reader policy to filesystem implementation.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces `:downloads` and `:storage:files`. Downloads owns state/retention/resolution policy; file storage owns atomic bytes; Room owns metadata transactions.

## Global Constraints

- Entry module graph: Wave 08 exit graph.
- Exit module graph: entry graph plus `:downloads` and `:storage:files`.
- Introduces `:downloads` and `:storage:files` in Task 1.
- Consumes from Wave 08: `ReaderDocumentStore`, sanitized documents, release IDs/fingerprints, and progress.
- Produces for Wave 10: cache/download engines, quota state, reconciliation reports, and worker-safe commands.
- Room schema 5 enters; schema 6 adds cache/download metadata.
- Explicit downloads are never evicted as cache and never deleted by plugin removal.
- No periodic scheduling, auth, notifications, or settings UI.

### Task 1: Introduce download and file-storage boundaries

**Files:**
- Create: `downloads/build.gradle.kts`, `downloads/src/main/kotlin/app/openstory/downloads/blob/ChapterBlobStore.kt`, `BlobModels.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/blob/ChapterBlobStoreContractTest.kt`
- Create: `storage/files/build.gradle.kts`, `storage/files/src/main/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStore.kt`
- Test: `storage/files/src/test/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStoreTest.kt`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`
- Create: `app/src/main/kotlin/app/openstory/di/DownloadModule.kt`

- [x] Write RED tests for temp-write, fsync/close, atomic rename, checksum, path confinement, interrupted write, and exact exit graph.
- [x] Implement `ChapterBlobStore` port in Downloads and `AtomicFileChapterBlobStore` adapter without exposing paths.
- [x] Run `./gradlew :downloads:test :storage:files:test :verifyArchitecture detekt --stacktrace`.
- [x] Commit `downloads: add atomic content storage boundary`.

### Task 2: Implement cache quota and eviction policy

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheModels.kt`, `CacheEvictionPolicy.kt`, `CacheRepository.kt`, `CacheService.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/cache/CacheEvictionPolicyTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadEntities.kt`, `DownloadDao.kt`, `RoomDownloadRepository.kt`
- Modify: `storage/room/build.gradle.kts`, `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`, `RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/DownloadMigrationTest.kt`

- [x] Write RED tests for LRU access, quota, pinned/current/progress protection, explicit-download exclusion, and schema `5 -> 6` preservation.
- [x] Implement pure eviction plans and Room metadata transactions; file deletion follows committed plans with reconciliation safety.
- [x] Run `./gradlew :downloads:test :storage:room:connectedDebugAndroidTest --stacktrace` and `./scripts/verify-room-schema-stability.sh`.
- [x] Commit `downloads: add bounded chapter cache`.

### Task 3: Implement explicit download state machine

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/DownloadModels.kt`, `DownloadRepository.kt`, `DownloadService.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/DownloadServiceTest.kt`
- Create: `app/src/main/kotlin/app/openstory/work/ChapterDownloadWorker.kt`
- Test: `app/src/test/kotlin/app/openstory/work/ChapterDownloadWorkerTest.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`

- [x] Write RED tests for queued/running/completed/failed/cancelled, idempotent retry, checksum mismatch, cancellation cleanup, and worker delegation.
- [x] Implement capability-owned transitions and one-ID WorkManager adapter.
- [x] Run `./gradlew :downloads:test :app:testDebugUnitTest :storage:room:connectedDebugAndroidTest --stacktrace`.
- [x] Commit `downloads: add explicit offline state machine`.

### Task 4: Provide download-cache-network Reader resolution

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStoreTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`, `feature/reader/build.gradle.kts`

- [x] Write RED tests proving explicit download, cache, then network order; successful sanitized network reads write into automatic cache; corrupt local bytes are quarantined; fallback never marks remote failure as local success.
- [x] Implement the Reader port adapter without adding a Reader-to-Downloads dependency cycle.
- [x] Run `./gradlew :reader:test :downloads:test :feature:reader:testDebugUnitTest :app:testDebugUnitTest --stacktrace`.
- [x] Commit `downloads: resolve reader content offline first`.

### Task 5: Add download actions and indicators

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadActionSheet.kt`, `DownloadViewModel.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/download/DownloadViewModelTest.kt`
- Create: `feature/reader/src/main/kotlin/app/openstory/reader/ui/DownloadIndicator.kt`
- Test: `feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/DownloadIndicatorTest.kt`

- [x] Write RED tests for one release/range/filter commands, progress/cancel/retry, offline indicator, and destructive confirmation.
- [x] Implement UI over download services only.
- [x] Run `./gradlew :feature:catalog:testDebugUnitTest :feature:reader:connectedDebugAndroidTest lintDebug --stacktrace`.
- [x] Commit `downloads: add offline content controls`.

### Task 6: Reconcile database and filesystem state

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/reconcile/StorageReconciliationService.kt`, `StorageReconciliationPlan.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/reconcile/StorageReconciliationServiceTest.kt`
- Create: `storage/files/src/main/kotlin/app/openstory/storage/files/FileBlobInventory.kt`
- Test: `storage/files/src/test/kotlin/app/openstory/storage/files/FileBlobInventoryTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/storage/LowStorageBehaviorTest.kt`

- [x] Write RED tests for orphan files, missing files, interrupted temp files, stale metadata, low-space refusal, and protected downloads.
- [x] Implement bounded recoverable reconciliation with no broad recursive deletion.
- [x] Run `./gradlew :downloads:test :storage:files:test :storage:room:connectedDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace` and `./scripts/structural-review-report.sh`.
- [x] Commit `downloads: reconcile offline storage`.

## Wave Checkpoint

- [x] Exact exit graph and schema 6 pass.
- [x] Interrupted writes never replace valid content.
- [x] Cache eviction cannot delete explicit downloads.
- [x] Reader works offline and falls back safely, including first-open completed downloads without progress metadata.
- [x] `./scripts/verify.sh` and the Wave 09 device checkpoint pass.
- [x] Deep ownership review confirms policy, Room metadata, filesystem bytes, Reader port, UI, and workers remain separated.

Checkpoint evidence (2026-08-11): full verification, lint, Detekt, architecture, schema stability, and structural hard policies passed. Device coverage passed on API 26 for Catalog, Reader, Room, and app tests; Room and the Wave 09 low-storage app contract also passed on API 37. The unrelated API 37 JavaScript sandbox contract remains outside this wave's storage checkpoint.
