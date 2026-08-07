<!--
DOCUMENT LIFECYCLE
Status: PLANNED / NOT STARTED IN THIS SNAPSHOT
Current repository note: Start only after the Wave 06 checkpoint is accepted.
Canonical execution status: ../../project/current-state.md
Original planning text below is preserved rather than retroactively rewritten.
-->

# Wave 07 — Chapter Sync and Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize releases from multiple content plugins and group equivalent releases under stable canonical chapters without duplicate unread counts or unsafe merges.

**Architecture:** A pure parser/scorer/aggregation engine emits deterministic mutation plans. Sync adapters fetch recent/full/incremental source snapshots, and Room commits releases, canonical groups, overrides, cursors, and change events atomically. The story UI renders one chapter with multiple selectable releases.

**Tech Stack:** Kotlin/BigDecimal, Room, coroutines, plugin host/content API, Compose, deterministic fixtures.

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

This is the product-defining wave. It replaces “primary source per story” with chapter-level aggregation and establishes the event semantics later used by reader progress and notifications.

## Entry Dependencies

- Wave 06 checkpoint is approved.
- At least two deterministic content fixture plugins map to the same canonical story.
- User-protected content mappings are available.

## Exit Deliverables

- Chapter parser and equivalence scorer.
- Deterministic aggregation engine and overrides.
- Fast/full/incremental sync.
- Transactional chapter/release/event persistence.
- MangaDex-style chapter/release list and correction UI.
- Canonical unread semantics.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Parse and normalize numbered and special chapter labels

**Files:**
- Create: core/aggregation/build.gradle.kts
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterLabelParser.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/NormalizedChapterKey.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterSortKey.kt
- Test: core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterLabelParserTest.kt

**Interfaces:**
- Consumes: Raw source chapter title/number/volume hints and canonical ChapterKind.
- Produces: Locale-independent parser producing semantic kind, decimal chapter/volume components, normalized title tokens, and stable sort key with confidence/evidence.

**Acceptance:**
- `Chapter 010` and `Ch. 10` normalize to numeric 10.
- `10.5` remains decimal and sorts between 10 and 11.
- Prologue, epilogue, side story, extra, and unknown remain semantically distinct.
- Parser never invents a numeric value from unrelated digits such as years in titles.

**Implementation notes:**
- Use `BigDecimal` created from normalized strings, never `Double`, for chapter/volume numbers.
- Maintain language-aware keyword tables in data files with exact tests for Vietnamese/English initially.
- Return parsing evidence such as matched token/range so diagnostics and correction UI can explain decisions.

- [ ] **Step 1: Write the failing test**

Create `core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterLabelParserTest.kt`:

```kotlin
package app.openstory.aggregation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterLabelParserTest {
    @Test fun parsesNumberedDecimalAndSpecialLabels() {
        val parser = ChapterLabelParser()
        assertEquals("10", parser.parse("Chapter 010", null, null).chapterNumber?.toPlainString())
        assertEquals("10.5", parser.parse("Ch. 10.5", null, null).chapterNumber?.toPlainString())
        assertEquals(ChapterKind.PROLOGUE, parser.parse("Prologue", null, null).kind)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:aggregation:test --tests app.openstory.aggregation.ChapterLabelParserTest.parsesNumberedDecimalAndSpecialLabels
```

Expected: **FAIL** because the chapter parser and normalized key are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/aggregation/src/main/kotlin/app/openstory/aggregation/NormalizedChapterKey.kt`:

```kotlin
package app.openstory.aggregation

import app.openstory.model.ChapterKind
import java.math.BigDecimal

