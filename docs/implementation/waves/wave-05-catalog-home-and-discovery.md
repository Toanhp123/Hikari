<!--
DOCUMENT LIFECYCLE
Status: PLANNED / NOT STARTED IN THIS SNAPSHOT
Current repository note: Start only after the Wave 04 checkpoint is accepted.
Canonical execution status: ../../project/current-state.md
Original planning text below is preserved rather than retroactively rewritten.
-->

# Wave 05 — Catalog Home and Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a useful catalog-driven Home, search, rankings, and story detail experience from bundled and community catalog plugins.

**Architecture:** Catalog plugins feed source-preserving snapshots into Room. A deterministic resolver links duplicates to canonical stories, Home use cases combine cached sections resiliently, and Compose screens render combined or source-specific views without calling plugins directly.

**Tech Stack:** Compose, Navigation 3, plugin host, Room/Flow, coroutines, paging primitives, image loading adapter, UI tests.

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

This wave proves the catalog half of the product and gives a clean install immediate value. It deliberately does not fetch readable chapters or make Library add wait for content sources.

## Entry Dependencies

- Wave 04 checkpoint is approved.
- Bundled plugin package can execute through the secure host.
- Catalog DTOs and Room story/catalog tables are stable.

## Exit Deliverables

- Bundled default catalog.
- Combined and catalog-specific Home.
- Deterministic cross-catalog dedupe and aggregate ranking.
- Search/filter flows.
- Catalog-preserving story detail.
- Resilient partial refresh behavior.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Create catalog ingestion repository and canonical merge boundary

**Files:**
- Create: core/model/src/main/kotlin/app/openstory/model/CatalogSnapshot.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/CatalogRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/RoomCatalogRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/mapper/CatalogMapper.kt
- Test: core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt

**Interfaces:**
- Consumes: Catalog plugin DTOs, canonical story/catalog entities, Room transactions, clock.
- Produces: Atomic ingestion of plugin-owned catalog metadata while preserving source-specific values and linking/creating canonical stories through a narrow resolver interface.

**Acceptance:**
- Refreshing one catalog does not overwrite another catalog score/description.
- Source entry identity is `(catalogPluginId, sourceId)`.
- Removed cards from a Home section do not delete the underlying canonical story.
- Ingestion timestamp and plugin version are retained for diagnostics.

**Implementation notes:**
- At this wave, the resolver may create one canonical story per new source entry; deterministic cross-catalog dedupe is added in Task 3.
- Store remote image references and metadata, not image bytes, in catalog tables.
- Do not mark a story as library-owned during discovery ingestion.

- [ ] **Step 1: Write the failing test**

Create `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt`:

```kotlin
package app.openstory.database.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomCatalogRepositoryTest {
    @Test fun refreshPreservesOtherCatalogMetadata() = runTest {
        val fixture = catalogRepositoryFixture()
        fixture.ingest("catalog.a", score = 8.2, scale = 10.0)
        fixture.ingest("catalog.b", score = 91.0, scale = 100.0)
        fixture.ingest("catalog.a", score = 8.4, scale = 10.0)
        assertEquals(setOf("catalog.a", "catalog.b"), fixture.story().catalogEntries.map { it.catalogPluginId.value }.toSet())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomCatalogRepositoryTest
```

Expected: **FAIL** because catalog ingestion transactions and resolver boundary do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/database/src/main/kotlin/app/openstory/database/repository/CatalogRepository.kt`:

```kotlin
package app.openstory.database.repository

import app.openstory.common.AppResult
import app.openstory.model.CatalogSnapshot
import app.openstory.model.PluginId

