<!--
DOCUMENT LIFECYCLE
Status: READY TO START / WAVE 05 CHECKPOINT ACCEPTED
Current repository note: The Wave 05 checkpoint is accepted; Wave 06 Task 01 may begin.
Canonical execution status: ../../project/current-state.md
Original planning text below is preserved rather than retroactively rewritten.
-->

# Wave 06 — Library and Story Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add stories instantly to a local Library and find/approve readable content mappings across installed source plugins.

**Architecture:** Library membership is a local transaction independent of network work. A deterministic, explainable matcher searches preferred plugins quickly, expands in the background, and persists automated or user-protected mappings. Compose UI exposes metadata-only, searching, linked, and review states.

**Tech Stack:** Room repositories, plugin host, matching algorithms, coroutines, Compose, Navigation, WorkManager scheduling boundary.

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

This wave connects discovery to readable sources without violating local-first immediacy. It establishes user override semantics before chapter data can arrive.

## Entry Dependencies

- Wave 05 checkpoint is approved.
- Catalog story detail and canonical IDs are stable.
- Content plugin search/details contracts execute through the secure host.

## Exit Deliverables

- Immediate metadata-only Library.
- Library list and statuses.
- Explainable story matcher.
- Staged content-source search.
- Protected mapping persistence.
- Manual mapping/URL import and review UI.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Implement immediate metadata-only Library commands

**Files:**
- Create: feature/library/build.gradle.kts
- Create: feature/library/src/main/kotlin/app/openstory/library/domain/AddStoryToLibrary.kt
- Create: feature/library/src/main/kotlin/app/openstory/library/domain/RemoveStoryFromLibrary.kt
- Create: feature/library/src/main/kotlin/app/openstory/library/domain/ChangeLibraryStatus.kt
- Create: feature/library/src/main/kotlin/app/openstory/library/domain/ObserveLibrary.kt
- Test: feature/library/src/test/kotlin/app/openstory/library/domain/AddStoryToLibraryTest.kt

**Interfaces:**
- Consumes: Catalog story detail, local story repository, clock, library status types.
- Produces: Library commands that persist canonical metadata and user status immediately without invoking content plugins.

**Acceptance:**
- Add returns after one local transaction.
- Adding the same story is idempotent and preserves progress/status unless explicitly changed.
- Remove from Library does not delete canonical catalog metadata immediately and never deletes explicit downloads without confirmation.
- Metadata-only entries are valid first-class states.

**Implementation notes:**
- Content matching is triggered after successful local add through an explicit coordinator in Task 4, never inside the transaction.
- Keep delete semantics separate: remove membership, delete cached data, delete downloads, and purge all metadata are different actions.
- Emit library changes through Room Flow so Home/story detail controls update immediately.

- [ ] **Step 1: Write the failing test**

Create `feature/library/src/test/kotlin/app/openstory/library/domain/AddStoryToLibraryTest.kt`:

```kotlin
package app.openstory.library.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AddStoryToLibraryTest {
    @Test fun addDoesNotWaitForContentSearch() = runTest {
        val fixture = addLibraryFixture(contentSearchWouldSuspend = true)
        val result = fixture.useCase(fixture.story, LibraryStatus.WANT_TO_READ)
        assertEquals(Unit, result.value())
        assertEquals(1, fixture.localRepository.libraryWrites)
        assertEquals(0, fixture.contentHostCalls)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:library:test --tests app.openstory.library.domain.AddStoryToLibraryTest.addDoesNotWaitForContentSearch
```

Expected: **FAIL** because Library commands and isolation from content search do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/library/src/main/kotlin/app/openstory/library/domain/AddStoryToLibrary.kt`:

```kotlin
package app.openstory.library.domain

