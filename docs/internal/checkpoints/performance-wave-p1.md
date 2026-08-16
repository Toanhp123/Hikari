# Performance Wave P1 Checkpoint

Date: 2026-08-16
Status: **IMPLEMENTED; STATIC + STANDALONE RUNTIME VERIFIED; GRADLE VERIFICATION PENDING**

## Scope

Performance Wave P1 addresses the highest-risk runtime findings from the full performance audit without taking on the broader P2 algorithm/query work.

Implemented boundaries:

- `AtomicFileChapterBlobStore` owns an injected I/O dispatcher. Blocking read/write/fsync/move/delete work no longer inherits a Reader/ViewModel caller dispatcher.
- `FileBlobInventory` runs scan/delete filesystem work on its I/O dispatcher.
- `TransactionalPluginPackageStorage` runs package store/read/remove filesystem work on an injected I/O dispatcher with `Dispatchers.IO` as the production default.
- The previous global chapter-blob JVM monitor is removed. `ChapterBlobFileLocks` coordinates committed blob operations by normalized file path with coroutine `Mutex` instances and waiter-aware reference cleanup, so unrelated keys can proceed concurrently while same-key read/write/delete remains serialized.
- `ChapterBlob` now exposes `sizeBytes` and a read-only `inputStream()`. Normal construction hashes once and owns one payload copy; verified encoded slices create one payload ownership copy. SHA-256 hex encoding no longer uses per-byte `String.format`.
- Atomic blob writes stream checksum/newline/payload instead of allocating one additional full encoded array. Reads validate the checksum header without a checksum `copyOfRange` and create one payload ownership copy.
- Reader cache admission/metadata use `sizeBytes`; Reader document decode consumes `inputStream()` instead of copying the whole blob first.
- `HikariArtwork` uses Coil 3.5's `rememberConstraintsSizeResolver()`, feeds it to `ImageRequest.size(...)`, and attaches the same resolver to the rendered `Image` modifier. Existing fallback, cache-key, crossfade, painter-state and shared artwork API behavior is retained.
- `scripts/tests/performance-wave-p1-policy-test.sh` guards these invariants and is automatically picked up by the repository static-gate glob.

## Explicitly deferred to P2

- catalog matcher/candidate indexing;
- Room full-table/N+1 projections;
- plugin provision/read/manifest/script caching beyond filesystem dispatcher ownership;
- chapter pagination snapshot/reaggregation;
- cache quota full-scan/sort policy.

## Verification completed in sandbox

GREEN:

```text
scripts/tests/performance-wave-p1-policy-test.sh
scripts/tests/performance-lifecycle-policy-test.sh
scripts/tests/performance-wave-4-policy-test.sh
scripts/verify-package-boundaries.sh
scripts/verify-source-layout.sh
scripts/verify-room-schema-stability.sh
scripts/verify-structural-suppressions.sh
scripts/verify-current-architecture.sh
git diff --check
```

The two instrumentation-script contract tests also pass after temporarily restoring executable bits stripped by the uploaded ZIP.

Because the Gradle wrapper distribution is not cached and the sandbox cannot resolve `services.gradle.org`, focused Gradle tests cannot run here. The exact blocker is `java.net.UnknownHostException: services.gradle.org` while downloading `gradle-9.5.0-bin.zip`.

As an offline runtime substitute, standalone Kotlin/JVM harnesses compiled the actual P1 production source with the bundled `kotlinx-coroutines-core` and verified:

- `ChapterBlob` ownership/size/slice integrity;
- platform atomic file write/read integrity;
- all injected blob file callbacks execute on the configured I/O thread;
- a blocked write for blob A does not prevent blob B from starting;
- plugin package store/read/remove works and package staging enters through the configured I/O thread.

These harnesses are verification-only and are not part of the patch.

The new storage/blob and plugin-package test sources were also compiled with `kotlinc` against minimal verification-only stubs to catch Kotlin syntax/type errors while Gradle was unavailable.

Patch integrity was verified by applying the exported baseline-to-P1 diff to a pristine extraction of the supplied ZIP with `git apply --check`, applying it, and rerunning the P1/lifecycle/Wave 4 performance policies successfully.

## Developer-machine verification

Run after applying the patch:

```bash
./gradlew \
  :downloads:testDebugUnitTest \
  :storage:files:testDebugUnitTest \
  :plugins:runtime:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  --stacktrace

./gradlew compareRoborazziDebug --stacktrace
./scripts/verify.sh
```

After those gates are green, rerun the existing Macrobenchmark/Reader journeys on a physical device to measure the before/after effect of P1. P1 intentionally does not change benchmark CUJ definitions.