interface CatalogRepository {
    suspend fun ingest(pluginId: PluginId, snapshot: CatalogSnapshot): AppResult<Unit>
    suspend fun catalogEntry(pluginId: PluginId, sourceId: String): AppResult<CatalogEntryWithStory?>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomCatalogRepositoryTest
./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/model/src/main/kotlin/app/openstory/model/CatalogSnapshot.kt core/database/src/main/kotlin/app/openstory/database/repository/CatalogRepository.kt core/database/src/main/kotlin/app/openstory/database/repository/RoomCatalogRepository.kt core/database/src/main/kotlin/app/openstory/database/mapper/CatalogMapper.kt core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt
git commit -m "catalog: add source-preserving ingestion repository"
```

### Task 2: Bundle a deterministic default catalog plugin

**Files:**
- Create: bundled-plugins/default-catalog/manifest.json
- Create: bundled-plugins/default-catalog/selector.json
- Create: bundled-plugins/default-catalog/fixtures/home.html
- Create: bundled-plugins/default-catalog/fixtures/search.html
- Create: app/src/main/assets/plugins/default-catalog.osp
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapper.kt
- Test: core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapperTest.kt

**Interfaces:**
- Consumes: Package format, selector runtime, installer, registry, and catalog contract suite.
- Produces: A signed/trusted bundled catalog package installed idempotently on first launch and upgradable through the same version machinery as community plugins.

**Acceptance:**
- Clean install has one enabled catalog without network-time installation.
- Bundled package still passes checksum/layout/contract validation.
- User disable state survives app upgrade.
- A newer bundled version upgrades only under normal capability-diff rules.

**Implementation notes:**
- Use a legally permitted/open metadata endpoint or static development fixture until a production catalog integration is selected.
- Never embed third-party credentials in the package.
- Document attribution/license in the plugin package and app About screen.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapperTest.kt`:

```kotlin
package app.openstory.plugin.host.install

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class BundledPluginBootstrapperTest {
    @Test fun bootstrapIsIdempotentAndPreservesDisabledState() = runTest {
        val fixture = bundledBootstrapFixture()
        fixture.bootstrapper.ensureInstalled()
        fixture.registry.setEnabled("org.openstory.catalog.default", false)
        fixture.bootstrapper.ensureInstalled()
        assertFalse(fixture.registry.find("org.openstory.catalog.default")!!.enabled)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.install.BundledPluginBootstrapperTest
```

Expected: **FAIL** because no bundled package or bootstrap policy exists.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapper.kt`:

```kotlin
package app.openstory.plugin.host.install

class BundledPluginBootstrapper(
    private val assets: BundledPluginAssets,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
) {
    suspend fun ensureInstalled() {
        assets.packages().forEach { pkg ->
            val current = registry.find(pkg.pluginId)
            if (current == null || pkg.version.isNewerThan(current.activeVersion)) installer.install(pkg.request())
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.install.BundledPluginBootstrapperTest
./gradlew :core:plugin-host:test :core:plugin-host:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add bundled-plugins/default-catalog/manifest.json bundled-plugins/default-catalog/selector.json bundled-plugins/default-catalog/fixtures/home.html bundled-plugins/default-catalog/fixtures/search.html app/src/main/assets/plugins/default-catalog.osp core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapper.kt core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapperTest.kt
git commit -m "catalog: ship default bundled catalog plugin"
```

### Task 3: Deduplicate catalog entries and compute deterministic combined Home ranking

**Files:**
- Create: core/matching/build.gradle.kts
- Create: core/matching/src/main/kotlin/app/openstory/matching/TitleNormalizer.kt
- Create: core/matching/src/main/kotlin/app/openstory/matching/CatalogStoryResolver.kt
- Create: core/matching/src/main/kotlin/app/openstory/matching/AggregateRanking.kt
- Test: core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt
- Test: core/matching/src/test/kotlin/app/openstory/matching/AggregateRankingTest.kt

**Interfaces:**
- Consumes: Catalog ingestion boundary, canonical stories, aliases/authors/year metadata.
- Produces: Deterministic cross-catalog candidate resolver and transparent aggregate rank that preserves original catalog sections and scores.

**Acceptance:**
- High-confidence exact external-ID or normalized title+author matches may auto-link.
- Ambiguous same-title works remain separate and produce review candidates.
- Aggregate score converts each score to percentile/scale-normalized value and weights by configured catalog priority, not review count guessed from missing fields.
- Tie-breaking is stable across runs.

**Implementation notes:**
- Store comparison features and score explanation for diagnostics/user merge review.
- User-confirmed merge/split decisions are persisted as overrides and always win.
- Do not use cover perceptual hashing until image handling exists and benchmarks justify it.

- [ ] **Step 1: Write the failing test**

Create `core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt`:

```kotlin
package app.openstory.matching

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStoryResolverTest {
    @Test fun sameTitleDifferentAuthorDoesNotAutoMerge() {
        val resolver = CatalogStoryResolver()
        val result = resolver.compare(
            catalogCandidate(title = "Reborn", authors = setOf("Author A")),
            canonicalCandidate(title = "Reborn", authors = setOf("Author B")),
        )
        assertEquals(MergeDecision.REVIEW, result.decision)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:matching:test --tests app.openstory.matching.CatalogStoryResolverTest.sameTitleDifferentAuthorDoesNotAutoMerge
```

Expected: **FAIL** because catalog matching and deterministic normalization are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/matching/src/main/kotlin/app/openstory/matching/TitleNormalizer.kt`:

```kotlin
package app.openstory.matching

import java.text.Normalizer
import java.util.Locale

object TitleNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\p{L}\p{N}]+"), " ")
        .trim()
        .replace(Regex("\s+"), " ")
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:matching:test --tests app.openstory.matching.CatalogStoryResolverTest.sameTitleDifferentAuthorDoesNotAutoMerge
./gradlew :core:matching:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/matching/build.gradle.kts core/matching/src/main/kotlin/app/openstory/matching/TitleNormalizer.kt core/matching/src/main/kotlin/app/openstory/matching/CatalogStoryResolver.kt core/matching/src/main/kotlin/app/openstory/matching/AggregateRanking.kt core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt core/matching/src/test/kotlin/app/openstory/matching/AggregateRankingTest.kt
git commit -m "matching: deduplicate catalog stories and rank home"
```

### Task 4: Implement Home use cases and resilient multi-catalog refresh

**Files:**
- Create: feature/home/build.gradle.kts
- Create: feature/home/src/main/kotlin/app/openstory/home/domain/ObserveCombinedHome.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/model/HomeUiModel.kt
- Create: feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt

**Interfaces:**
- Consumes: Enabled catalog host facade, catalog repository, resolver/ranking, dispatchers, typed results.
- Produces: Use cases that refresh enabled catalogs independently, persist successful sections, and expose combined plus catalog-specific Home models.

**Acceptance:**
- One failing catalog does not erase or block successful sections.
- Refresh reports partial success and per-plugin stale timestamps.
- Cached Home is emitted before network refresh completes.
- Combined cards dedupe by canonical story ID while source-owned section labels remain visible.

**Implementation notes:**
- Use structured concurrency with `supervisorScope`; cap catalog concurrency to protect battery/network.
- Preserve last successful snapshot and show stale state rather than blanking the screen.
- Manual refresh and later background refresh call the same use case.

- [ ] **Step 1: Write the failing test**

Create `feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt`:

```kotlin
package app.openstory.home.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshHomeTest {
    @Test fun oneCatalogFailureStillPersistsSuccessfulCatalog() = runTest {
        val fixture = homeRefreshFixture(successful = setOf("a"), failing = setOf("b"))
        val report = fixture.useCase()
        assertEquals(setOf("a"), report.succeeded.map { it.value }.toSet())
        assertEquals(setOf("b"), report.failed.keys.map { it.value }.toSet())
        assertEquals(1, fixture.repository.savedSnapshots.size)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:home:test --tests app.openstory.home.domain.RefreshHomeTest.oneCatalogFailureStillPersistsSuccessfulCatalog
```

Expected: **FAIL** because Home refresh orchestration and partial-success report do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt`:

```kotlin
package app.openstory.home.domain

class RefreshHome(
    private val host: PluginHost,
    private val repository: CatalogRepository,
    private val dispatchers: AppDispatchers,
) {
    suspend operator fun invoke(): HomeRefreshReport = supervisorScope {
        host.enabledCatalogs().map { hosted ->
            async(dispatchers.io) { hosted.id to hosted.instance.home(defaultHomeRequest()) }
        }.awaitAll().fold(HomeRefreshReport()) { report, (id, result) ->
            report.record(id, result.tapSuccess { repository.ingest(id, it.toSnapshot()) })
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:home:test --tests app.openstory.home.domain.RefreshHomeTest.oneCatalogFailureStillPersistsSuccessfulCatalog
./gradlew :feature:home:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/home/build.gradle.kts feature/home/src/main/kotlin/app/openstory/home/domain/ObserveCombinedHome.kt feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt feature/home/src/main/kotlin/app/openstory/home/model/HomeUiModel.kt feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt
git commit -m "home: refresh catalogs independently"
```

### Task 5: Build combined and catalog-specific Compose Home screens

**Files:**
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeRoute.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeScreen.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/CatalogHomeScreen.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeCard.kt
- Test: feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt
- Test: feature/home/src/androidTest/kotlin/app/openstory/home/ui/HomeScreenTest.kt

**Interfaces:**
- Consumes: Home use cases/UI model, image loader adapter, app navigation route.
- Produces: Accessible Home UI with combined sections, per-catalog selection, loading/stale/error states, and canonical story navigation.

**Acceptance:**
- First cached content is visible while refresh runs.
- Partial failures show non-blocking source-specific status.
- Cards identify score source/scale and content type.
- Semantics expose story title, source section, and score without relying on color.

**Implementation notes:**
- Use lazy rows/columns with stable keys from canonical story IDs.
- Avoid nested uncontrolled scrolling; section rows use fixed card dimensions.
- Expose a source switcher/dropdown and a dedicated route for catalog-specific Home.

- [ ] **Step 1: Write the failing test**

Create `feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt`:

```kotlin
package app.openstory.home.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class HomeViewModelTest {
    @Test fun cachedSectionsRemainVisibleDuringRefresh() = runTest {
        val fixture = homeViewModelFixtureWithCachedSection()
        fixture.viewModel.state.test {
            assertTrue(awaitItem().sections.isNotEmpty())
            fixture.viewModel.refresh()
            val refreshing = awaitItem()
            assertTrue(refreshing.refreshing && refreshing.sections.isNotEmpty())
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:home:test --tests app.openstory.home.ui.HomeViewModelTest.cachedSectionsRemainVisibleDuringRefresh
```

Expected: **FAIL** because Home ViewModel and state behavior are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt`:

```kotlin
package app.openstory.home.ui

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHome: ObserveCombinedHome,
    private val refreshHome: RefreshHome,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    val state = combine(observeHome(), refreshing) { home, busy -> home.copy(refreshing = busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiModel.Empty)
    fun refresh() = viewModelScope.launch { refreshing.value = true; try { refreshHome() } finally { refreshing.value = false } }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:home:test --tests app.openstory.home.ui.HomeViewModelTest.cachedSectionsRemainVisibleDuringRefresh
./gradlew :feature:home:test :feature:home:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/home/src/main/kotlin/app/openstory/home/ui/HomeRoute.kt feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt feature/home/src/main/kotlin/app/openstory/home/ui/HomeScreen.kt feature/home/src/main/kotlin/app/openstory/home/ui/CatalogHomeScreen.kt feature/home/src/main/kotlin/app/openstory/home/ui/HomeCard.kt feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt feature/home/src/androidTest/kotlin/app/openstory/home/ui/HomeScreenTest.kt
git commit -m "home: add combined and per-catalog compose screens"
```

### Task 6: Implement catalog search, filter definitions, and story detail metadata

**Files:**
- Create: feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/SearchViewModel.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt
- Create: feature/story/build.gradle.kts
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailViewModel.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailScreen.kt
- Test: feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt
- Test: feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt

**Interfaces:**
- Consumes: Catalog plugin search/filter contracts, ingestion repository, canonical resolver, Navigation route.
- Produces: Debounced multi-catalog search with source filters and story detail that keeps catalog-specific ratings/descriptions distinct.

**Acceptance:**
- Blank queries do not invoke plugins.
- Search cancellation prevents stale results replacing newer query results.
- Duplicate catalog results link to one canonical card but retain source badges.
- Story detail renders each catalog score with its own scale and timestamp.

**Implementation notes:**
- Persist recent searches locally only after a later explicit privacy decision; MVP can keep them in memory.
- Translate plugin filter definitions into host widgets; unknown filter types are ignored with diagnostics rather than crashes.
- Story detail Add-to-Library control is introduced in Wave 06, not implemented against catalog plugin directly.

- [ ] **Step 1: Write the failing test**

Create `feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt`:

```kotlin
package app.openstory.home.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCatalogsTest {
    @Test fun lateOldQueryCannotReplaceNewQuery() = runTest {
        val fixture = searchFixture(oldDelayMs = 1_000, newDelayMs = 10)
        fixture.controller.submit("old")
        fixture.controller.submit("new")
        fixture.advanceUntilIdle()
        assertEquals("new", fixture.controller.state.value.query)
        assertEquals(listOf("new-result"), fixture.controller.state.value.results.map { it.title })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:home:test --tests app.openstory.home.domain.SearchCatalogsTest.lateOldQueryCannotReplaceNewQuery
```

Expected: **FAIL** because debounced/cancellable search orchestration is missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt`:

```kotlin
package app.openstory.home.domain

class SearchCatalogs(
    private val host: PluginHost,
    private val resolver: CatalogStoryResolver,
) {
    fun results(queries: Flow<SearchRequest>): Flow<SearchResultPage> = queries
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { request ->
            if (request.query.isBlank()) flowOf(SearchResultPage.Empty)
            else flow { emit(searchEnabledCatalogs(request).deduplicateWith(resolver)) }
        }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:home:test --tests app.openstory.home.domain.SearchCatalogsTest.lateOldQueryCannotReplaceNewQuery
./gradlew :feature:home:test :feature:story:test :feature:home:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt feature/home/src/main/kotlin/app/openstory/home/ui/SearchViewModel.kt feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt feature/story/build.gradle.kts feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailViewModel.kt feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailScreen.kt feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt
git commit -m "catalog: add search filters and metadata detail"
```

## Wave Checkpoint

Do not begin `2026-08-03-06-library-and-story-matching.md` until every item below is demonstrated on a clean checkout:

- [ ] A clean install shows cached/fixture Home from the bundled plugin.
- [ ] Disabling one catalog removes its sections without deleting canonical stories.
- [ ] Two catalog records for the same work show one card and separate scores.
- [ ] Failure of one catalog does not blank successful sections.
- [ ] Search cancellation and accessibility UI tests pass.

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
