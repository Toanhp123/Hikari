# Navigation, Story, and Reader Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement performance Waves 1-3: retain top-level navigation state, activate Story subfeatures only when needed, and keep one Reader session while changing chapters.

**Architecture:** Discover, Home, and Library each own a saved Navigation 3 back stack and independently decorated entry state. Story keeps only its always-visible state hot while Mapping, Chapters, and download-status observation follow the selected Story section. Reader changes chapter inside one `ReaderViewModel` and materializes chapter groups in one pass.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation 3 1.1.4, Lifecycle 2.11.0, Hilt, Coroutines/Flow, Room, Compose UI tests.

## Global Constraints

- Preserve the visible Discover/Home/Library, Story, and Reader information architecture.
- Do not implement Wave 4 work in this patch.
- Do not create one download Flow collector per chapter release.
- Do not push a new Reader route for Previous/Next chapter.
- Retained off-screen UI state must use demand-driven `WhileSubscribed` collection.
- Keep repository UI token/shared-component, architecture, source-layout, lint, and Room-schema contracts intact.

---

### Task 1: Retain top-level navigation state

**Files:**
- Create: `app/src/main/kotlin/app/openstory/navigation/AppNavigationState.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavigator.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt`
- Create: `app/src/test/kotlin/app/openstory/navigation/AppNavigatorTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `AppNavigationState`, `rememberAppNavigationState()`, `AppNavigationState.decoratedEntries()`.
- Consumes: `NavBackStack<NavKey>`, `rememberDecoratedNavEntries`, `rememberViewModelStoreProvider`.

- [x] **Step 1: Write navigation regression tests before production changes.**

```kotlin
navigator.navigate(AppRoute.Search)
navigator.selectTopLevel(TopLevelDestination.Library)
navigator.navigate(AppRoute.Story("library-story"))
navigator.selectTopLevel(TopLevelDestination.Home)

assertEquals(AppRoute.Search, navigator.currentRoute)
assertEquals(
    listOf(AppRoute.Library, AppRoute.Story("library-story")),
    state.backStacks.getValue(AppRoute.Library).toList(),
)
```

The JVM and instrumentation navigation suites both assert that nested histories survive top-level switches and that Back from a non-start root returns to the retained Home stack.

- [x] **Step 2: Verify the baseline performance contract is RED.**

Run:

```bash
bash scripts/tests/performance-lifecycle-policy-test.sh
```

Baseline evidence: `AppNavigationState` did not exist and `AppNavigator.selectTopLevel()` contained `backStack.clear()`.

- [x] **Step 3: Implement three saved top-level stacks and per-stack entry decoration.**

Use this ownership shape:

```kotlin
val discover = rememberNavBackStack(AppRoute.Discover)
val home = rememberNavBackStack(AppRoute.Home)
val library = rememberNavBackStack(AppRoute.Library)

val storeProvider = rememberViewModelStoreProvider(key = route)
rememberDecoratedNavEntries(
    backStack = backStacks.getValue(route),
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(storeProvider),
    ),
    entryProvider = entryProvider,
)
```

`stacksInUse` exposes Home alone when Home is active, otherwise Home plus the selected non-start stack.

- [x] **Step 4: Make navigation operations mutate only the active stack.**

```kotlin
fun selectTopLevel(destination: TopLevelDestination) {
    navigationState.topLevelRoute = destination.route
}

fun back() {
    val active = navigationState.activeBackStack
    if (active.size > 1) active.removeAt(active.lastIndex)
    else if (navigationState.topLevelRoute != navigationState.startRoute) {
        navigationState.topLevelRoute = navigationState.startRoute
    }
}
```

- [x] **Step 5: Stop retained Library work when it is not collected.**

Use:

```kotlin
SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
```

The existing Discover and Home state flows already use the same demand-driven pattern. Update ViewModel unit tests to actively collect state so they test the lifecycle contract rather than relying on eager sharing.

- [ ] **Step 6: Run Android navigation instrumentation on a device/emulator.**

Run when Android instrumentation is available:

```bash
./gradlew :app:connectedDebugAndroidTest --stacktrace
```

Expected: retained-stack and retained-ViewModel tests pass.

---

### Task 2: Make Story subfeatures demand-driven

**Files:**
- Create: `app/src/main/kotlin/app/openstory/navigation/StorySectionDependencies.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadViewModel.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadService.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/download/DownloadViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/mapping/MappingViewModelTest.kt`

**Interfaces:**
- Produces: `StoryUiState.readableTargets`, `DownloadViewModel.statuses`, `StorySectionDependencies`.
- Consumes: `ChapterRepository.snapshot(storyId)`, `DownloadService.observeAll()`.

- [x] **Step 1: Write regression tests for Story targets and aggregate download observation.**

```kotlin
assertEquals(1, viewModel.state.value.readableTargets.size)
assertEquals(1, chapterRepository.snapshotCalls)
assertEquals(0, chapterRepository.observeCalls)
```

```kotlin
val statuses = viewModel.statuses.first()
assertEquals(1, repository.observeAllCalls)
assertEquals(0, repository.observeCalls)
assertEquals(DownloadState.QUEUED, statuses[releaseId])
```

- [x] **Step 2: Move hero readable targets into `StoryViewModel` without creating ChapterList UI state.**

Materialize one snapshot when the Story ViewModel is created:

```kotlin
readableTargets.value = chapters.snapshot(storyId).asReaderTargets(storyId)
```

`asReaderTargets()` groups releases once by canonical chapter ID and preserves chapter/release order.

- [x] **Step 3: Activate Story subfeature ViewModels only for the selected section.**

```kotlin
when (section) {
    StorySection.OVERVIEW -> StorySectionDependencies()
    StorySection.SOURCES -> sourceDependencies(storyId)
    StorySection.CHAPTERS -> chapterDependencies(storyId, downloadViewModel, navigateToReader)
}
```

Sources creates/collects `MappingViewModel`; Chapters creates/collects `ChapterListViewModel` and download statuses. Their state flows use `WhileSubscribed` so retained ViewModels stop repository work after the timeout when the section leaves composition.

- [x] **Step 4: Replace N per-release download observers with one cold aggregate Flow.**

```kotlin
val statuses = service.observeAll().map { records ->
    records.associate { record -> record.key.releaseId to record.state }
}
```

Delete `DownloadViewModel.watch()` and the Story `LaunchedEffect` that called it for every release. The aggregate Flow is collected only from the Chapters branch.

- [ ] **Step 5: Run targeted catalog tests.**

Run when Gradle distribution access is available:

```bash
./gradlew :feature:catalog:testDebugUnitTest --stacktrace
```

Expected: Story, Chapters, Mapping, and download tests pass.

---

### Task 3: Keep one Reader ViewModel across chapter changes

**Files:**
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Test: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`

