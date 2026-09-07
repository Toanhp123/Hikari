# Hikari Performance Wave 7 — UI Demand and Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make UI surfaces subscribe only to bounded data required by current presentation semantics and move nontrivial pure projection CPU off Main, while preserving retained-navigation warm state.

**Architecture:** Consumers derive semantic IDs first, switch bounded repository flows with stable/deduplicated ID sets, activate optional dependencies only when `LibraryLocalDependencyPolicy` requires them, add query-specific Story/Download aggregates where IDs cannot be derived cheaply, and run scalable pure reducers on `AppDispatchers.default`. Retained-tab lifecycle changes remain benchmark-gated.

**Tech Stack:** Compose ViewModels, Coroutines/Flow, `AppDispatchers`, bounded Room repositories from Waves 1/4/5.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Do not use `Dispatchers.Default` to hide a global data-scope problem; bounded data access must exist first.
- `distinctUntilChanged()` belongs before expensive `flatMapLatest` subscription switching when the semantic ID set is unchanged.
- Preserve `ContentState`/retained-value semantics from CSC-v1.
- Preserve P6 retained composition and navigation stacks unless the lifecycle risk gate is promoted.

---

### Task 1: Add one-Story Library membership and progress observations

**Files:**
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryRepository.kt`
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryService.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/RoomLibraryRepository.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepository.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/library/RoomLibraryRepositoryTest.kt`
- Test: `library/src/test/kotlin/app/openstory/library/LibraryServiceTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepositoryTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`

**Interfaces:**

```kotlin
fun LibraryRepository.observe(storyId: StoryId): Flow<LibraryEntry?>
fun ReadingProgressRepository.observeForStory(storyId: StoryId): Flow<List<ReadingProgress>>
```

`LibraryService` exposes the point membership Flow. The progress side may instead expose a narrower `observeLatestResumeTarget(storyId)` if the repository/domain already has a stable resume model.

- [ ] **Step 1: Add StoryViewModel regression** with large unrelated Library + progress histories and assert both sources request only the resolved target Story.
- [ ] **Step 2: Add `LibraryDao.observe(storyId)` point Flow + repository/service API**. Resolve the requested Story consistently with existing Library write semantics; do not introduce a second canonicalization rule.
- [ ] **Step 3: Add Room bounded query** for resolved Story progress; use Wave-1 identity resolution rather than global progress filtering.
- [ ] **Step 4: Switch StoryViewModel** from `library.observe().map { firstOrNull(...) }` and `progress.observeAll().map { latestResumeTarget(...) }` to the point/bounded APIs.
- [ ] **Step 5: Preserve Library status, resume ordering/completion/content-fingerprint and retained-observation failure semantics exactly**.
- [ ] **Step 6: Run Library service/Room + progress + Story ViewModel tests**.
- [ ] **Step 7: Commit** `perf(ui): observe story membership and progress by story`.

---

### Task 2: Make Library dependency policy control subscription demand

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryDependencyPolicy.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryDependencyPolicyTest.kt`

**Interfaces:**
- Membership observation yields the bounded Story-ID set first.
- Catalog projections use `observeForStories(ids)`.
- mappings/progress flows are subscribed only when the current controls require them.

- [ ] **Step 1: Add fake-repository subscription-count tests** for default view, search query active, mapping/source filter active, progress-dependent sort active, and controls returning to a state that no longer requires a dependency.
- [ ] **Step 2: Derive stable `Set<StoryId>` from membership** and apply `distinctUntilChanged()` before switching bounded projection flows.
- [ ] **Step 3: Replace `catalog.observe()` with `catalog.observeForStories(ids)`**.
- [ ] **Step 4: Express optional dependency flows** as `flatMapLatest { required -> if (required) repository.observeForStories(ids) else flowOf(empty...) }` using existing policy decisions; do not fake readiness by retaining a live global source.
- [ ] **Step 5: Preserve retained-value-on-failure behavior** for a dependency that was previously loaded, while stopping future upstream demand when semantically unnecessary.
- [ ] **Step 6: Run full Library ViewModel/ContentState/screenshot tests**.
- [ ] **Step 7: Commit** `perf(library): bind reactive demand to presentation policy`.

---

### Task 3: Bound download-related UI reads to explicit/current release IDs

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadService.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadViewModel.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/StorySectionDependencies.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepositoryTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/download/DownloadViewModelTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterRepositoryTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModelTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt`

**Interfaces:**

```kotlin
data class DownloadChapterProjection(
    val releaseId: ChapterReleaseId,
    val storyId: StoryId,
    val chapterLabel: String,
    val releaseLabel: String,
    val pluginId: PluginId,
)

fun observeDownloadProjection(
    releaseIds: Set<ChapterReleaseId>,
): Flow<List<DownloadChapterProjection>>

fun DownloadRepository.observeForReleaseIds(
    releaseIds: Set<ChapterReleaseId>,
): Flow<List<DownloadRecord>>
```

