# Architecture Baseline 2 R4 - Presentation, Navigation, and DI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Home, Search, and Story into one `:feature:catalog` presentation module, replace manual `OpenStoryAppGraph`/ViewModel factories with Hilt constructor injection, and simplify Navigation 3 routes to stable identities.

**Architecture:** Presentation depends only on `:catalog` and common IDs. Hilt wires storage/runtime/catalog implementations at the app composition root. Navigation 3 owns route/back-stack state, uses NavEntry-scoped ViewModel stores, and uses Hilt ViewModels; Story receives only `StoryId` through assisted injection.

**Tech Stack:** Jetpack Compose BOM 2026.06.00, Lifecycle 2.11.0, Navigation 3 1.1.4, Hilt 2.60.1, androidx.hilt lifecycle-viewmodel-compose 1.3.0.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.


---
### Task 1: Add Navigation 3 ViewModel/Hilt dependencies required by the canonical lifecycle pattern

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `feature/catalog/build.gradle.kts`

**Interfaces:**
- Adds:
  - `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0`
  - `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0`
  - `javax.inject:javax.inject:1` as an explicit dependency for pure constructor-injected target modules.

- [ ] **Step 1: Write a dependency-policy RED test**

Extend `build-logic/src/test/kotlin/app/openstory/build/RepositoryHygieneTest.kt` to require aliases:

```text
androidx-lifecycle-viewmodel-navigation3
androidx-hilt-lifecycle-viewmodel-compose
javax-inject
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :build-logic:test --tests app.openstory.build.RepositoryHygieneTest --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Add version-catalog aliases and module dependencies**

Use lifecycle version `2.11.0` for `lifecycle-viewmodel-navigation3` and explicit AndroidX Hilt version `1.3.0`.

- [ ] **Step 4: Run configuration/build tests**

```bash
./gradlew :build-logic:test :feature:catalog:assembleDebug :app:assembleDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts feature/catalog/build.gradle.kts   build-logic/src/test/kotlin/app/openstory/build/RepositoryHygieneTest.kt
git commit -m "build: add nav3 hilt viewmodel support"
```

### Task 2: Port Home presentation into `:feature:catalog/home`

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home/HomeUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home/HomeViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home/HomeScreen.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home/HomeCard.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/home/HomeViewModelTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/home/HomeScreenTest.kt`

**Interfaces:**
- `HomeViewModel` injects `CatalogRepository`, `CatalogHomeQuery`, and `CatalogRefreshService`.
- Repository observation is direct; no forwarding `ObserveHomeUseCase`.
- `HomeScreen` receives immutable UI state + callbacks and imports no repository/service.

- [ ] **Step 1: Write RED ViewModel tests**

Required:

```text
cached Home emits before refresh completes
refresh failure keeps cached sections and exposes non-blocking failure state
source selection changes cached projection without plugin call
refresh action invokes CatalogRefreshService exactly once
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest   --tests app.openstory.catalog.ui.home.HomeViewModelTest --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement `@HiltViewModel` with `viewModelScope`**

Do not create custom `CoroutineScope`; collect flows with lifecycle-safe state in the composable.

- [ ] **Step 4: Port Compose semantics and run UI test**

Preserve revalidated current behavior: combined/source-specific sections, stable item keys, source/score semantics, refresh status, cover-renderer seam.

```bash
./gradlew :feature:catalog:testDebugUnitTest   :feature:catalog:connectedDebugAndroidTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home   feature/catalog/src/test/kotlin/app/openstory/catalog/ui/home   feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/home
git commit -m "ui: port catalog home presentation"
```

### Task 3: Port Search presentation without structural suppression

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchFilters.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchResultCard.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchViewModelTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/search/SearchScreenTest.kt`

**Interfaces:**
- ViewModel injects `CatalogSearchService`.
- Query debounce/cancellation is presentation behavior; catalog search execution stays in service.
- No `@file:Suppress("TooManyFunctions")`.

- [ ] **Step 1: Write RED tests**

Required:

```text
query under minimum length does not execute search
new query cancels/obsoletes previous result
per-source filter state remains source-scoped
partial source failures coexist with successful results
recent searches stay memory-only
```

