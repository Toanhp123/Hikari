# Hikari Performance Wave 0 — Contracts and Fixtures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish representative whole-app performance fixtures and deterministic scaling contracts before structural production changes land.

**Architecture:** Extend the existing `benchmarkRelease` fixture rather than creating a second benchmark app. Add parameterized aged-state/image/Chapter fixtures plus deterministic plugin control-plane/security counters and focused query-count/slope tests so later waves can prove that unrelated historical dimensions, executable/package width, and stable failure states no longer dominate bounded operations.

**Tech Stack:** Android Macrobenchmark, benchmarkRelease fixture activity, Kotlin/JUnit, Room androidTest, existing diagnostics/test fakes.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Wave 0 does not change production performance behavior.
- Keep benchmark data deterministic and network-independent unless a scenario explicitly measures a local fake transport.
- Do not turn one giant fixture into the only benchmark; each dimension must be independently selectable.
- Preserve existing baseline-profile scenarios and tags.

---

### Task 1: Parameterize the benchmark fixture profile

**Files:**
- Create: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureProfile.kt`
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- Create: `app/src/test/kotlin/app/openstory/benchmark/BenchmarkFixtureProfileTest.kt`

**Interfaces:**
- Produces `BenchmarkFixtureProfile(catalogStories, progressRows, redirectRows, libraryEntries, explicitDownloadRecords, readerImagePages, readerAssetMetadataRows, automaticCacheRows, chapterCount, chapterPageSize, metadataWidth)`.
- Produces intent extras/codec used only by benchmarkRelease and benchmark module.

- [ ] **Step 1: Write profile validation tests** covering default values, supported scale presets, and rejection of negative/unbounded values.
- [ ] **Step 2: Run** `./gradlew :app:testBenchmarkReleaseUnitTest --tests '*BenchmarkFixtureProfileTest*' --no-daemon` and verify RED because the profile does not exist.
- [ ] **Step 3: Implement an immutable profile and named presets** `SMALL`, `MEDIUM`, `AGED`, `STRESS` with explicit fields; do not infer one dimension from another.
- [ ] **Step 4: Extend `prepareBenchmarkFixture(profile)`** so the driver passes the profile to `BenchmarkFixtureActivity` via extras while the no-arg overload preserves existing tests.
- [ ] **Step 5: Refactor `seedFixture()`** into focused private seed functions keyed by profile fields, including independent Library membership and explicit-download history seeders; keep all existing default fixture values identical.
- [ ] **Step 6: Run existing Macrobenchmark compile + profile unit tests** and verify no existing benchmark scenario changes tags/launch behavior.
- [ ] **Step 7: Self-review** that profile parsing cannot accidentally run stress-scale seeding in normal app builds; all code must stay under `benchmarkRelease`/`:benchmark`.
- [ ] **Step 8: Commit** `test(perf): parameterize whole-app benchmark fixtures`.

---

### Task 2: Add a real Search execution benchmark and aged catalog fixture

**Files:**
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`

**Interfaces:**
- Consumes `BenchmarkFixtureProfile.catalogStories` and `metadataWidth`.
- Produces a Search benchmark that enters a deterministic query and waits for a result-state tag, rather than only reopening Search.

- [ ] **Step 1: Add a focused Search contract test** proving the benchmark provider/query returns deterministic local results without external network.
- [ ] **Step 2: Run the focused Catalog test** and verify the new benchmark-specific local provider/fake contract is RED until fixture wiring exists.
- [ ] **Step 3: Seed unrelated historical catalog rows** separately from the rows matching the benchmark query; support narrow/medium/wide metadata payloads.
- [ ] **Step 4: Add driver helpers** to enter the deterministic benchmark query, wait for `search-progress` to disappear, then wait for the seeded benchmark result title before ending the measured section; do not add a release-path test tag.
- [ ] **Step 5: Add `searchQueryAgedCatalog()`** Macrobenchmark with the same query under at least SMALL and AGED fixture profiles in separate benchmark methods so results remain comparable.
- [ ] **Step 6: Run benchmark module compilation**: `./gradlew :benchmark:assemble --no-daemon`.
- [ ] **Step 7: Record the pre-change baseline** on the target device for later Wave-2 comparison; store numbers in the Wave-0 checkpoint, not as hard-coded pass/fail milliseconds.
- [ ] **Step 8: Commit** `test(perf): benchmark real search on aged catalog`.

---

### Task 3: Add Reader image/cache fixture that exercises the real asset pipeline