`DownloadService` forwards the bounded release-status API. `DownloadViewModel` exposes status observation for a caller-provided stable release-ID set instead of one global `statuses` Flow.

- [ ] **Step 1: Add standalone DownloadsViewModel test** with small explicit download set + many unrelated Chapter groups; assert enrichment requests are bounded to explicit downloaded release IDs.
- [ ] **Step 2: Add Story `DownloadViewModel` regression** with a small current release set + thousands of unrelated download records; assert status observation requests only the current IDs and emits the same state map for them.
- [ ] **Step 3: Add chunked Room `observeForReleaseIds` query** over explicit download records, with empty-set and >bind-limit tests; avoid combining one point Flow per release.
- [ ] **Step 4: Derive Story chapter release IDs** from the current `ChapterListContent.releaseTargets` in `StorySectionDependencies`, stabilize the set, and collect `DownloadViewModel` statuses only for those IDs. Remove the global `downloadViewModel.statuses` subscription.
- [ ] **Step 5: Add Room bounded Chapter projection** that maps explicit downloaded release IDs to canonical Chapter/Story context in chunks; avoid `chapters.observeAll()` + local release map.
- [ ] **Step 6: Derive Story IDs from the bounded Chapter result** and request `CatalogStoryProjectionRepository.observeForStories(storyIds)`.
- [ ] **Step 7: Remove global Chapter/catalog subscriptions** from standalone Downloads ViewModel while preserving grouping/sorting/state semantics.
- [ ] **Step 8: Run Download repository/ViewModels, Room Chapter and navigation tests**.
- [ ] **Step 9: Commit** `perf(downloads): bound download ui release demand`.

---

### Task 4: Replace Settings list observation with aggregate storage summary APIs

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/storage/StorageUsageRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/storageusage/RoomStorageUsageRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/readerassets/ReaderAssetDao.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/settings/AppStorageSummaryAdapter.kt`
- Test: `app/src/test/kotlin/app/openstory/settings/AppStorageSummaryAdapterTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/storageusage/RoomStorageUsageRepositoryTest.kt`

**Interfaces:**
```kotlin
data class StorageUsageSnapshot(
    val explicitDownloadBytes: Long,
    val automaticChapterCacheBytes: Long,
    val readerAssetBytes: Long,
)

interface StorageUsageRepository {
    fun observe(): Flow<StorageUsageSnapshot>
}
```

`RoomStorageUsageRepository` combines three aggregate SQL Flows over the Wave-5 separated tables. `AppStorageSummaryAdapter` depends on this narrow read model + Settings, computes `automaticCacheBytes = automaticChapterCacheBytes + readerAssetBytes`, and removes its presentation dependency on `AutomaticCacheBudgetCoordinator.snapshot()` and full download/asset lists.

- [ ] **Step 1: Add adapter test** asserting Settings consumes aggregate values rather than full download/asset record lists.
- [ ] **Step 2: Add Room aggregate Flow queries** on the Wave-5 explicit-download, automatic-Chapter-cache, and `reader_asset_entries` tables and combine them in `RoomStorageUsageRepository`; no aggregate query materializes row models.
- [ ] **Step 3: Bind the narrow repository in `StorageModule` and switch `AppStorageSummaryAdapter`** to combine `StorageUsageSnapshot` with Settings while preserving existing `totalBytes`, `automaticCacheBytes`, and quota semantics.
- [ ] **Step 4: Run Settings/download/cache tests**.
- [ ] **Step 5: Commit** `perf(settings): observe storage aggregates directly`.

---

### Task 5: Add explicit Default dispatcher boundaries to scalable UI projectors

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt`
- Verify/no change: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipeline.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModelTest.kt`

**Interfaces:**
- Inject/use `AppDispatchers` already provided by `core:common`.
- Pure reducer/projector work executes under `withContext(dispatchers.default)` or an equivalent `mapLatest/flowOn` boundary that does not move Compose state mutation off Main incorrectly.

- [ ] **Step 1: Add dispatcher tests** with a named test dispatcher/context marker proving the reducer executes on `default`, not the ViewModel Main collector context.
- [ ] **Step 2: Extract only genuinely CPU-heavy pure mapping/reduction blocks** where needed; keep UI state mutation/publication structured and deterministic.
- [ ] **Step 3: Apply boundary to Search service orchestration/projection** after Wave-2 global index removal, then Library and Downloads bounded reducers.
- [ ] **Step 4: Apply the same pure-reducer boundary to `UpdatesViewModel` and `HomeDashboardViewModel`; verify `DiscoverProjectionPipeline` already uses `AppDispatchers.default` and leave that existing boundary unchanged.**
- [ ] **Step 5: Run ViewModel unit/screenshot tests**.
- [ ] **Step 6: Commit** `perf(ui): isolate foreground cpu projection from main`.

---

### Task 6: Measure retained-tab semantic work and classify RISK-LIFECYCLE

**Files:**
- Measure: `app/src/main/kotlin/app/openstory/navigation/PersistentTopLevelNavDisplay.kt`
- Benchmark: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Consume diagnostics: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkReactiveWorkDiagnostics.kt`
- Create: `docs/superpowers/checkpoints/2026-09-07-hikari-perf-retained-tab-risk.md`