- [ ] **Step 2: Verify RED**

Expected: **FAIL**.

- [ ] **Step 3: Implement ViewModel and focused components**

Use `viewModelScope`, `MutableStateFlow`, `debounce`, `mapLatest`/cancellation-safe search. Keep filter rendering in `SearchFilters.kt` and result card in `SearchResultCard.kt` because each has independent UI semantics, not to satisfy line count.

- [ ] **Step 4: Run unit + Compose tests and suppression gate**

```bash
./gradlew :feature:catalog:testDebugUnitTest   :feature:catalog:connectedDebugAndroidTest --stacktrace
bash scripts/verify-structural-suppressions.sh
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search   feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search   feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/search
git commit -m "ui: port catalog search presentation"
```

### Task 4: Port Story presentation with canonical `StoryId` route input

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/story/StoryScreenTest.kt`

**Interfaces:**
- `StoryViewModel` is Hilt-assisted:

```kotlin
@HiltViewModel(assistedFactory = StoryViewModel.Factory::class)
class StoryViewModel @AssistedInject constructor(
    @Assisted private val storyId: StoryId,
    private val repository: CatalogRepository,
    private val details: CatalogDetailsService,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(storyId: StoryId): StoryViewModel
    }
}
```

- Story route does not carry plugin/source IDs.

- [ ] **Step 1: Write RED tests**

Required:

```text
cached story renders without source refresh
retry refreshes exact available source selected by catalog state
source detail failure does not change canonical StoryId
ViewModel has no PluginRuntime/Room dependency
```

- [ ] **Step 2: Verify RED**

Expected: **FAIL**.

- [ ] **Step 3: Implement ViewModel/screen**

Repository flow supplies cached canonical/source metadata. Details service handles source enrichment. Source-selection UI remains a catalog UI action and never becomes route identity.

- [ ] **Step 4: Run unit + Compose tests**

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story   feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story   feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/story
git commit -m "ui: port canonical story presentation"
```

### Task 5: Replace manual composition with minimal Hilt bindings

**Files:**
- Create: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`
- Create: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`
- Create: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`
- Modify: `catalog/build.gradle.kts` to add `implementation(libs.javax.inject)`.
- Modify: `plugins/runtime/build.gradle.kts` to add `implementation(libs.javax.inject)`.
- Test: `app/src/test/kotlin/app/openstory/di/CompositionPolicyTest.kt`

**Interfaces:**
- `StorageModule` provides singleton Room database, `CatalogRepository`, `PluginStateStore`.
- `PluginRuntimeModule` provides package root/JavaScript engine/credential provider/runtime facade.
- `CatalogModule` binds/provides `CatalogSourceRegistry` and stateless catalog services only where constructor injection cannot.
- No service locator or graph object.

- [ ] **Step 1: Write the focused RED composition-module test**

Create `CompositionPolicyTest` for this task's deliverable only. Assert the three Hilt module source files exist, are installed in the intended Hilt component, and contain no provider/binding whose return type is a ViewModel or `ViewModelProvider.Factory`.

Also assert target presentation/catalog source obeys the approved platform/scheduling policy:

```text
catalog/src/main                    -> no android.content.Context, no AppDispatchers
feature/catalog/**/ViewModel.kt    -> no Context constructor dependency, no AppDispatchers, no custom CoroutineScope, no Dispatchers.*
```

ViewModels use `viewModelScope`; blocking Android adapters own main-safety at their boundary. Do **not** scan for legacy `OpenStoryAppGraph` symbols yet; Task 7 owns their deletion and its own RED gate.

- [ ] **Step 2: Implement modules**

Prefer constructor injection. Use `@Provides` for framework creation and interface bindings when a direct `@Binds` module is not practical.

- [ ] **Step 3: Compile Hilt graph**

```bash
./gradlew :app:kspDebugKotlin :app:assembleDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4: Run app unit tests**

```bash
./gradlew :app:testDebugUnitTest --stacktrace
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/openstory/di   app/src/test/kotlin/app/openstory/di   catalog/build.gradle.kts plugins/runtime/build.gradle.kts
git commit -m "app: wire baseline services with hilt"
```