**Files:**
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Create: `app/src/benchmarkRelease/res/drawable-nodpi/benchmark_reader_page.png`
- Test: `app/src/test/kotlin/app/openstory/reader/assets/ReaderAssetIntegrationTest.kt`

**Interfaces:**
- Produces a benchmark Reader document containing `ReaderBlock.ImagePage` blocks and deterministic local delivery locators.
- Seeds `RA`/`AC` metadata independently of visible image-page count.

- [ ] **Step 1: Add an integration regression test** proving the benchmark Reader image source traverses `ReaderAssetCoordinator`/delivery/cache rather than bypassing the asset subsystem.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ReaderAssetIntegrationTest*' --no-daemon` and verify the new fixture case is RED.
- [ ] **Step 3: Add deterministic benchmark image pages** with stable asset IDs and a local benchmark delivery source compatible with production Reader asset contracts; do not weaken HTTPS rules in production code.
- [ ] **Step 4: Seed unrelated Reader-asset and automatic-cache metadata** according to `readerAssetMetadataRows` and `automaticCacheRows` without forcing all blobs into memory at setup time.
- [ ] **Step 5: Add `readerImageScrollColdCache` and `readerImageScrollWarmCache` using `FrameTimingMetric`, plus `readerImageMemoryWarmCache` using `MemoryUsageMetric(MemoryUsageMetric.Mode.Max)`.**
- [ ] **Step 6: Verify the old `readerScrollLongChapter` remains text-only** and is retained as a separate rendering benchmark rather than silently changing its meaning.
- [ ] **Step 7: Run Reader/app tests and benchmark assembly**.
- [ ] **Step 8: Commit** `test(perf): add reader image cache macrobenchmarks`.

---

### Task 4: Add Chapter and background scheduling scaling fixtures

**Files:**
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt`
- Create: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizerTest.kt`
- Test: `app/src/test/kotlin/app/openstory/work/PeriodicChapterDispatchTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterSyncCandidateSourceTest.kt`

**Interfaces:**
- Produces parameterized `J/Z/Pg/Lb` fixtures.
- Adds deterministic counters/timing-independent assertions for repeated full-graph reads and candidate-source calls where possible.

- [ ] **Step 1: Add a multi-page synchronizer test fixture** with enough releases/pages to expose cumulative-prefix work while asserting identical final graph semantics.
- [ ] **Step 2: Add a periodic-dispatch test fixture** with more than one batch (`> 20`) and instrument the candidate source call count/returned candidate count per continuation.
- [ ] **Step 3: Add Room test data builders** for hundreds/thousands of schedule candidates without changing production code.
- [ ] **Step 4: Run focused Chapter/WorkManager/Room tests** and record current query/candidate behavior as the pre-Wave-6 contract baseline.
- [ ] **Step 5: Commit** `test(perf): add chapter and periodic scaling fixtures`.

---

### Task 5: Add retained-navigation and query-plan risk diagnostics

**Files:**
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Create: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkReactiveWorkDiagnostics.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/reader/ReadingProgressQueryPlanTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/readerassets/ReaderAssetQueryPlanTest.kt`

**Interfaces:**
- Produces benchmark-only counters for Room/reducer work by destination when feasible without changing release behavior.
- Produces `EXPLAIN QUERY PLAN` assertions for the known global scans/sorts that later waves target.

- [ ] **Step 1: Add benchmark-only diagnostic counters** around repository/query/reducer boundaries through existing DI test/benchmark hooks; never add release logging in hot paths.
- [ ] **Step 2: Add a retained-tab scenario**: visit Home→Discover→Library, return Home, mutate an unrelated table in the benchmark fixture, then capture inactive-destination work counters and warm-navigation frames.
- [ ] **Step 3: Add query-plan tests** proving current `reading_progress.observeAll()` requires global scan/sort and Reader asset usage sum is a global aggregate; these are characterization tests, not permanent expected bad behavior after later waves.
- [ ] **Step 4: Document the exact promotion threshold for `RISK-LIFECYCLE`** in the checkpoint: material repeated inactive Room/reducer work must be measurable while the destination is hidden.
- [ ] **Step 5: Run all Wave-0 focused tests plus** `bash scripts/verify-package-boundaries.sh` and `bash scripts/verify-structural-suppressions.sh`.
- [ ] **Step 6: Create** `docs/superpowers/checkpoints/2026-09-07-hikari-perf-wave-0-baseline.md` with device, fixture dimensions, benchmark medians/tails, and risk observations.
- [ ] **Step 7: Commit** `test(perf): freeze whole-app performance baselines`.

---

### Task 6: Add plugin control-plane, failure-state and secure-session characterization fixtures

**Files:**
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/PluginPerformanceFixture.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntimePerformanceTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisionerTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionManagedCredentialProviderTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionServiceSecurityGenerationTest.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStoreTest.kt`

