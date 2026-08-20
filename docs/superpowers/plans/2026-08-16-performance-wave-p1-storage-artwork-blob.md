# Performance Wave P1 Storage + Artwork + Blob Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Main-thread chapter file I/O, global blob-lock contention, original-size artwork decoding, and redundant full-payload blob allocation without changing user-visible behavior.

**Architecture:** Concrete filesystem adapters own an injected I/O dispatcher and coordinate mutations through a shared per-file coroutine mutex registry. `ChapterBlob` keeps defensive public ownership while adding zero-copy size/input-stream operations and slice verification for trusted storage/codec paths. `HikariArtworkState` carries Coil's constraints-aware resolver so the request and rendered image share the same measured size contract.

**Tech Stack:** Kotlin 2.4.10, kotlinx.coroutines, Android app-private files, Coil 3 Compose, Jetpack Compose, kotlin.test/coroutines-test.


## Execution Status — 2026-08-16

- Production implementation and regression/static tests are present for all P1 scope items.
- `performance-wave-p1-policy-test.sh`, lifecycle/Wave 4 policies, package boundaries, source layout, Room schema stability, structural suppressions, and current architecture are GREEN in the sandbox.
- Standalone Kotlin/JVM runtime harnesses using the actual production storage/blob/plugin source are GREEN for read/write integrity, injected dispatcher execution, and unrelated-key parallelism.
- Gradle unit/screenshot execution is **blocked** because the sandbox cannot resolve `services.gradle.org` to download Gradle 9.5.0. Those Gradle steps remain developer-machine verification gates.

---

## Global Constraints

- Preserve all Wave 1-4 behavior and UI output.
- No Room schema migration.
- Concrete blocking storage adapters own their execution context; Reader/UI callers stay dispatcher-agnostic.
- Same-key read/write/delete operations remain race-safe.
- A corrupt read must not delete a replacement committed after validation began.
- `ChapterBlob.bytes()` remains defensive.
- No catalog matching, Room-query, plugin-runtime, chapter-pagination, or cache-eviction P2 work.

---

### Task 1: Off-Main chapter file I/O with per-file coroutine coordination

**Files:**
- Modify: `storage/files/src/main/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStore.kt`
- Modify: `storage/files/src/main/kotlin/app/openstory/storage/files/FileBlobInventory.kt`
- Create: `storage/files/src/main/kotlin/app/openstory/storage/files/ChapterBlobFileLocks.kt`
- Modify: `storage/files/src/test/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStoreTest.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/TransactionalPluginPackageStorage.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/TransactionalPluginPackageStorageTest.kt`

**Interfaces:**
- Produces: `internal object ChapterBlobFileLocks { suspend fun <T> withLock(file: File, block: suspend () -> T): T }`.
- `AtomicFileChapterBlobStore` gains an internal constructor parameter `ioDispatcher: CoroutineDispatcher`, defaulted by the Android constructor to `Dispatchers.IO`.
- `FileBlobInventory` gains the same injected dispatcher in its internal constructor, while the Android constructor uses `Dispatchers.IO`.
- `TransactionalPluginPackageStorage` gains an injected `CoroutineDispatcher = Dispatchers.IO` and wraps store/read/remove filesystem work with `withContext(ioDispatcher)`.

- [x] **Step 1: Add failing dispatcher/parallelism tests**

Add tests backed by named single-thread executors that assert blob file callbacks and plugin package staging run on their injected I/O dispatcher. Add a second blob test that blocks key A inside its first output chunk, starts key B, and asserts key B reaches its write before key A is released.

- [ ] **Step 2: Run focused storage tests and confirm RED** — BLOCKED in sandbox by Gradle wrapper DNS; RED was independently observed through the P1 static policy before implementation.

Run:

```bash
./gradlew :storage:files:testDebugUnitTest --tests 'app.openstory.storage.files.AtomicFileChapterBlobStoreTest' --tests 'app.openstory.storage.files.FileBlobInventoryTest'
```

Expected before implementation: dispatcher test records caller thread and unrelated-key concurrency test times out because the global monitor serializes both writes.

- [x] **Step 3: Implement keyed coroutine lock registry**

Create a registry keyed by `file.toPath().toAbsolutePath().normalize().toString()`. The registry uses a tiny `synchronized(entries)` section only to increment/decrement an entry reference count; the actual operation is guarded by `Mutex.withLock`. Waiters count as users before suspension so an entry is removed only after no holder/waiter remains.

- [x] **Step 4: Move store filesystem operations under `withContext(ioDispatcher)`**