data class NormalizedChapterKey(
    val kind: ChapterKind,
    val volumeNumber: BigDecimal?,
    val chapterNumber: BigDecimal?,
    val titleTokens: Set<String>,
    val parserConfidence: Double,
) {
    init { require(parserConfidence in 0.0..1.0) }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:aggregation:test --tests app.openstory.aggregation.ChapterLabelParserTest.parsesNumberedDecimalAndSpecialLabels
./gradlew :core:aggregation:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/aggregation/build.gradle.kts core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterLabelParser.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/NormalizedChapterKey.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterSortKey.kt core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterLabelParserTest.kt
git commit -m "aggregation: parse chapter labels deterministically"
```

### Task 2: Score whether two releases represent the same canonical chapter

**Files:**
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchScorer.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchPolicy.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchResult.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/TextFingerprint.kt
- Test: core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterMatchScorerTest.kt

**Interfaces:**
- Consumes: Normalized chapter keys, raw release metadata, optional plugin-provided mapping and content fingerprint.
- Produces: Explainable MATCH/REVIEW/SEPARATE decision using exact mapping, kind/number/volume, title tokens, list order, publish proximity, and optional body fingerprint.

**Acceptance:**
- Different explicit numeric chapter numbers never auto-merge.
- Exact plugin mapping can match despite title differences unless content type/story mapping conflicts.
- Special chapters require stronger title/order evidence than numbered chapters.
- Fingerprint is supporting evidence, not required and not computed by downloading every body.

**Implementation notes:**
- Version the match policy and persist version with automatic grouping decisions.
- Content fingerprints use normalized first/last text windows and a cryptographic hash; never store full body in match explanation.
- List-order evidence is source-relative and low weight because sources may omit/reorder chapters.

- [ ] **Step 1: Write the failing test**

Create `core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterMatchScorerTest.kt`:

```kotlin
package app.openstory.aggregation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterMatchScorerTest {
    @Test fun differentExplicitNumbersStaySeparate() {
        val scorer = ChapterMatchScorer(defaultChapterMatchPolicy())
        val result = scorer.compare(releaseKey(chapter = "100"), releaseKey(chapter = "101"))
        assertEquals(ChapterMatchDecision.SEPARATE, result.decision)
        assertEquals("explicit_number_conflict", result.primaryReason)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:aggregation:test --tests app.openstory.aggregation.ChapterMatchScorerTest.differentExplicitNumbersStaySeparate
```

Expected: **FAIL** because chapter equivalence scoring and hard conflicts are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchScorer.kt`:

```kotlin
package app.openstory.aggregation

class ChapterMatchScorer(private val policy: ChapterMatchPolicy) {
    fun compare(left: ReleaseKey, right: ReleaseKey): ChapterMatchResult {
        if (left.explicitChapter != null && right.explicitChapter != null && left.explicitChapter != right.explicitChapter)
            return ChapterMatchResult(0.0, ChapterMatchDecision.SEPARATE, "explicit_number_conflict")
        if (left.kind != right.kind && left.kind.isSpecial && right.kind.isSpecial)
            return ChapterMatchResult(0.0, ChapterMatchDecision.SEPARATE, "special_kind_conflict")
        val score = policy.numberScore(left, right) + policy.volumeScore(left, right) +
            policy.titleScore(left, right) + policy.orderScore(left, right) + policy.fingerprintScore(left, right)
        return when {
            score >= policy.matchAt -> ChapterMatchResult(score, ChapterMatchDecision.MATCH, "weighted_match")
            score >= policy.reviewAt -> ChapterMatchResult(score, ChapterMatchDecision.REVIEW, "ambiguous")
            else -> ChapterMatchResult(score, ChapterMatchDecision.SEPARATE, "insufficient_evidence")
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:aggregation:test --tests app.openstory.aggregation.ChapterMatchScorerTest.differentExplicitNumbersStaySeparate
./gradlew :core:aggregation:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchScorer.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchPolicy.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterMatchResult.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/TextFingerprint.kt core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterMatchScorerTest.kt
git commit -m "aggregation: score equivalent chapter releases"
```

### Task 3: Build deterministic release grouping with persistent user overrides

**Files:**
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterAggregationEngine.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/AggregationOverride.kt
- Create: core/aggregation/src/main/kotlin/app/openstory/aggregation/AggregationPlan.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/ChapterOverrideRepository.kt
- Test: core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterAggregationEngineTest.kt

**Interfaces:**
- Consumes: Chapter parser/scorer, existing canonical chapters/releases, user split/merge overrides.
- Produces: Pure engine that assigns incoming releases to existing/new canonical chapters deterministically and emits an auditable database mutation plan.

**Acceptance:**
- User merge/split overrides run before automatic scoring and survive sync.
- Engine output is independent of input ordering.
- MATCH joins one group, REVIEW stays separate with review suggestion, and SEPARATE creates/retains distinct group.
- Canonical IDs remain stable when new equivalent releases arrive.

**Implementation notes:**
- Generate stable new chapter IDs from story ID + normalized semantic signature + collision suffix, not source/plugin ID.
- When ambiguity exists, prefer false negatives (separate groups) over destructive false positives.
- Store user override endpoints as source release IDs/signatures so they can be reapplied after canonical rebuild.

- [ ] **Step 1: Write the failing test**

Create `core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterAggregationEngineTest.kt`:

```kotlin
package app.openstory.aggregation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterAggregationEngineTest {
    @Test fun aggregationIsStableAcrossInputOrder() {
        val engine = ChapterAggregationEngine(ChapterLabelParser(), ChapterMatchScorer(defaultChapterMatchPolicy()))
        val releases = listOf(sourceRelease("a", "Chapter 10"), sourceRelease("b", "Ch. 10"), sourceRelease("c", "Chapter 11"))
        val first = engine.plan(existing = emptyList(), incoming = releases, overrides = emptyList())
        val second = engine.plan(existing = emptyList(), incoming = releases.reversed(), overrides = emptyList())
        assertEquals(first.canonicalSignatures(), second.canonicalSignatures())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:aggregation:test --tests app.openstory.aggregation.ChapterAggregationEngineTest.aggregationIsStableAcrossInputOrder
```

Expected: **FAIL** because there is no deterministic grouping engine or mutation plan.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/aggregation/src/main/kotlin/app/openstory/aggregation/AggregationPlan.kt`:

```kotlin
package app.openstory.aggregation

import app.openstory.model.*

data class AggregationPlan(
    val chaptersToCreate: List<CanonicalChapter>,
    val chaptersToUpdate: List<CanonicalChapter>,
    val releaseAssignments: Map<ReleaseId, ChapterId>,
    val reviewSuggestions: List<ChapterReviewSuggestion>,
    val unavailableReleaseIds: Set<ReleaseId>,
)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:aggregation:test --tests app.openstory.aggregation.ChapterAggregationEngineTest.aggregationIsStableAcrossInputOrder
./gradlew :core:aggregation:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/aggregation/src/main/kotlin/app/openstory/aggregation/ChapterAggregationEngine.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/AggregationOverride.kt core/aggregation/src/main/kotlin/app/openstory/aggregation/AggregationPlan.kt core/database/src/main/kotlin/app/openstory/database/repository/ChapterOverrideRepository.kt core/aggregation/src/test/kotlin/app/openstory/aggregation/ChapterAggregationEngineTest.kt
git commit -m "aggregation: group releases with stable overrides"
```

### Task 4: Implement recent, full, and incremental mapping synchronization

**Files:**
- Create: sync/build.gradle.kts
- Create: sync/src/main/kotlin/app/openstory/sync/ChapterSyncEngine.kt
- Create: sync/src/main/kotlin/app/openstory/sync/SyncMode.kt
- Create: sync/src/main/kotlin/app/openstory/sync/SourceSnapshotDiffer.kt
- Create: sync/src/main/kotlin/app/openstory/sync/SyncReport.kt
- Test: sync/src/test/kotlin/app/openstory/sync/ChapterSyncEngineTest.kt

**Interfaces:**
- Consumes: Content mappings, hosted content plugins, plugin sync contract, mapping repository sync state, aggregation engine.
- Produces: Idempotent source-mapping sync supporting FAST_LATEST, FULL_INITIAL, and INCREMENTAL modes with cursor/fingerprint fallback and transactional aggregation commit.

**Acceptance:**
- FAST_LATEST has a strict release limit and never marks unseen older releases deleted.
- FULL_INITIAL fetches complete list and establishes source fingerprint/cursor.
- INCREMENTAL applies upserts/tombstones only after validating source IDs and cursor.
- Fallback diff never deletes on partial/error response.
- Cursor advances only after database transaction succeeds.

**Implementation notes:**
- Pin hosted plugin version for the operation so activation changes do not mix contracts mid-sync.
- Bound mapping concurrency and release count/body size; chapter list sync does not fetch chapter bodies.
- Use plugin `Retry-After` data to report deferred retry rather than hot-looping.

- [ ] **Step 1: Write the failing test**

Create `sync/src/test/kotlin/app/openstory/sync/ChapterSyncEngineTest.kt`:

```kotlin
package app.openstory.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterSyncEngineTest {
    @Test fun failedCommitDoesNotAdvanceCursor() = runTest {
        val fixture = chapterSyncFixture(pluginCursor = "next", commitFails = true)
        fixture.engine.sync(fixture.mapping, SyncMode.INCREMENTAL)
        assertEquals("old", fixture.mappingRepository.currentCursor)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :sync:test --tests app.openstory.sync.ChapterSyncEngineTest.failedCommitDoesNotAdvanceCursor
```

Expected: **FAIL** because chapter sync modes and commit/cursor semantics are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `sync/src/main/kotlin/app/openstory/sync/ChapterSyncEngine.kt`:

```kotlin
package app.openstory.sync

class ChapterSyncEngine(
    private val host: PluginHost,
    private val mappings: ContentMappingRepository,
    private val chapters: ChapterRepository,
    private val aggregation: ChapterAggregationEngine,
) {
    suspend fun sync(mapping: ContentMapping, mode: SyncMode): SyncReport {
        val plugin = host.content(mapping.pluginId).instance
        val result = when (mode) {
            SyncMode.FAST_LATEST -> plugin.latest(mapping.sourceStoryId, limit = 20).map(SourceSyncPayload::fromLatest)
            SyncMode.FULL_INITIAL -> plugin.allChapters(mapping.sourceStoryId).map(SourceSyncPayload::fromFull)
            SyncMode.INCREMENTAL -> plugin.sync(mapping.sourceStoryId, mapping.syncCursor).map(SourceSyncPayload::fromDelta)
        }
        return when (result) {
            is AppResult.Failure -> SyncReport.failed(mapping.id, result.error)
            is AppResult.Success -> {
                val source = result.value
                val plan = aggregation.plan(chapters.current(mapping.storyId), source.toReleases(mapping), chapters.overrides(mapping.storyId))
                chapters.commitSync(mapping, source, plan)
            }
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :sync:test --tests app.openstory.sync.ChapterSyncEngineTest.failedCommitDoesNotAdvanceCursor
./gradlew :sync:test :core:aggregation:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add sync/build.gradle.kts sync/src/main/kotlin/app/openstory/sync/ChapterSyncEngine.kt sync/src/main/kotlin/app/openstory/sync/SyncMode.kt sync/src/main/kotlin/app/openstory/sync/SourceSnapshotDiffer.kt sync/src/main/kotlin/app/openstory/sync/SyncReport.kt sync/src/test/kotlin/app/openstory/sync/ChapterSyncEngineTest.kt
git commit -m "sync: add recent full and incremental chapter sync"
```

### Task 5: Persist aggregation plans, tombstones, and unread events transactionally

**Files:**
- Create: core/database/src/main/kotlin/app/openstory/database/repository/ChapterRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/RoomChapterRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/dao/ChapterSyncDao.kt
- Create: core/model/src/main/kotlin/app/openstory/model/ChapterChangeEvent.kt
- Test: core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomChapterRepositoryTest.kt

**Interfaces:**
- Consumes: Aggregation mutation plan, source sync payload/cursor, progress and chapter/release tables.
- Produces: Transactional sync commit that upserts releases/groups, marks source tombstones unavailable, preserves history, and records deduplicated chapter/release events.

**Acceptance:**
- A tombstone changes release availability but does not delete canonical chapter/progress/download row.
- New canonical chapter creates one NEW_CHAPTER event even if multiple releases arrive together.
- New preferred-language release for existing chapter creates NEW_PREFERRED_RELEASE event, not NEW_CHAPTER.
- Re-running identical sync creates no new events.

**Implementation notes:**
- Use a source snapshot/version token to reject stale concurrent commits.
- Maintain an availability history timestamp so temporary source outages can be distinguished from explicit tombstones.
- Unread state derives from canonical progress plus chapter ordering, not number of release rows.

- [ ] **Step 1: Write the failing test**

Create `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomChapterRepositoryTest.kt`:

```kotlin
package app.openstory.database.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomChapterRepositoryTest {
    @Test fun twoEquivalentReleasesCreateOneNewChapterEvent() = runTest {
        val fixture = chapterRepositoryFixture()
        fixture.commitEquivalentReleases(listOf("sourceA:100", "sourceB:100"))
        assertEquals(1, fixture.events().count { it.kind == ChapterChangeKind.NEW_CHAPTER })
        assertEquals(2, fixture.releases().size)
        assertEquals(1, fixture.chapters().size)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomChapterRepositoryTest
```

Expected: **FAIL** because aggregation plans and deduplicated change events are not persisted transactionally.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/database/src/main/kotlin/app/openstory/database/repository/ChapterRepository.kt`:

```kotlin
package app.openstory.database.repository

interface ChapterRepository {
    suspend fun current(storyId: StoryId): List<CanonicalChapterWithReleases>
    suspend fun overrides(storyId: StoryId): List<AggregationOverride>
    suspend fun commitSync(mapping: ContentMapping, source: SourceSyncPayload, plan: AggregationPlan): SyncReport
    fun observeStoryChapters(storyId: StoryId, filters: ChapterFilters): Flow<List<CanonicalChapterWithReleases>>
    fun observeUnconsumedEvents(): Flow<List<ChapterChangeEvent>>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomChapterRepositoryTest
./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/database/src/main/kotlin/app/openstory/database/repository/ChapterRepository.kt core/database/src/main/kotlin/app/openstory/database/repository/RoomChapterRepository.kt core/database/src/main/kotlin/app/openstory/database/dao/ChapterSyncDao.kt core/model/src/main/kotlin/app/openstory/model/ChapterChangeEvent.kt core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomChapterRepositoryTest.kt
git commit -m "database: commit chapter aggregation and change events"
```

### Task 6: Build story chapter list with release expansion, filters, and correction actions

**Files:**
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterListViewModel.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterList.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterReleaseRow.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterFiltersSheet.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/domain/CorrectChapterGrouping.kt
- Test: feature/story/src/test/kotlin/app/openstory/story/ui/chapters/ChapterListViewModelTest.kt
- Test: feature/story/src/androidTest/kotlin/app/openstory/story/ui/chapters/ChapterListTest.kt

**Interfaces:**
- Consumes: Observed canonical chapter graphs, language/plugin/group filters, progress, sync reports, override repository, reader route.
- Produces: MangaDex-style canonical chapter list where each chapter expands to selectable releases and supports pinned language, filtering, manual merge/split review, and sync status.

**Acceptance:**
- Collapsed row shows canonical label, unread/read state, and available release count.
- Expanded rows show language, plugin, group/uploader, publish/update time, availability, download state.
- Pinned language filters default display but never deletes/hides access to all releases permanently.
- Opening a release passes both canonical chapter ID and chosen release ID to Reader.
- User merge/split writes override then rebuilds affected groups transactionally.

**Implementation notes:**
- Use Paging only if measured chapter counts require it; start with Room Flow and lazy list using stable IDs.
- Show ambiguous review suggestions in a separate correction surface, not as silent auto-merges.
- Expose full sync and manual refresh actions with per-plugin progress/error details.

- [ ] **Step 1: Write the failing test**

Create `feature/story/src/test/kotlin/app/openstory/story/ui/chapters/ChapterListViewModelTest.kt`:

```kotlin
package app.openstory.story.ui.chapters

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterListViewModelTest {
    @Test fun newReleaseDoesNotIncreaseCanonicalUnreadCount() = runTest {
        val fixture = chapterListFixture(chapters = 3, releases = 3)
        fixture.viewModel.state.test {
            assertEquals(3, awaitItem().unreadChapterCount)
            fixture.addEquivalentReleaseToExistingChapter()
            assertEquals(3, awaitItem().unreadChapterCount)
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:story:test --tests app.openstory.story.ui.chapters.ChapterListViewModelTest.newReleaseDoesNotIncreaseCanonicalUnreadCount
```

Expected: **FAIL** because chapter/release UI state and canonical unread semantics do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterReleaseRow.kt`:

```kotlin
package app.openstory.story.ui.chapters

@Composable
fun ChapterReleaseRow(model: ReleaseRowModel, onOpen: () -> Unit) {
    ListItem(
        headlineContent = { Text(model.languageDisplayName) },
        supportingContent = { Text(listOfNotNull(model.pluginName, model.groupName, model.timeLabel).joinToString(" · ")) },
        trailingContent = { if (model.downloaded) Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded") },
        modifier = Modifier.clickable(enabled = model.available, onClick = onOpen),
    )
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:story:test --tests app.openstory.story.ui.chapters.ChapterListViewModelTest.newReleaseDoesNotIncreaseCanonicalUnreadCount
./gradlew :feature:story:test :feature:story:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterListViewModel.kt feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterList.kt feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterReleaseRow.kt feature/story/src/main/kotlin/app/openstory/story/ui/chapters/ChapterFiltersSheet.kt feature/story/src/main/kotlin/app/openstory/story/domain/CorrectChapterGrouping.kt feature/story/src/test/kotlin/app/openstory/story/ui/chapters/ChapterListViewModelTest.kt feature/story/src/androidTest/kotlin/app/openstory/story/ui/chapters/ChapterListTest.kt
git commit -m "story: add aggregated chapter and release list"
```

## Wave Checkpoint

Do not begin `2026-08-03-08-reader-and-reading-progress.md` until every item below is demonstrated on a clean checkout:

- [ ] `10`, `10.5`, prologue, epilogue, extra, and conflicting-volume fixtures pass.
- [ ] Equivalent releases from two plugins produce one canonical chapter and two release rows.
- [ ] Ambiguous special chapters remain separate until user correction.
- [ ] Identical re-sync creates no duplicate chapter/event.
- [ ] Tombstoned release leaves progress/download/canonical chapter intact.

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
