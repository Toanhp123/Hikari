# Wave 08 — Reader and Reading Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a safe text reader that opens any selected release, chooses sensible defaults, switches sources, navigates canonical chapters, and restores exact progress.

**Architecture:** Plugin chapter DTOs are sanitized into a host-owned document model. A deterministic selection policy applies language then source continuity. The reader repository loads an exact release, a debounced progress controller persists canonical/exact state, and a process-restorable Compose state machine renders the document.

**Tech Stack:** Kotlin, Compose, Room/Flow, Navigation 3 saved state, plugin host, coroutines, DataStore preferences, Android UI tests.

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

This wave turns aggregated release data into the primary user value: reliable reading without hiding source choice or corrupting canonical progress.

## Entry Dependencies

- Wave 07 checkpoint is approved.
- Canonical chapters contain multiple fixture releases.
- Progress repository and reader route IDs are stable.

## Exit Deliverables

- Sanitized reader document model.
- Approved language/source release selection policy.
- Exact release loading with alternatives.
- Debounced canonical/exact progress persistence.
- Restorable Reader ViewModel.
- Accessible Compose reader and source switcher.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Validate and sanitize structured chapter documents

**Files:**
- Create: core/reader/build.gradle.kts
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReaderDocument.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/ChapterDocumentSanitizer.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/TextNormalizer.kt
- Test: core/reader/src/test/kotlin/app/openstory/reader/ChapterDocumentSanitizerTest.kt

**Interfaces:**
- Consumes: Plugin `ChapterDocument` wire DTO, safe image references, typed errors.
- Produces: Host-owned immutable `ReaderDocument` with bounded paragraph/heading/divider/note blocks, safe inline spans, normalized whitespace, and deterministic content fingerprint.

**Acceptance:**
- Script/style/form/iframe/event-handler content cannot enter the reader model.
- Empty/whitespace-only documents fail with `plugin.chapter_empty`.
- Block count, text length, nesting, and image count are bounded.
- Sanitization is deterministic for cache/integrity fingerprints.

**Implementation notes:**
- Prefer plugin structured blocks; legacy HTML conversion, if later added, must occur inside plugin host before sanitizer.
- Normalize Unicode to NFC/NFKC only where it does not alter intended prose; preserve paragraph boundaries.
- Compute fingerprint over canonical block types and normalized text, not source HTML.

- [ ] **Step 1: Write the failing test**

Create `core/reader/src/test/kotlin/app/openstory/reader/ChapterDocumentSanitizerTest.kt`:

```kotlin
package app.openstory.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterDocumentSanitizerTest {
    @Test fun sanitizerRejectsExecutableAndEmptyContent() {
        val sanitizer = ChapterDocumentSanitizer(defaultReaderLimits())
        val result = sanitizer.sanitize(pluginDocument(blocks = listOf(rawHtml("<script>x()</script>"))))
        assertEquals("plugin.chapter_empty", result.errorCode())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.ChapterDocumentSanitizerTest.sanitizerRejectsExecutableAndEmptyContent
```

Expected: **FAIL** because reader document model and sanitizer are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/reader/src/main/kotlin/app/openstory/reader/ReaderDocument.kt`:

```kotlin
package app.openstory.reader

data class ReaderDocument(
    val title: String?,
    val blocks: List<ReaderBlock>,
    val fingerprint: String,
)