**Interfaces:**
- Produces: `ReaderViewModel.openChapter(CanonicalChapterId)` and `ChapterGraphSnapshot.toReaderGroups()`.

- [x] **Step 1: Write Reader regression tests before changing navigation callbacks.**

```kotlin
viewModel.openChapter(CanonicalChapterId("chapter-2"))
runCurrent()

assertEquals("chapter-2", viewModel.state.value.chapterLabel)
assertEquals("chapter-2", savedState.get<String>("reader.chapter-id"))
```

Also verify pending progress is flushed under the previous chapter ID before the new chapter becomes active, and verify one-pass grouping preserves chapter/release ordering.

- [x] **Step 2: Persist current chapter in `SavedStateHandle` and reload inside the same ViewModel.**

```kotlin
fun openChapter(chapterId: CanonicalChapterId) {
    if (chapterId == this.chapterId) return
    mutableState.value = mutableState.value.copy(
        loading = true,
        document = null,
        selectedReleaseId = null,
        failure = null,
    )
    this.chapterId = chapterId
    savedState[CHAPTER_ID_KEY] = chapterId.value
    savedState.remove<String>(RELEASE_ID_KEY)
    load(explicitReleaseId = null, flushProgress = true)
}
```

- [x] **Step 3: Materialize the chapter graph in one pass.**

```kotlin
val releasesByChapter = releases.groupBy { release -> release.canonicalChapterId }
return chapters.filterNot { it.tombstoned }.map { chapter ->
    CanonicalChapterGroup(chapter, releasesByChapter[chapter.id].orEmpty())
}
```

- [x] **Step 4: Route Previous/Next directly to the existing Reader ViewModel.**

```kotlin
onPreviousChapter = viewModel::openChapter
onNextChapter = viewModel::openChapter
```

No `navigate(AppRoute.Reader(...))` call is allowed for those actions.

- [ ] **Step 5: Run targeted Reader tests.**

Run when Gradle distribution access is available:

```bash
./gradlew :feature:reader:testDebugUnitTest --stacktrace
```

Expected: chapter switch, progress flush, release selection, and graph grouping tests pass.

---

### Task 4: Lock the performance lifecycle contract and package the patch

**Files:**
- Create: `scripts/tests/performance-lifecycle-policy-test.sh`
- Modify: `docs/superpowers/specs/2026-08-15-navigation-story-reader-performance-design.md`
- Modify: `docs/superpowers/plans/2026-08-15-navigation-story-reader-performance.md`

**Interfaces:**
- Produces: repository static guardrail for all Wave 1-3 lifecycle invariants.

- [x] **Step 1: Add static policy assertions.**

The policy rejects destructive top-level clearing, missing per-stack decoration, eager retained top-level/Story tab flows, per-release Story download watchers, Reader route stacking, and per-chapter full-release scans.

- [x] **Step 2: Run repository static verification.**

Run:

```bash
bash scripts/tests/performance-lifecycle-policy-test.sh
bash scripts/tests/ui-shared-component-policy-test.sh
bash scripts/tests/ui-token-policy-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-source-layout.sh
bash scripts/verify-ui-tokens.sh
bash scripts/verify-structural-suppressions.sh
bash scripts/verify-current-architecture.sh
git diff --check
```

Expected: every command exits 0. `scripts/verification-common.sh` already discovers every `scripts/tests/*.sh`, so `scripts/verify.sh` does not need a special-case edit.

- [ ] **Step 3: Run the Gradle verification requested for the final patch.**

Run on a machine with the Gradle distribution available:

```bash
./gradlew :app:compileDebugKotlin \
  :feature:catalog:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  --stacktrace
./gradlew compareRoborazziDebug --stacktrace
./scripts/verify.sh
```

- [x] **Step 4: Generate and apply-check the final patch.**

Run:

```bash
git diff --binary HEAD > /mnt/data/Hikari-performance-waves-1-3.patch
git apply --check /mnt/data/Hikari-performance-waves-1-3.patch
sha256sum /mnt/data/Hikari-performance-waves-1-3.patch
```

Expected: `git apply --check` exits 0 and the patch contains only Waves 1-3 plus their tests/docs/guardrail.
