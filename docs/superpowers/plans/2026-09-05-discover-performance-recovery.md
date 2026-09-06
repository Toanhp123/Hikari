# Discover Performance Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Discover load, refresh, scroll, and scroll-to-top latency while preserving canonical-only presentation and deterministic semantic ordering.

**Architecture:** Discover refresh will commit provider data and defer canonical engine work to the durable queue, while a bounded-concurrency settlement pipeline prepares visible canonical Stories independently. UI identities will remain stable during progressive settlement, top-level live backdrop capture will be disabled, and all affected collection screens will share a short-distance scroll-to-top primitive.

**Tech Stack:** Kotlin 2.4, Coroutines/Flow, Jetpack Compose, Navigation 3, Room, Coil 3, Macrobenchmark, Robolectric/Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-05-discover-performance-recovery-design.md`

## Global Constraints

- Preserve reconciliation, fusion, source-preference, and Discover ranking policy.
- Publish canonical projections only; never substitute raw provider cards.
- Keep Room schema unchanged.
- Limit concurrent Discover settlement work to exactly four Stories.
- Keep benchmark artwork deterministic and local; do not add network-dependent benchmarks.
- Work inline on branch `perf/discover-end-to-end`; do not create sub-agents or worktrees.

---

### Task 1: Defer Discover Refresh Canonical Work

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipeline.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Modify: `docs/internal/checkpoints/canonical-refresh-foreground-convergence-p0-2026-08-24.md`

**Interfaces:**
- Consumes: `CatalogRefreshService.refresh(prioritySelector)`.
- Produces: Discover refresh calls `CatalogRefreshPrioritySelector` with an empty foreground Story set while retaining the existing `DiscoverRefreshExecution` report contract.

- [ ] **Step 1: Write the failing refresh-policy test**

Rename the current 19-Story expectation and assert that a fresh Discover refresh sends an empty immediate set:

```kotlin
@Test
fun freshDiscoverRefreshDefersCanonicalConvergence() = runTest(dispatcher.scheduler) {
    val repository = FakeRepository(emptyList())
    val source = FakeSource().apply {
        homeAction = { CatalogSourceResult.Success(discoverSections(itemsPerSection = 10)) }
    }
    val engine = RecordingDiscoverEngine()

    viewModel(repository, source, engine = engine)
    runCurrent()

    assertEquals(emptySet(), engine.immediateStoryIdBatches.single())
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest --tests "app.openstory.catalog.ui.discover.DiscoverViewModelTest.freshDiscoverRefreshDefersCanonicalConvergence" --no-daemon
```

Expected: FAIL because the current pipeline selects 19 immediate Stories.

- [ ] **Step 3: Implement the deferred Discover policy**

Change the selector in `DiscoverRefreshPipeline.refresh()`:

```kotlin
val results = refreshService.refresh(
    prioritySelector = CatalogRefreshPrioritySelector { emptySet() },
)
```

Remove imports that are no longer needed. Update the historical checkpoint to state that the previous 19-Story foreground boundary was superseded by the 2026-09-05 recovery design.

- [ ] **Step 4: Run focused and catalog refresh tests**

Run:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest --tests "app.openstory.catalog.ui.discover.DiscoverViewModelTest" --tests "app.openstory.catalog.ui.discover.DiscoverRefreshPipelineTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipeline.kt feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt docs/internal/checkpoints/canonical-refresh-foreground-convergence-p0-2026-08-24.md
git commit -m "perf(discover): defer refresh canonical convergence"
```

### Task 2: Settle Canonical Stories with Bounded Concurrency

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipeline.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipelineTest.kt`

**Interfaces:**
- Consumes: `CanonicalBootstrapUseCase.ensureReady(StoryId)` and `CatalogStoryProjectionRepository.find(StoryId)`.
- Produces: `settle()` retains seed-first and deterministic-map semantics, starts no more than four missing Stories concurrently, and emits one result per completed ordered batch rather than one result per Story.

- [ ] **Step 1: Change the progressive-emission test to the desired batched contract**

Replace the expected emissions for two missing Stories with:

```kotlin
assertEquals(
    listOf(
        emptyList(),
        listOf(first, second),
    ),
    emissions.map { it.keys.toList() },
)
```

- [ ] **Step 2: Add a failing maximum-concurrency test**

Use `CompletableDeferred<Unit>`, `AtomicInteger`, and a preparing canonical fixture. Start settlement for six Stories, run the test scheduler, and assert exactly four rebuilds entered before releasing the gate:

```kotlin
assertEquals(4, active.get())
assertEquals(4, maximumActive.get())
```

After completing the gate, assert the final map contains all six Story IDs and `maximumActive` remains four.

- [ ] **Step 3: Run the bootstrap pipeline tests and verify RED**

Run:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest --tests "app.openstory.catalog.ui.discover.DiscoverCanonicalBootstrapPipelineTest" --no-daemon
```

Expected: the emission test fails with three emissions instead of two, and the concurrency test observes one active rebuild.

- [ ] **Step 4: Implement bounded ordered batches**

In `settle()`:

```kotlin
val missing = expectedIds.filterNot(settlements::containsKey)
coroutineScope {
    val semaphore = Semaphore(MAX_CONCURRENT_SETTLEMENTS)
    val pending = missing.map { storyId ->
        async {
            semaphore.withPermit {
                storyId to settleOne(storyId, selectedContentType)
            }
        }
    }
    pending.chunked(SETTLEMENT_EMISSION_BATCH_SIZE).forEach { batch ->
        batch.awaitAll().forEach { (storyId, settlement) -> settlements[storyId] = settlement }
        emit(expectedIds.mapNotNull { id -> settlements[id]?.let { id to it } }.toMap())
    }
}
```

Set both constants to four. Preserve cancellation rethrowing and typed per-Story failures.

- [ ] **Step 5: Run all Discover bootstrap/projection/ViewModel tests**

Run:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest --tests "app.openstory.catalog.ui.discover.DiscoverCanonicalBootstrapPipelineTest" --tests "app.openstory.catalog.ui.discover.DiscoverProjectionPipelineTest" --tests "app.openstory.catalog.ui.discover.DiscoverViewModelTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipeline.kt feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverCanonicalBootstrapPipelineTest.kt
git commit -m "perf(discover): settle visible stories concurrently"
```

### Task 3: Stabilize Incremental Discover Composition

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestGrid.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverPopularPager.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverCompositionIdentityTest.kt`

**Interfaces:**
- Produces: Latest row identity depends only on row position; Popular page correction is skipped when the target page already equals the current page.

- [ ] **Step 1: Write a failing Compose identity test**

```kotlin
@Test
fun fillingLatestRowRetainsTheFirstCardNode() {
    lateinit var latest: MutableState<List<DiscoverStoryItem>>
    compose.setContent {
        latest = remember { mutableStateOf(listOf(item(1))) }
        HikariTheme {
            DiscoverScreen(state = readyState(latest.value), /* callbacks */)
        }
    }
    val before = compose.onNodeWithTag("discover-latest-item-story-1", useUnmergedTree = true)
        .fetchSemanticsNode().id

    compose.runOnIdle { latest.value = listOf(item(1), item(2), item(3)) }

    val after = compose.onNodeWithTag("discover-latest-item-story-1", useUnmergedTree = true)
        .fetchSemanticsNode().id
    assertEquals(before, after)
}
```

The production mutation this catches is restoring Story-content-derived outer row keys, which disposes the first card when later cards settle.

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest --tests "app.openstory.catalog.ui.discover.DiscoverCompositionIdentityTest" --no-daemon
```

Expected: FAIL because filling the row changes its LazyColumn item key and recreates the first card semantics node.

- [ ] **Step 3: Implement stable row and pager policies**

Use constant/position keys for LazyColumn rows and wrap cards in Compose `key(item.storyId)` inside each `Row`. In the Popular pager, call `scrollToPage` only when the calculated target differs from `pagerState.currentPage`; appending pages around the retained Story therefore performs no scroll mutation.

- [ ] **Step 4: Run Discover UI tests**

Run:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest --tests "app.openstory.catalog.ui.discover.*" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestGrid.kt feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverPopularPager.kt feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverCompositionIdentityTest.kt
git commit -m "perf(discover): stabilize incremental section identity"
```

### Task 4: Add Reliable Shared Scroll-to-Top

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/scroll/HikariScrollToTop.kt`
- Create: `core/designsystem/src/test/kotlin/app/openstory/designsystem/scroll/HikariScrollToTopTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt`
- Modify: corresponding top-level chrome tests under `feature/catalog/src/test/kotlin/...`

**Interfaces:**
- Produces: `suspend fun LazyListState.hikariScrollToTop()` and `suspend fun LazyGridState.hikariScrollToTop()`.
- Screens accept optional injected list/grid state parameters and track one scroll Job.

- [ ] **Step 1: Write failing helper tests**

Use real Compose lazy states in `runComposeUiTest`. Scroll to an index greater than the staging threshold, invoke the helper, advance until idle, and assert exact index and offset zero for both list and grid state.

- [ ] **Step 2: Strengthen screen tests before production edits**

Inject remembered states into Discover, Home, Search, Library list, and Library grid tests. After clicking Back to top, assert:

```kotlin
compose.runOnIdle {
    assertEquals(0, listState.firstVisibleItemIndex)
    assertEquals(0, listState.firstVisibleItemScrollOffset)
}
```

Use the equivalent grid assertions. Also assert the action remains visible whenever either index or offset is non-zero.

- [ ] **Step 3: Run the helper and screen tests and verify RED**

Run:

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "app.openstory.designsystem.scroll.HikariScrollToTopTest" :feature:catalog:testDebugUnitTest --tests "*TopLevelChromeTest" --tests "app.openstory.catalog.ui.search.SearchScreenshotTest" --no-daemon
```

Expected: compilation fails because helpers and injectable states do not exist, or exact-top assertions expose the threshold behavior.

- [ ] **Step 4: Implement the shared operation**

```kotlin
suspend fun LazyListState.hikariScrollToTop() {
    if (firstVisibleItemIndex > SCROLL_TO_TOP_STAGING_INDEX) {
        scrollToItem(SCROLL_TO_TOP_STAGING_INDEX)
    }
    animateScrollToItem(0)
}
```

Add the grid equivalent and set the staging index to three. Derive visibility from `firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0`. Each screen stores `var scrollToTopJob by remember { mutableStateOf<Job?>(null) }`, cancels the previous job, and launches one replacement.

- [ ] **Step 5: Run design-system and feature tests**

Run:

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest :feature:catalog:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add core/designsystem/src/main/kotlin/app/openstory/designsystem/scroll core/designsystem/src/test/kotlin/app/openstory/designsystem/scroll feature/catalog/src/main/kotlin/app/openstory/catalog/ui feature/catalog/src/test/kotlin/app/openstory/catalog/ui
git commit -m "fix(ui): make scroll to top bounded and exact"
```

### Task 5: Disable Top-level Live Backdrop Capture

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/ui/HikariAppShell.kt`
- Modify: `app/src/test/kotlin/app/openstory/navigation/AppShellScreenshotTest.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`

**Interfaces:**
- Produces: the shell retains `HikariBackdropHost`, always sets `captureBackdrop = false`, and passes a fallback/no-token scope to floating navigation.

- [ ] **Step 1: Verify the existing no-backdrop fallback behavior**

Run the real design-system tests that render `HikariFloatingNavigation` without a backdrop scope:

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "app.openstory.designsystem.HikariProductPrimitivesTest" --no-daemon
```

Expected: PASS, proving the fallback surface already works before the shell changes. This one-line capture policy is a performance configuration; the behavioral regression authority is the screenshot and Macrobenchmark path, not a source-text assertion.

- [ ] **Step 2: Disable capture and retire identical A/B journeys**

Keep the host stable but set `captureBackdrop = false`. Remove or rename top-level enabled/disabled benchmark journeys that would now execute identical production behavior; keep one production navigation regression journey.

- [ ] **Step 3: Run app screenshots before accepting new output**

```powershell
.\gradlew.bat :app:verifyRoborazziDebug --no-daemon
```

Expected: either PASS because the fallback pixels are already equivalent, or FAIL only on shell snapshots whose floating navigation now uses the fallback surface. Inspect every diff before recording new goldens.

- [ ] **Step 4: Run app screenshots/navigation tests and benchmark assembly**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :benchmark:assemble --no-daemon
```

Expected: PASS; update screenshot goldens only if the fallback surface changes recorded pixels.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/app/openstory/ui/HikariAppShell.kt app/src/test/kotlin/app/openstory/navigation/AppShellScreenshotTest.kt benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt
git commit -m "perf(ui): disable top-level backdrop capture"
```

### Task 6: Add Representative Discover Macrobenchmarks

**Files:**
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt`
- Create: `app/src/benchmarkRelease/res/drawable/benchmark_browse_cover.xml`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt`
- Modify: `benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt`

**Interfaces:**
- Produces: fixture `coverUrl` values resolve to deterministic app-local artwork; driver can verify a ready Discover tag; Macrobenchmark contains `discoverBackToTop`.

- [ ] **Step 1: Add the new Macrobenchmark journey first**

Add `discoverBackToTop` using the desired `discover-ready-content` wait and first-content verification before the ready tag exists.

- [ ] **Step 2: Assemble and verify RED**

Run `:benchmark:assemble`. Expected: compilation fails because the new driver helper/tag contract has not been implemented.

- [ ] **Step 3: Implement local artwork and ready tags**

Set each browse entry to a deterministic `android.resource://app.openstory/...` URL. Add a stable `discover-ready-content` test tag to the first ready section and wait for it before measuring scroll.

- [ ] **Step 4: Implement the back-to-top CUJ**

The journey must enter Discover, wait for ready content, perform enough swipes to show `hikari-scroll-to-top`, click it, and wait for `discover-popular-pager` before ending measurement.

- [ ] **Step 5: Assemble benchmark and regenerate profile sources if signatures changed**

Run:

```powershell
.\gradlew.bat :app:assembleBenchmarkRelease :benchmark:assemble --no-daemon
```

Expected: PASS. Device Macrobenchmark execution remains pending when `adb devices` has no connected device.

- [ ] **Step 6: Commit**

```powershell
git add app/src/benchmarkRelease benchmark/src/main feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover
git commit -m "test(perf): cover discover artwork and back to top"
```

### Task 7: Full Verification and Documentation

**Files:**
- Modify: `docs/internal/checkpoints/` with exact host verification and device-pending status.

**Interfaces:**
- Produces: a clean branch with passing host gates and an explicit list of device-only measurements not claimed.

- [ ] **Step 1: Run focused tests**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest :feature:catalog:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

- [ ] **Step 2: Run build and static verification**

```powershell
.\gradlew.bat :app:assembleDebug :benchmark:assemble :detekt --no-daemon
bash ./scripts/verify-fast.sh
```

- [ ] **Step 3: Inspect repository state**

```powershell
git diff --check
git status --short
git log --oneline master..HEAD
adb devices
```

- [ ] **Step 4: Record exact verification results**

Document commands, pass/fail output, and state that physical-device FrameTiming/Perfetto remains unverified if no device is attached.

- [ ] **Step 5: Commit the checkpoint**

```powershell
git add docs/internal/checkpoints
git commit -m "docs: record discover performance recovery verification"
```