sealed interface ReaderBlock {
    data class Paragraph(val text: AnnotatedReaderText) : ReaderBlock
    data class Heading(val level: Int, val text: AnnotatedReaderText) : ReaderBlock
    data object Divider : ReaderBlock
    data class Note(val text: AnnotatedReaderText) : ReaderBlock
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.ChapterDocumentSanitizerTest.sanitizerRejectsExecutableAndEmptyContent
./gradlew :core:reader:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/reader/build.gradle.kts core/reader/src/main/kotlin/app/openstory/reader/ReaderDocument.kt core/reader/src/main/kotlin/app/openstory/reader/ChapterDocumentSanitizer.kt core/reader/src/main/kotlin/app/openstory/reader/TextNormalizer.kt core/reader/src/test/kotlin/app/openstory/reader/ChapterDocumentSanitizerTest.kt
git commit -m "reader: sanitize structured chapter documents"
```

### Task 2: Implement default release selection from language and reading history

**Files:**
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReleaseSelectionPolicy.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReleasePreference.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReleaseSelectionExplanation.kt
- Test: core/reader/src/test/kotlin/app/openstory/reader/ReleaseSelectionPolicyTest.kt

**Interfaces:**
- Consumes: Canonical chapter release list, ordered language preferences, recent plugin/group selections, availability state.
- Produces: Deterministic policy implementing approved C+D behavior: language priority first, then continuity with previous plugin/group, then freshness/stable tie-breakers.

**Acceptance:**
- Pinned preferred language outranks prior plugin in another language.
- Within the same preferred language, previous plugin/group is preferred when available.
- Unavailable/disabled releases are excluded but remain visible in selection UI.
- Tie-breaking by plugin ID/release ID is stable.

**Implementation notes:**
- Store preferences globally plus per-story recent choice; per-story choice wins only within language priority.
- Expose explanation to UI/diagnostics such as “Vietnamese preferred” or “same source as previous chapter”.
- Never auto-change the already opened release because a newer alternative appears mid-session.

- [ ] **Step 1: Write the failing test**

Create `core/reader/src/test/kotlin/app/openstory/reader/ReleaseSelectionPolicyTest.kt`:

```kotlin
package app.openstory.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReleaseSelectionPolicyTest {
    @Test fun languagePriorityWinsBeforePreviousSource() {
        val policy = ReleaseSelectionPolicy()
        val selected = policy.select(
            releases = listOf(release("english-old", "en", plugin = "previous"), release("vietnamese", "vi", plugin = "other")),
            preference = ReleasePreference(languages = listOf("vi", "en"), previousPluginId = "previous", previousGroup = null),
        )
        assertEquals("vietnamese", selected!!.release.id.value)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.ReleaseSelectionPolicyTest.languagePriorityWinsBeforePreviousSource
```

Expected: **FAIL** because release-selection priorities are not implemented.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/reader/src/main/kotlin/app/openstory/reader/ReleaseSelectionPolicy.kt`:

```kotlin
package app.openstory.reader

class ReleaseSelectionPolicy {
    fun select(releases: List<SelectableRelease>, preference: ReleasePreference): SelectedRelease? = releases
        .asSequence()
        .filter { it.available }
        .sortedWith(compareBy<SelectableRelease>(
            { preference.languageRank(it.language) },
            { if (it.pluginId.value == preference.previousPluginId) 0 else 1 },
            { if (preference.previousGroup != null && it.group == preference.previousGroup) 0 else 1 },
            { -it.updatedAtEpochMillis },
            { it.id.value },
        ))
        .firstOrNull()
        ?.let { SelectedRelease(it, ReleaseSelectionExplanation.forChoice(it, preference)) }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.ReleaseSelectionPolicyTest.languagePriorityWinsBeforePreviousSource
./gradlew :core:reader:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/reader/src/main/kotlin/app/openstory/reader/ReleaseSelectionPolicy.kt core/reader/src/main/kotlin/app/openstory/reader/ReleasePreference.kt core/reader/src/main/kotlin/app/openstory/reader/ReleaseSelectionExplanation.kt core/reader/src/test/kotlin/app/openstory/reader/ReleaseSelectionPolicyTest.kt
git commit -m "reader: select default release by language and continuity"
```

### Task 3: Create reader content repository with source fallback boundaries

**Files:**
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReaderContentRepository.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/PluginReaderContentRepository.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReaderContentSource.kt
- Test: core/reader/src/test/kotlin/app/openstory/reader/PluginReaderContentRepositoryTest.kt

**Interfaces:**
- Consumes: Chapter/release repository, plugin host content contract, sanitizer, dispatcher/error primitives; cache adapter initially optional.
- Produces: Repository that resolves an exact release, fetches its document through the pinned plugin version, sanitizes it, and returns source/fingerprint metadata.

**Acceptance:**
- Requesting an exact release never silently returns another release.
- Unavailable exact release returns a typed error plus alternative release IDs for UI selection.
- Plugin failure does not modify progress.
- Document is sanitized before any caller sees it.

**Implementation notes:**
- Wave 09 will add cache/download sources ahead of plugin network; keep repository interface unchanged.
- Pin plugin version during fetch and record version with loaded document metadata.
- Do not prefetch neighboring chapter bodies until storage/quota logic exists.

- [ ] **Step 1: Write the failing test**

Create `core/reader/src/test/kotlin/app/openstory/reader/PluginReaderContentRepositoryTest.kt`:

```kotlin
package app.openstory.reader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginReaderContentRepositoryTest {
    @Test fun exactReleaseFailureReturnsAlternativesWithoutSwitching() = runTest {
        val fixture = readerRepositoryFixture(failingRelease = "a", alternatives = listOf("b"))
        val result = fixture.repository.load(fixture.chapterId, ReleaseId("a"))
        assertEquals("reader.release_unavailable", result.errorCode())
        assertEquals(listOf(ReleaseId("b")), result.alternativeReleaseIds())
        assertEquals(0, fixture.progressWrites)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.PluginReaderContentRepositoryTest.exactReleaseFailureReturnsAlternativesWithoutSwitching
```

Expected: **FAIL** because reader content loading/failure boundaries do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/reader/src/main/kotlin/app/openstory/reader/ReaderContentRepository.kt`:

```kotlin
package app.openstory.reader

interface ReaderContentRepository {
    suspend fun load(chapterId: ChapterId, releaseId: ReleaseId): AppResult<LoadedReaderDocument>
}

data class LoadedReaderDocument(
    val chapterId: ChapterId,
    val releaseId: ReleaseId,
    val document: ReaderDocument,
    val source: ReaderContentSource,
)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.PluginReaderContentRepositoryTest.exactReleaseFailureReturnsAlternativesWithoutSwitching
./gradlew :core:reader:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/reader/src/main/kotlin/app/openstory/reader/ReaderContentRepository.kt core/reader/src/main/kotlin/app/openstory/reader/PluginReaderContentRepository.kt core/reader/src/main/kotlin/app/openstory/reader/ReaderContentSource.kt core/reader/src/test/kotlin/app/openstory/reader/PluginReaderContentRepositoryTest.kt
git commit -m "reader: load exact sanitized chapter releases"
```

### Task 4: Persist debounced exact progress and canonical completion safely

**Files:**
- Create: core/reader/src/main/kotlin/app/openstory/reader/ReadingProgressController.kt
- Create: core/reader/src/main/kotlin/app/openstory/reader/ProgressWritePolicy.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/ReadingProgressRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/RoomReadingProgressRepository.kt
- Test: core/reader/src/test/kotlin/app/openstory/reader/ReadingProgressControllerTest.kt

**Interfaces:**
- Consumes: Progress domain model, Room repository, clock/test scheduler, reader block positions.
- Produces: Controller that debounces scroll writes, flushes on lifecycle/navigation, marks completion at a threshold, and preserves newer timestamps against stale sessions.

**Acceptance:**
- Scroll events do not write Room on every pixel/frame.
- Flush stores canonical chapter ID, exact release ID, paragraph index/fraction, and timestamp.
- Completion of one release marks canonical chapter completed.
- Switching release keeps canonical completion while storing new exact position.
- Older session flush cannot overwrite newer progress.

**Implementation notes:**
- Use a monotonic session sequence plus wall-clock timestamp to resolve same-device concurrent sessions.
- Completion threshold is policy-controlled (for example last paragraph + 90%); a manual mark-read action is explicit.
- Flush in `ViewModel.onCleared`, lifecycle stop, release switch, and chapter navigation.

- [ ] **Step 1: Write the failing test**

Create `core/reader/src/test/kotlin/app/openstory/reader/ReadingProgressControllerTest.kt`:

```kotlin
package app.openstory.reader

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingProgressControllerTest {
    @Test fun rapidPositionsCoalesceAndFlushLatest() = runTest {
        val fixture = progressControllerFixture(debounceMillis = 500)
        fixture.controller.onPosition(ReaderPosition.Paragraph(1, 0.1f))
        fixture.controller.onPosition(ReaderPosition.Paragraph(8, 0.6f))
        fixture.advanceTimeBy(499)
        assertEquals(0, fixture.repository.writes.size)
        fixture.controller.flush()
        assertEquals(ReaderPosition.Paragraph(8, 0.6f), fixture.repository.writes.single().position)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.ReadingProgressControllerTest.rapidPositionsCoalesceAndFlushLatest
```

Expected: **FAIL** because progress coalescing and flush behavior are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/reader/src/main/kotlin/app/openstory/reader/ReadingProgressController.kt`:

```kotlin
package app.openstory.reader

class ReadingProgressController(
    private val repository: ReadingProgressRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val policy: ProgressWritePolicy,
) {
    private val positions = MutableSharedFlow<ProgressInput>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var latest: ProgressInput? = null
    init { scope.launch { positions.onEach { latest = it }.debounce(policy.debounceMillis).collect { persist(it) } } }
    fun onPosition(input: ProgressInput) { positions.tryEmit(input) }
    suspend fun flush() { latest?.let { persist(it) } }
    private suspend fun persist(input: ProgressInput) = repository.upsert(input.toProgress(clock.nowEpochMillis()))
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:reader:test --tests app.openstory.reader.ReadingProgressControllerTest.rapidPositionsCoalesceAndFlushLatest
./gradlew :core:reader:test :core:database:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/reader/src/main/kotlin/app/openstory/reader/ReadingProgressController.kt core/reader/src/main/kotlin/app/openstory/reader/ProgressWritePolicy.kt core/database/src/main/kotlin/app/openstory/database/repository/ReadingProgressRepository.kt core/database/src/main/kotlin/app/openstory/database/repository/RoomReadingProgressRepository.kt core/reader/src/test/kotlin/app/openstory/reader/ReadingProgressControllerTest.kt
git commit -m "reader: persist canonical and exact reading progress"
```

### Task 5: Implement process-restorable Reader ViewModel and chapter navigation

**Files:**
- Create: feature/reader/build.gradle.kts
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/domain/OpenReader.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/domain/NavigateCanonicalChapter.kt
- Test: feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt

**Interfaces:**
- Consumes: Reader content repository, release selection policy, progress repository/controller, chapter graph, SavedStateHandle route.
- Produces: ViewModel that resolves exact/default release, restores position, loads document, switches release, and navigates previous/next canonical chapter while retaining source continuity.

**Acceptance:**
- Route exact release wins; absent release invokes policy.
- Process recreation restores route IDs and persisted progress, not large document objects in saved state.
- Next chapter selects a release using current language/plugin/group preference.
- Load failure exposes alternatives and retry without marking read.
- Release switch flushes old progress before opening new release.

**Implementation notes:**
- Cancel previous load when route/release changes; guard late results with request identity.
- Keep typography preferences in DataStore; keep reading progress in Room.
- Expose source/version/fingerprint in a diagnostics sheet, not primary reading chrome.

- [ ] **Step 1: Write the failing test**

Create `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`:

```kotlin
package app.openstory.reader.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderViewModelTest {
    @Test fun nextChapterPrefersSameSourceWithinLanguage() = runTest {
        val fixture = readerViewModelFixture(currentPlugin = "a", nextReleases = listOf(release("a", "vi"), release("b", "vi")))
        fixture.viewModel.nextChapter()
        fixture.viewModel.state.test {
            val ready = awaitReady()
            assertEquals("a", ready.release.pluginId.value)
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:reader:test --tests app.openstory.reader.ui.ReaderViewModelTest.nextChapterPrefersSameSourceWithinLanguage
```

Expected: **FAIL** because Reader state machine and canonical navigation are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt`:

```kotlin
package app.openstory.reader.ui

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val chapter: CanonicalChapter,
        val release: ChapterRelease,
        val document: ReaderDocument,
        val restoredPosition: ReaderPosition,
        val availableReleases: List<ChapterRelease>,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
    ) : ReaderUiState
    data class Error(val code: String, val alternatives: List<ChapterRelease>) : ReaderUiState
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:reader:test --tests app.openstory.reader.ui.ReaderViewModelTest.nextChapterPrefersSameSourceWithinLanguage
./gradlew :feature:reader:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/reader/build.gradle.kts feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt feature/reader/src/main/kotlin/app/openstory/reader/domain/OpenReader.kt feature/reader/src/main/kotlin/app/openstory/reader/domain/NavigateCanonicalChapter.kt feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt
git commit -m "reader: add restorable viewmodel and chapter navigation"
```

### Task 6: Build accessible Compose text reader, controls, and source switcher

**Files:**
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderRoute.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderDocumentView.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderSettingsSheet.kt
- Create: feature/reader/src/main/kotlin/app/openstory/reader/ui/ReleaseSwitcherSheet.kt
- Create: feature/settings/src/main/kotlin/app/openstory/settings/ReaderPreferencesRepository.kt
- Test: feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt

**Interfaces:**
- Consumes: Reader ViewModel state, typography preferences, app navigation, progress callbacks.
- Produces: Text reader with font size, line height, paragraph spacing, font family, theme/background mode, previous/next, progress restoration, and per-chapter release switching.

**Acceptance:**
- Reader restores to saved paragraph/fraction after process recreation.
- Changing typography preserves semantic paragraph position.
- Release switcher lists every release with selected/availability/download state.
- System font scaling and TalkBack remain usable; controls have content descriptions.
- Immersive controls can hide but are always recoverable by tap/back/accessibility.

**Implementation notes:**
- Persist semantic paragraph position rather than raw pixels.
- Use bundled/system fonts only in MVP; do not ship arbitrary downloaded font files.
- Apply color contrast tests to every reader theme and support dynamic text scaling without clipping controls.

- [ ] **Step 1: Write the failing test**

Create `feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt`:

```kotlin
package app.openstory.reader.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun releaseSwitcherShowsAllAlternativesAndSelectedRelease() {
        compose.setContent { ReaderScreen(readyReaderState(releaseCount = 3), ReaderActions.NoOp) }
        compose.onNodeWithContentDescription("Change release").performClick()
        compose.onAllNodesWithTag("release-option").assertCountEquals(3)
        compose.onNodeWithTag("release-selected").assertExists()
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.reader.ui.ReaderScreenTest
```

Expected: **FAIL** because reader Compose UI and release switcher are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderDocumentView.kt`:

```kotlin
package app.openstory.reader.ui

@Composable
fun ReaderDocumentView(
    document: ReaderDocument,
    preferences: ReaderPreferences,
    restoredPosition: ReaderPosition,
    onPositionChanged: (ProgressInput) -> Unit,
) {
    val state = rememberLazyListState()
    RestoreParagraphPosition(state, restoredPosition)
    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = preferences.horizontalPadding)) {
        itemsIndexed(document.blocks, key = { index, _ -> index }) { index, block ->
            ReaderBlockView(block, preferences, Modifier.semantics { this.indexForKey = index })
        }
    }
    ReportSemanticReaderPosition(state, document.blocks.size, onPositionChanged)
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.reader.ui.ReaderScreenTest
./gradlew :feature:reader:test :feature:reader:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderRoute.kt feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderDocumentView.kt feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderSettingsSheet.kt feature/reader/src/main/kotlin/app/openstory/reader/ui/ReleaseSwitcherSheet.kt feature/settings/src/main/kotlin/app/openstory/settings/ReaderPreferencesRepository.kt feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt
git commit -m "reader: add compose text reading experience"
```

## Wave Checkpoint

Do not begin `2026-08-03-09-cache-downloads-and-storage.md` until every item below is demonstrated on a clean checkout:

- [ ] Executable/empty chapter payload fixtures are rejected safely.
- [ ] Preferred language wins, then previous plugin/group within that language.
- [ ] Opening/switching releases records exact release but one canonical completion.
- [ ] Process death restores chapter/release/semantic paragraph position.
- [ ] Reader navigation never silently marks a failed chapter read.

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