### Task 6: Replace navigation wiring with NavEntry-scoped Hilt ViewModels

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppNavigator.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Create/Modify: `app/src/main/kotlin/app/openstory/navigation/TopLevelDestination.kt`
- Modify: `app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt`
- Test: `app/src/test/kotlin/app/openstory/navigation/AppRouteSerializationTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt`

**Interfaces:**
- Canonical route:

```kotlin
@Serializable
data class Story(val storyId: String) : AppRoute
```

- `NavDisplay` installs `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`.
- Home/Search use `hiltViewModel()`.
- Story uses `hiltViewModel<StoryViewModel, StoryViewModel.Factory>(creationCallback = { factory -> factory.create(StoryId(route.storyId)) })`.

- [ ] **Step 1: Write RED route test**

Replace the Story-route assertion in `AppRouteSerializationTest.kt` with:

```kotlin
@Test fun storyRouteRoundTripsWithCanonicalIdentityOnly() {
    val route: AppRoute = AppRoute.Story(storyId = "story_123")
    val encoded = Json.encodeToString(AppRoute.serializer(), route)
    assertEquals(route, Json.decodeFromString(AppRoute.serializer(), encoded))
    assertTrue("story_123" in encoded)
    assertFalse("pluginId" in encoded)
    assertFalse("sourceId" in encoded)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest \
  --tests app.openstory.navigation.AppRouteSerializationTest.storyRouteRoundTripsWithCanonicalIdentityOnly \
  --stacktrace
```

Expected: **FAIL** with the current route shape.

- [ ] **Step 3: Implement navigation host**

Keep only top-level navigation and destination wiring in `AppNavHost`; no domain orchestration.

- [ ] **Step 4: Run app navigation tests**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/openstory/navigation   app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt   app/src/test/kotlin/app/openstory/navigation   app/src/androidTest/kotlin/app/openstory/navigation
git commit -m "app: simplify navigation and viewmodel scope"
```

### Task 7: Delete old presentation/composition and accept R4

**Files:**
- Delete: `feature/home/`
- Delete: `feature/story/`
- Delete: `app/src/main/kotlin/app/openstory/di/OpenStoryAppGraph.kt`
- Delete: `app/src/main/kotlin/app/openstory/navigation/AppViewModels.kt`
- Delete: `app/src/main/kotlin/app/openstory/navigation/OpenStoryNavDisplay.kt`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `config/quality/structural-suppressions.txt`
- Create: `docs/internal/checkpoints/architecture-baseline-2-r4.md`
- Modify: `docs/project/current-state.md`

- [ ] **Step 1: Write the RED legacy-composition removal gate**

Extend `CompositionPolicyTest` to scan production app source and fail on:

```text
OpenStoryAppGraph
LambdaViewModelFactory
createHomeViewModel
createSearchViewModel
createStoryDetailViewModel
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.di.CompositionPolicyTest --stacktrace
```

Expected before deletion: **FAIL** because the legacy composition symbols still exist.

- [ ] **Step 2: Remove legacy modules and the R0 suppression allowance**

`config/quality/structural-suppressions.txt` becomes empty unless a new narrowly justified target-code exception was explicitly approved. Do not carry the SearchScreen allowance forward.

- [ ] **Step 3: Tighten final feature dependency policy**

Remove `:feature:home` and `:feature:story`; app depends on `:feature:catalog`.

Run:

```bash
./gradlew projects verifyArchitecture --stacktrace
bash scripts/verify-structural-suppressions.sh
```

Expected: **PASS**.

- [ ] **Step 4: Run full current user-journey gates**

```bash
./gradlew :catalog:test   :feature:catalog:testDebugUnitTest   :feature:catalog:connectedDebugAndroidTest   :app:testDebugUnitTest   :app:connectedDebugAndroidTest   :app:assembleDebug --stacktrace
./scripts/verify.sh
```

Expected: **PASS**.

- [ ] **Step 5: Record evidence and advance state**

Set:

```text
Architecture Baseline 2 R4: ACCEPTED
Current active boundary: R5 - Repository Cleanup
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "ui: replace wave five presentation architecture"
```