Each store operation computes its target, acquires the per-file mutex, then executes `readBytes`, temp creation, write/fsync/move, and delete in `withContext(ioDispatcher)`. Remove `ChapterBlobFileLayout.operationLock` usage entirely from the store. Keep temp cleanup in `finally`.

- [x] **Step 5: Move inventory scan/delete to I/O and coordinate committed blob deletion by file path**

`scan()` runs traversal in `withContext(ioDispatcher)` without a global monitor. `delete()` runs artifact resolution on I/O; committed `.blob` deletion acquires `ChapterBlobFileLocks.withLock(file)` before filesystem deletion, while unique stale temp files delete directly. Remove `operationLock` from `ChapterBlobFileLayout`.

- [ ] **Step 6: Run focused storage tests to GREEN** — BLOCKED in sandbox by Gradle wrapper DNS; standalone Kotlin/JVM runtime harness is GREEN.

Run the Task 1 Gradle command again. Existing same-key corruption/replacement test must still pass, and new dispatcher/parallelism tests must pass.

- [ ] **Step 7: Commit Task 1**

```bash
git add storage/files
git commit -m "perf: isolate chapter blob file io"
```

---

### Task 2: Reduce `ChapterBlob` hashing and full-payload copies

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/blob/BlobModels.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/cache/CacheService.kt`
- Modify: `storage/files/src/main/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStore.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/blob/ChapterBlobStoreContractTest.kt`
- Modify: `storage/files/src/test/kotlin/app/openstory/storage/files/AtomicFileChapterBlobStoreTest.kt`

**Interfaces:**
- `ChapterBlob` adds `val sizeBytes: Int`.
- `ChapterBlob` adds `fun inputStream(): InputStream` backed by owned content without copying.
- `ChapterBlob.verified(bytes, offset, length, checksum)` verifies a source slice and copies that slice exactly once into blob ownership.

- [x] **Step 1: Add failing blob ownership/slice/size tests**

Add tests that mutate the source array after `fromBytes` and after slice `verified` and assert `blob.bytes()` is unchanged; mutate the array returned by `bytes()` and assert a second `bytes()` is unchanged; assert `sizeBytes` equals the payload length; verify slice factory accepts the payload section of a header+payload array and rejects a checksum mismatch.

- [ ] **Step 2: Run focused blob tests and confirm RED** — BLOCKED in sandbox by Gradle wrapper DNS; new test sources were compiled independently with `kotlinc`.

Run:

```bash
./gradlew :downloads:testDebugUnitTest --tests 'app.openstory.downloads.blob.ChapterBlobStoreContractTest'
```

Expected before implementation: missing `sizeBytes`, `inputStream`, and slice-verification API causes test compile failure.

- [x] **Step 3: Implement one-hash/one-copy ownership and allocation-light checksum hex**

`fromBytes` copies once into `ChapterBlob` ownership then computes SHA-256 once over the owned bytes; `verified(bytes, checksum)` likewise owns one copy and validates that owned content once. A private digest helper supports `(bytes, offset, length)` with `MessageDigest.update(bytes, offset, length)`. Convert digest bytes with a fixed `charArrayOf('0'..'f')` lookup instead of per-byte `String.format`.

- [x] **Step 4: Replace copy-for-size and copy-for-decode call sites**

Use `blob.sizeBytes.toLong()` in write admission and cache metadata. Replace `ByteArrayInputStream(blob.bytes())` with `blob.inputStream()` in `ReaderDocumentBlobCodec.decode`.

- [x] **Step 5: Stream atomic-file output and verify encoded payload slice without intermediate arrays**

Extend `BlobFileOutput` with `writeBlob(blob)`; the platform implementation reads `ChapterBlob.inputStream()` and `copyTo(stream)` so the owned array is never exposed and no full defensive payload copy is created. Store writes checksum bytes, one newline byte, then streams blob content rather than building `checksum + newline + payload`. Decode checksum with `decodeToString(0, 64)` and call slice `ChapterBlob.verified(encoded, 65, encoded.size - 65, checksum)` so payload ownership is created with one copy.

- [ ] **Step 6: Run downloads + storage focused tests to GREEN** — BLOCKED in sandbox by Gradle wrapper DNS; production/runtime harness and test-source compile checks are GREEN.

Run:

```bash
./gradlew :downloads:testDebugUnitTest :storage:files:testDebugUnitTest --tests 'app.openstory.downloads.blob.ChapterBlobStoreContractTest' --tests 'app.openstory.storage.files.AtomicFileChapterBlobStoreTest'
```

- [ ] **Step 7: Commit Task 2**

```bash
git add downloads storage/files
git commit -m "perf: reduce chapter blob allocations"
```

---

### Task 3: Make shared artwork requests constraints-aware

**Files:**
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtwork.kt`
- Modify: `core/designsystem/src/test/kotlin/app/openstory/designsystem/artwork/HikariArtworkScreenshotTest.kt` only if public state construction requires adaptation
- Create or modify static performance policy test under `scripts/tests/` to guard resolver wiring