**Interfaces:**
- Produces deterministic counters for `Pl`, aggregate `JsB`, bundled `Bp`, terminal/retryable provisioning state, `Cr`, and `Hr` without depending on external network or JavaScript isolate timing.
- Characterizes current operation counts; it does not yet change production runtime behavior.

- [ ] **Step 1: Add `PluginPerformanceFixture`** with independently selectable installed-plugin count, per-plugin manifest/script width, bundled package width/state, credential-record count, and repeated public-call/request-build count.
- [ ] **Step 2: Characterize `enabled(operation)`** and record manifest reads, `main.js` reads/bytes and active-package cache behavior for `Pl = 1/4/16` and small/large `JsB`.
- [ ] **Step 3: Characterize bundled provisioning** for current/newer/missing/update-needed/`NEEDS_REVIEW`/terminal-invalid/transient-failure states across repeated `ensureProvisioned()`/runtime entries. Count source/package reads, verifier/update calls and retry passes.
- [ ] **Step 4: Characterize authenticated request construction** with fake policy/session stores for `Cr = 0/1/4`, `Hr = 1/10/100`, and unrelated `Pl`. Record policy enumeration, session-store read count and summary refresh count.
- [ ] **Step 5: Add Android Keystore instrumentation timing/counter scaffolding** sufficient for Wave 4 to compare one-vs-repeated key acquisition without exposing secrets or changing production crypto semantics.
- [ ] **Step 6: Run** `./gradlew :plugins:runtime:testDebugUnitTest :app:testDebugUnitTest --no-daemon` plus targeted Keystore instrumentation and record the pre-Wave-4 counts in the Wave-0 checkpoint.
- [ ] **Step 7: Self-review** that isolate execution time is not mixed into control-plane/session counters; `RISK-PLUGIN-ISOLATE` must remain independently measurable after X16–X18 repairs.
- [ ] **Step 8: Commit** `test(perf): characterize plugin control-plane and auth costs`.

---

### Task 7: Add Story-screen bounded-history scaling fixture

**Files:**
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/download/DownloadViewModelTest.kt`

**Interfaces:**
- Consumes `BenchmarkFixtureProfile.libraryEntries`, `explicitDownloadRecords`, and `progressRows` independently while holding one target Story and its Chapter/release set constant.
- Produces a deterministic Story navigation scenario and benchmark-only reactive-work counters for Library membership, progress, and download-status sources.

- [ ] **Step 1: Seed one target Story** with a fixed Chapter/release set plus independently scalable unrelated Library entries, progress rows, and explicit download records.
- [ ] **Step 2: Add characterization tests** proving current Story ViewModel/Download ViewModel behavior observes global histories before Wave 7; assertions must describe source-request scope/counters rather than hard-coded latency.
- [ ] **Step 3: Add `storyBoundedDemandAgedHistory()`** Macrobenchmark/diagnostic scenario that opens the target Story, opens Chapters, waits for stable content, and records query/reducer counters at SMALL versus AGED `L/D/G` dimensions.
- [ ] **Step 4: Record the pre-Wave-7 slope** in the Wave-0 checkpoint; target Story/release cardinality must be identical across compared profiles.
- [ ] **Step 5: Run benchmark assembly plus Story/Download ViewModel tests**.
- [ ] **Step 6: Commit** `test(perf): characterize story bounded-demand aging`.

---

## Wave 0 acceptance gate

- [ ] Real Search query benchmark exists and varies historical `N/B` independently.
- [ ] Reader image benchmark exercises asset/cache path and varies `RA/AC/Y`.
- [ ] Chapter/page/background fixtures exercise `J/Z/Pg/Lb`.
- [ ] Plugin fixtures independently characterize `Pl/JsB/Bp/Cr/Hr` plus terminal-vs-retryable failure state without isolate-timing contamination.
- [ ] Story bounded-demand fixture independently varies `L/D/G` while target Story/current release set stays fixed.
- [ ] Retained-navigation reactive-work diagnostics can decide `RISK-LIFECYCLE` later.
- [ ] Existing benchmark semantics are preserved rather than silently repurposed.
- [ ] No production behavior optimization has been merged in this wave.