class AddStoryToLibrary(
    private val repository: LocalStoryRepository,
) {
    suspend operator fun invoke(story: CanonicalStory, status: LibraryStatus): AppResult<Unit> =
        repository.addToLibrary(story, status)
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:library:test --tests app.openstory.library.domain.AddStoryToLibraryTest.addDoesNotWaitForContentSearch
./gradlew :feature:library:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/library/build.gradle.kts feature/library/src/main/kotlin/app/openstory/library/domain/AddStoryToLibrary.kt feature/library/src/main/kotlin/app/openstory/library/domain/RemoveStoryFromLibrary.kt feature/library/src/main/kotlin/app/openstory/library/domain/ChangeLibraryStatus.kt feature/library/src/main/kotlin/app/openstory/library/domain/ObserveLibrary.kt feature/library/src/test/kotlin/app/openstory/library/domain/AddStoryToLibraryTest.kt
git commit -m "library: add immediate metadata-only commands"
```

### Task 2: Build Library list, filters, statuses, and metadata-only states

**Files:**
- Create: feature/library/src/main/kotlin/app/openstory/library/ui/LibraryViewModel.kt
- Create: feature/library/src/main/kotlin/app/openstory/library/ui/LibraryScreen.kt
- Create: feature/library/src/main/kotlin/app/openstory/library/ui/LibraryItem.kt
- Create: feature/library/src/main/kotlin/app/openstory/library/model/LibraryUiState.kt
- Test: feature/library/src/test/kotlin/app/openstory/library/ui/LibraryViewModelTest.kt
- Test: feature/library/src/androidTest/kotlin/app/openstory/library/ui/LibraryScreenTest.kt

**Interfaces:**
- Consumes: Library use cases, story/catalog display metadata, Navigation story route.
- Produces: Compose Library with reading-status filters, local sorting, unread placeholders, and clear mapping/sync state.

**Acceptance:**
- Metadata-only story displays “finding sources” or “no source linked”, not an error card.
- Status/filter/sort changes are local and immediate.
- Stable item keys use StoryId.
- Accessibility semantics include title, status, source state, and last-read information.

**Implementation notes:**
- Do not display a fabricated unread count before chapter aggregation exists.
- Use a single query or repository projection for list rows; avoid one DAO call per story.
- Preserve scroll/filter state through saved state.

- [ ] **Step 1: Write the failing test**

Create `feature/library/src/test/kotlin/app/openstory/library/ui/LibraryViewModelTest.kt`:

```kotlin
package app.openstory.library.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryViewModelTest {
    @Test fun metadataOnlyEntryRemainsVisible() = runTest {
        val fixture = libraryViewModelFixture(mappingCount = 0)
        fixture.viewModel.state.test {
            val item = awaitItem().items.single()
            assertEquals(SourceState.NO_MAPPING, item.sourceState)
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:library:test --tests app.openstory.library.ui.LibraryViewModelTest.metadataOnlyEntryRemainsVisible
```

Expected: **FAIL** because Library UI state does not represent metadata-only entries.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/library/src/main/kotlin/app/openstory/library/model/LibraryUiState.kt`:

```kotlin
package app.openstory.library.model

data class LibraryUiState(
    val items: List<LibraryItemModel> = emptyList(),
    val selectedStatus: LibraryStatus? = null,
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
)

enum class SourceState { SEARCHING, LINKED, NO_MAPPING, ERROR }
enum class LibrarySort { LAST_ACTIVITY, TITLE, DATE_ADDED, LAST_READ }
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:library:test --tests app.openstory.library.ui.LibraryViewModelTest.metadataOnlyEntryRemainsVisible
./gradlew :feature:library:test :feature:library:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/library/src/main/kotlin/app/openstory/library/ui/LibraryViewModel.kt feature/library/src/main/kotlin/app/openstory/library/ui/LibraryScreen.kt feature/library/src/main/kotlin/app/openstory/library/ui/LibraryItem.kt feature/library/src/main/kotlin/app/openstory/library/model/LibraryUiState.kt feature/library/src/test/kotlin/app/openstory/library/ui/LibraryViewModelTest.kt feature/library/src/androidTest/kotlin/app/openstory/library/ui/LibraryScreenTest.kt
git commit -m "library: add local list and source states"
```

### Task 3: Implement explainable story-match feature extraction and scoring

**Files:**
- Create: core/matching/src/main/kotlin/app/openstory/matching/story/StoryFeatures.kt
- Create: core/matching/src/main/kotlin/app/openstory/matching/story/StoryMatchScorer.kt
- Create: core/matching/src/main/kotlin/app/openstory/matching/story/StoryMatchDecision.kt
- Create: core/matching/src/main/kotlin/app/openstory/matching/story/MatchExplanation.kt
- Test: core/matching/src/test/kotlin/app/openstory/matching/story/StoryMatchScorerTest.kt

**Interfaces:**
- Consumes: Canonical/catalog metadata, content plugin candidates, title normalizer.
- Produces: Deterministic score and explanation using exact/direct mappings, title/alias similarity, author overlap, content type, year, description tokens, and optional chapter count.

**Acceptance:**
- Direct trusted plugin/catalog mapping is highest-priority evidence but still validates content type.
- Title-only match cannot auto-link when authors conflict.
- Missing optional fields do not count as negative evidence.
- Thresholds produce AUTO_LINK, REVIEW, or REJECT with component scores.

**Implementation notes:**
- Put weights/thresholds in a versioned policy object and store policy version with automated mappings.
- Use token/Jaro-Winkler-like comparison implemented deterministically; avoid ML/personalization in MVP.
- Persist the top rejected/review candidates only within bounded history for user review/diagnostics.

- [ ] **Step 1: Write the failing test**

Create `core/matching/src/test/kotlin/app/openstory/matching/story/StoryMatchScorerTest.kt`:

```kotlin
package app.openstory.matching.story

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryMatchScorerTest {
    @Test fun titleOnlyWithConflictingAuthorRequiresReview() {
        val scorer = StoryMatchScorer(defaultStoryMatchPolicy())
        val result = scorer.score(
            canonicalFeatures(title = "The Return", authors = setOf("A")),
            sourceFeatures(title = "The Return", authors = setOf("B")),
        )
        assertEquals(StoryMatchDecision.REVIEW, result.decision)
        assertEquals(true, result.explanation.authorConflict)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:matching:test --tests app.openstory.matching.story.StoryMatchScorerTest.titleOnlyWithConflictingAuthorRequiresReview
```

Expected: **FAIL** because story matching score, thresholds, and explanations are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/matching/src/main/kotlin/app/openstory/matching/story/StoryMatchScorer.kt`:

```kotlin
package app.openstory.matching.story

class StoryMatchScorer(private val policy: StoryMatchPolicy) {
    fun score(canonical: StoryFeatures, source: StoryFeatures): StoryMatchResult {
        val title = policy.titleSimilarity(canonical.allTitles, source.allTitles)
        val authors = policy.authorOverlap(canonical.authors, source.authors)
        val conflict = canonical.authors.isNotEmpty() && source.authors.isNotEmpty() && authors == 0.0
        val total = title * 0.60 + authors * 0.20 + policy.typeScore(canonical, source) * 0.15 + policy.yearScore(canonical, source) * 0.05
        val decision = when {
            conflict || total < policy.rejectBelow -> StoryMatchDecision.REVIEW
            total >= policy.autoLinkAt -> StoryMatchDecision.AUTO_LINK
            else -> StoryMatchDecision.REVIEW
        }
        return StoryMatchResult(total, decision, MatchExplanation(title, authors, conflict))
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:matching:test --tests app.openstory.matching.story.StoryMatchScorerTest.titleOnlyWithConflictingAuthorRequiresReview
./gradlew :core:matching:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/matching/src/main/kotlin/app/openstory/matching/story/StoryFeatures.kt core/matching/src/main/kotlin/app/openstory/matching/story/StoryMatchScorer.kt core/matching/src/main/kotlin/app/openstory/matching/story/StoryMatchDecision.kt core/matching/src/main/kotlin/app/openstory/matching/story/MatchExplanation.kt core/matching/src/test/kotlin/app/openstory/matching/story/StoryMatchScorerTest.kt
git commit -m "matching: score catalog stories against content sources"
```

### Task 4: Orchestrate fast preferred-plugin search followed by bounded background expansion

**Files:**
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/FindContentMappings.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/ContentSearchPlanner.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/MappingSearchReport.kt
- Create: sync/src/main/kotlin/app/openstory/sync/MappingSearchCoordinator.kt
- Test: feature/story/src/test/kotlin/app/openstory/story/domain/FindContentMappingsTest.kt

**Interfaces:**
- Consumes: Enabled content plugin host, story match scorer, mapping repository, language/plugin preferences.
- Produces: A two-stage mapping search: quick search on preferred plugins for immediate results, then resumable bounded search over remaining enabled sources.

**Acceptance:**
- Library add schedules mapping search after local persistence.
- Quick stage has a strict deadline and does not wait for all plugins.
- Auto-link only stores high-confidence mapping; review candidates remain unlinked.
- Failure/timeout of one plugin does not cancel others.
- Installing/enabling a new content plugin can re-run search for metadata-only stories.

**Implementation notes:**
- Use plugin search query variants from preferred title and aliases, deduplicated and capped.
- Record last search time/plugin version so unchanged failed searches are not repeated excessively.
- The deferred coordinator may use WorkManager later, but its pure planning/use case is tested here.

- [ ] **Step 1: Write the failing test**

Create `feature/story/src/test/kotlin/app/openstory/story/domain/FindContentMappingsTest.kt`:

```kotlin
package app.openstory.story.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FindContentMappingsTest {
    @Test fun quickStageReturnsBeforeSlowPlugins() = runTest {
        val fixture = mappingSearchFixture(preferredDelay = 10, otherDelay = 10_000)
        val quick = fixture.useCase.quick(fixture.story)
        assertEquals(setOf("preferred"), quick.searchedPluginIds.map { it.value }.toSet())
        assertEquals(1, quick.autoLinked.size)
        assertEquals(false, fixture.otherCompleted)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:story:test --tests app.openstory.story.domain.FindContentMappingsTest.quickStageReturnsBeforeSlowPlugins
```

Expected: **FAIL** because mapping search planner and staged orchestration do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/story/src/main/kotlin/app/openstory/story/domain/ContentSearchPlanner.kt`:

```kotlin
package app.openstory.story.domain

class ContentSearchPlanner {
    fun plan(enabled: List<HostedPlugin<ContentPlugin>>, preferences: SourcePreferences): SearchPlan {
        val ordered = enabled.sortedWith(compareBy({ preferences.pluginRank(it.id) }, { it.id.value }))
        return SearchPlan(quick = ordered.take(preferences.quickPluginCount), deferred = ordered.drop(preferences.quickPluginCount))
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:story:test --tests app.openstory.story.domain.FindContentMappingsTest.quickStageReturnsBeforeSlowPlugins
./gradlew :feature:story:test :sync:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/story/src/main/kotlin/app/openstory/story/domain/FindContentMappings.kt feature/story/src/main/kotlin/app/openstory/story/domain/ContentSearchPlanner.kt feature/story/src/main/kotlin/app/openstory/story/domain/MappingSearchReport.kt sync/src/main/kotlin/app/openstory/sync/MappingSearchCoordinator.kt feature/story/src/test/kotlin/app/openstory/story/domain/FindContentMappingsTest.kt
git commit -m "story: find content mappings in staged search"
```

### Task 5: Persist user-approved mappings and protect them from automation

**Files:**
- Create: core/database/src/main/kotlin/app/openstory/database/repository/ContentMappingRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/RoomContentMappingRepository.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/ApproveContentMapping.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/RejectContentCandidate.kt
- Test: core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomContentMappingRepositoryTest.kt

**Interfaces:**
- Consumes: Content mapping domain/entity, match decisions/explanations, plugin/source IDs.
- Produces: Mapping repository that records automated/plugin/user origins, confidence, user lock, rejection fingerprints, and sync cursor independently per plugin/source story.

**Acceptance:**
- User-approved mapping is `USER_CONFIRMED` and cannot be replaced by later automated search.
- User rejection suppresses the same candidate/policy version but can be revisited after meaningful metadata change.
- One story may have multiple mappings across plugins and languages.
- Disabling plugin marks mapping unavailable without deleting it.

**Implementation notes:**
- Use a transaction/upsert policy that checks current origin and user lock before mutation.
- Store source story URL as display/navigation data, not identity.
- Keep last success/error/sync cursor per mapping for chapter sync and diagnostics.

- [ ] **Step 1: Write the failing test**

Create `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomContentMappingRepositoryTest.kt`:

```kotlin
package app.openstory.database.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomContentMappingRepositoryTest {
    @Test fun automaticUpsertCannotReplaceUserLockedMapping() = runTest {
        val fixture = mappingRepositoryFixture()
        fixture.repository.save(fixture.userConfirmed(sourceStoryId = "chosen"))
        fixture.repository.save(fixture.automatic(sourceStoryId = "other", confidence = 0.99))
        assertEquals("chosen", fixture.repository.mappingsFor(fixture.storyId).single().sourceStoryId)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomContentMappingRepositoryTest
```

Expected: **FAIL** because mapping conflict/user-lock persistence is not implemented.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/database/src/main/kotlin/app/openstory/database/repository/ContentMappingRepository.kt`:

```kotlin
package app.openstory.database.repository

interface ContentMappingRepository {
    suspend fun save(mapping: ContentMapping): AppResult<Unit>
    suspend fun mappingsFor(storyId: StoryId): List<ContentMapping>
    suspend fun updateSyncState(id: ContentMappingId, cursor: String?, fingerprint: String?, successAt: Long): AppResult<Unit>
    suspend fun markUnavailableForPlugin(pluginId: PluginId): AppResult<Unit>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomContentMappingRepositoryTest
./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/database/src/main/kotlin/app/openstory/database/repository/ContentMappingRepository.kt core/database/src/main/kotlin/app/openstory/database/repository/RoomContentMappingRepository.kt feature/story/src/main/kotlin/app/openstory/story/domain/ApproveContentMapping.kt feature/story/src/main/kotlin/app/openstory/story/domain/RejectContentCandidate.kt core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomContentMappingRepositoryTest.kt
git commit -m "database: persist protected content mappings"
```

### Task 6: Add mapping review, manual search, and URL import UI

**Files:**
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/mapping/MappingViewModel.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/mapping/MappingSheet.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/mapping/ManualSourceSearchScreen.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/ResolveSourceUrl.kt
- Create: feature/story/src/test/kotlin/app/openstory/story/ui/mapping/MappingViewModelTest.kt
- Create: feature/story/src/androidTest/kotlin/app/openstory/story/ui/mapping/MappingSheetTest.kt

**Interfaces:**
- Consumes: Mapping search/report, content plugin URL-recognition capability, approval/rejection commands.
- Produces: User-facing candidate explanations, approve/reject actions, manual per-plugin search, and pasted URL resolution.

**Acceptance:**
- UI shows plugin, language, source title, author, confidence components, and reason for review.
- Pasted URL is offered only to plugins whose declared hosts match it.
- A plugin may resolve a recognized URL to source story ID; the host still validates returned host/ID.
- Approve action immediately reflects in story source state and schedules chapter sync.

**Implementation notes:**
- Never send a pasted URL to unrelated plugins.
- Confidence is explanatory, not a misleading percentage guarantee; label as strong/possible/weak plus details.
- Allow unlink and remap without deleting chapters/progress until aggregation cleanup policy runs.

- [ ] **Step 1: Write the failing test**

Create `feature/story/src/test/kotlin/app/openstory/story/ui/mapping/MappingViewModelTest.kt`:

```kotlin
package app.openstory.story.ui.mapping

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MappingViewModelTest {
    @Test fun approvalPersistsAndSchedulesInitialSync() = runTest {
        val fixture = mappingViewModelFixture()
        fixture.viewModel.approve(fixture.candidate.id)
        fixture.advanceUntilIdle()
        assertEquals(1, fixture.approveCalls)
        assertEquals(listOf(fixture.mappingId), fixture.scheduledInitialSyncs)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:story:test --tests app.openstory.story.ui.mapping.MappingViewModelTest.approvalPersistsAndSchedulesInitialSync
```

Expected: **FAIL** because mapping review/approval UI orchestration is missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/story/src/main/kotlin/app/openstory/story/domain/ResolveSourceUrl.kt`:

```kotlin
package app.openstory.story.domain

class ResolveSourceUrl(private val host: PluginHost) {
    suspend operator fun invoke(url: String): List<UrlResolution> {
        val parsed = requireHttpsUrl(url)
        return host.enabledContentSources()
            .filter { parsed.host in it.manifest.allowedHosts }
            .mapNotNull { plugin -> plugin.instance.resolveStoryUrl(parsed.toString()).getOrNull()?.let { UrlResolution(plugin.id, it) } }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:story:test --tests app.openstory.story.ui.mapping.MappingViewModelTest.approvalPersistsAndSchedulesInitialSync
./gradlew :feature:story:test :feature:story:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/story/src/main/kotlin/app/openstory/story/ui/mapping/MappingViewModel.kt feature/story/src/main/kotlin/app/openstory/story/ui/mapping/MappingSheet.kt feature/story/src/main/kotlin/app/openstory/story/ui/mapping/ManualSourceSearchScreen.kt feature/story/src/main/kotlin/app/openstory/story/domain/ResolveSourceUrl.kt feature/story/src/test/kotlin/app/openstory/story/ui/mapping/MappingViewModelTest.kt feature/story/src/androidTest/kotlin/app/openstory/story/ui/mapping/MappingSheetTest.kt
git commit -m "story: add mapping review and url import"
```

## Wave Checkpoint

Do not begin `2026-08-03-07-chapter-sync-and-aggregation.md` until every item below is demonstrated on a clean checkout:

- [ ] Add-to-Library performs no network call before returning.
- [ ] Metadata-only stories remain usable and visible.
- [ ] High-confidence mapping auto-links; ambiguous mapping requires review.
- [ ] User-approved mapping survives repeated automated searches.
- [ ] Pasted URL reaches only matching declared-host plugins.

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