**Interfaces:**
- `HikariArtworkState` carries the remembered Coil constraints size resolver internally.
- `rememberHikariArtwork` uses `rememberConstraintsSizeResolver()` and sets it on `ImageRequest.Builder.size(...)`.
- `ArtworkLayer` applies the same resolver modifier to the `Image` before/with `matchParentSize()` according to Coil Compose API requirements.

- [x] **Step 1: Add RED static contract for artwork sizing**

Extend the performance policy test to require `rememberConstraintsSizeResolver`, `.size(sizeResolver)`, and application of `sizeResolver` to the rendered image modifier in `HikariArtwork.kt`.

- [x] **Step 2: Run policy and confirm RED**

Run the dedicated performance policy script. Expected: it fails because current artwork uses unconstrained `rememberAsyncImagePainter`.

- [x] **Step 3: Implement constraints-aware resolver without changing public artwork behavior**

Remember one resolver per artwork composable invocation, include it in `HikariArtworkState`, set it on the request, and apply it to the `Image` modifier. Preserve `ContentScale.Crop`, fallback gradients/monogram, crossfade motion policy, memory/disk cache keys, and semantic behavior.

- [ ] **Step 4: Run design-system unit/screenshot tests and policy to GREEN** — static P1/Wave 4 policies are GREEN; Gradle unit/screenshot execution is blocked by wrapper DNS.

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest
bash scripts/tests/performance-wave-4-policy-test.sh
```

If a new P1 policy file is added, run that explicitly too.

- [ ] **Step 5: Commit Task 3**

```bash
git add core/designsystem scripts/tests
git commit -m "perf: size artwork requests to layout"
```

---

### Task 4: P1 guardrail, docs, and continuation patch

**Files:**
- Create: `scripts/tests/performance-wave-p1-policy-test.sh`
- Modify: `scripts/verify.sh` only if existing policy discovery requires explicit registration
- Create: `docs/internal/checkpoints/performance-wave-p1.md`
- Modify: this plan to mark completed steps

**Interfaces:**
- Static policy fails if `operationLock`/blocking `synchronized` returns to chapter blob storage, if filesystem suspend adapters lose explicit I/O dispatching, if `HikariArtwork` loses constraints sizing, or if production size checks regress to `blob.bytes().size`.

- [x] **Step 1: Add policy assertions for all P1 invariants**

Use repository shell-test conventions and exact file checks for `withContext(ioDispatcher)`, `ChapterBlobFileLocks.withLock`, absence of `operationLock`, `rememberConstraintsSizeResolver`, `.size(sizeResolver)`, `sizeBytes`, and absence of known production `blob.bytes().size` call sites.

- [x] **Step 2: Run all runnable static gates**

Run:

```bash
bash scripts/tests/performance-wave-p1-policy-test.sh
bash scripts/tests/performance-lifecycle-policy-test.sh
bash scripts/tests/performance-wave-4-policy-test.sh
bash scripts/tests/package-boundary-policy-test.sh
bash scripts/tests/source-layout-test.sh
bash scripts/tests/room-schema-stability-test.sh
```

Use the exact script names present in the repository if one differs.

- [ ] **Step 3: Run focused Gradle verification when wrapper/dependencies are available** — PENDING on a networked developer machine.

Run:

```bash
./gradlew :downloads:testDebugUnitTest :storage:files:testDebugUnitTest :core:designsystem:testDebugUnitTest --stacktrace
```

Record an exact network/toolchain blocker instead of claiming success if Gradle cannot resolve.

- [x] **Step 4: Write checkpoint documentation**

Document changed invariants, focused test commands, benchmark follow-up, and explicitly state that catalog/Room/plugin/pagination/cache-eviction work remains P2.

- [x] **Step 5: Run diff and patch integrity gates**

Run `git diff --check` against the baseline and generate a patch from baseline commit `0ca1dae`. Apply it with `git apply --check` to a pristine extraction of the supplied ZIP.

- [x] **Step 6: Export continuation patch**

Write `/mnt/data/Hikari-performance-wave-p1.patch` containing all implementation, tests, policy, spec/plan/checkpoint documentation changes required to move the supplied baseline to the verified P1 state.