**Interfaces:**
- This task does not change production navigation/lifecycle behavior. It measures the final post-Wave-1–6 architecture; material inactive semantic work promotes `RISK-LIFECYCLE` for a focused follow-up design rather than applying an unreviewed lifecycle workaround here.

- [ ] **Step 1: Run the Wave-0 retained-tab diagnostic** against post-Wave-1–6 architecture and record inactive Room/reducer/subscriber counts.
- [ ] **Step 2: If inactive work is negligible**, close `RISK-LIFECYCLE` as accepted/non-defect and make no lifecycle production change.
- [ ] **Step 3: If inactive work is material**, record the exact collectors/repository calls that remain active, mark `RISK-LIFECYCLE` promoted, and write the required invariants for a focused follow-up design: preserve composition, `ViewModelStore`, saveable state, back stack, and P6 warm-navigation behavior.
- [ ] **Step 4: Run Home↔Discover warm-navigation A/B and repeated-cycle RSS** so the checkpoint contains both the performance benefit of retention and the measured inactive-work cost.
- [ ] **Step 5: Commit the evidence checkpoint** with `docs(perf): classify retained-tab demand risk`; do not change lifecycle/navigation production code in this task.

---

### Task 7: Close whole-UI bounded-demand gate

**Files:**
- Create: `scripts/tests/performance-big-update-bounded-binding-policy-test.sh`
- Create: `docs/superpowers/checkpoints/2026-09-07-hikari-perf-wave-7-ui.md`

- [ ] **Step 1: Search the production tree for remaining UI `observeAll()/observe()` uses** and classify each as legitimate global surface vs bounded surface; explicitly include Story membership/progress and `DownloadViewModel` release-status consumers, and do not blindly eliminate legitimate global screens.
- [ ] **Step 2: Add `performance-big-update-bounded-binding-policy-test.sh`** as a fail-closed source guard for the concrete production bindings introduced/used by this program. It must verify that `RoomCatalogStoryProjectionRepository`, `RoomContentMappingRepository`, `RoomReadingProgressRepository`, `RoomChapterRepository`, and `RoomDownloadRepository` override the bounded APIs consumed by UI hot paths, and that the named Story/Library/Downloads consumers call those bounded APIs rather than interface defaults implemented through `observeAll()`. Keep legitimate global-screen APIs allowed explicitly; the script must not ban `observeAll()` repository-wide.
- [ ] **Step 3: Add a self-test case inside the policy script (temporary fixture/copy or shell mutation pattern matching existing script tests)** proving the guard fails when a required production override/call is replaced by its global fallback, then restore the real tree and prove the guard passes.
- [ ] **Step 4: Run focused suites**: `:feature:catalog:testDebugUnitTest`, affected `:reader`, `:downloads`, `:app` tests.
- [ ] **Step 5: Run architecture scripts plus** `bash scripts/tests/performance-big-update-bounded-binding-policy-test.sh`.
- [ ] **Step 6: Re-run Library/Downloads/Search/navigation macrobenchmarks** across small/aged fixtures.
- [ ] **Step 7: Record A8/L1/L8 closure evidence, production-binding guard status, and lifecycle risk outcome**.
- [ ] **Step 8: Commit** `test(perf): close bounded ui demand wave`.

---

## Wave 7 acceptance gate

- [ ] Library does not maintain global catalog/mapping/progress subscriptions by default.
- [ ] Story detail observes Library membership + progress by one resolved Story, not whole histories.
- [ ] Story chapter download status observes only the current stable release-ID set; standalone Downloads enrichment is bounded to explicit download IDs.
- [ ] Story resume does not observe all progress.
- [ ] Downloads does not observe all Chapters/canonical projections to render a bounded download set.
- [ ] Settings storage summary consumes aggregates.
- [ ] CPU-heavy bounded reducers have explicit `AppDispatchers.default` boundary.
- [ ] retained navigation warm-state behavior is preserved; `RISK-LIFECYCLE` is either accepted as non-defect or promoted with measured evidence for a separate focused design.
