package app.openstory.reader.routing

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.preferences.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderRouteSessionStateTest {
    @Test
    fun graphOwnsOneDefensiveCopyAndIndexesChapterAndRelease() {
        val releaseA = release("release-a", "chapter-a")
        val releaseB = release("release-b", "chapter-b")
        val releasesA = mutableListOf(releaseA)
        val releaseIdsA = linkedSetOf(releaseA.id)
        val groupA = group("chapter-a", releasesA, releaseIdsA)
        val groupB = group("chapter-b", listOf(releaseB))
        val expectedGroupA = group("chapter-a", listOf(releaseA))
        val sourceGroups = mutableListOf(groupA, groupB)

        val graph = ReaderSessionChapterGraph.create(StoryId("story"), sourceGroups)
        sourceGroups.clear()
        releasesA.clear()
        releaseIdsA.clear()

        assertEquals(0, graph.indexOf(groupA.chapter.id))
        assertEquals(1, graph.indexOf(groupB.chapter.id))
        assertEquals(expectedGroupA, graph.group(groupA.chapter.id))
        assertEquals(expectedGroupA, graph.previousBefore(groupB.chapter.id))
        assertEquals(groupB, graph.nextAfter(groupA.chapter.id))
        assertEquals(releaseA, graph.release(releaseA.id))
        assertEquals(setOf(releaseA.id, releaseB.id), graph.releaseIds)
        assertEquals(listOf(expectedGroupA, groupB), graph.groups)
    }

    @Test
    fun graphIndexesDuplicateIdsUsingFirstOccurrenceSemantics() {
        val firstRelease = release("shared-release", "shared-chapter", languageTag = "en")
        val secondRelease = release("shared-release", "shared-chapter", languageTag = "fr")
        val firstGroup = group("shared-chapter", listOf(firstRelease), displayLabel = "first")
        val secondGroup = group("shared-chapter", listOf(secondRelease), displayLabel = "second")

        val graph = ReaderSessionChapterGraph.create(
            StoryId("story"),
            listOf(firstGroup, secondGroup),
        )

        assertEquals(0, graph.indexOf(firstGroup.chapter.id))
        assertEquals(firstGroup, graph.group(firstGroup.chapter.id))
        assertEquals(firstRelease, graph.release(firstRelease.id))
        assertNull(graph.previousBefore(firstGroup.chapter.id))
        assertEquals(secondGroup, graph.nextAfter(firstGroup.chapter.id))
    }

    @Test
    fun graphRejectsChapterOwnedByAnotherStory() {
        val group = group("chapter-a", storyId = StoryId("other-story"))

        assertFailsWith<IllegalArgumentException> {
            ReaderSessionChapterGraph.create(StoryId("story"), listOf(group))
        }
    }

    @Test
    fun graphRejectsReleaseOwnedByAnotherStory() {
        val release = release("release-a", "chapter-a", storyId = StoryId("other-story"))
        val group = group("chapter-a", listOf(release))

        assertFailsWith<IllegalArgumentException> {
            ReaderSessionChapterGraph.create(StoryId("story"), listOf(group))
        }
    }

    @Test
    fun initialExecutionWaitsForFirstGraphAndPreferencesWithoutReplanning() = runTest {
        val session = session(ReaderSessionId(10))

        val execution = async {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }
        runCurrent()
        assertFalse(execution.isCompleted)

        session.updateChapterGraph(emptyList())
        runCurrent()
        assertFalse(execution.isCompleted)

        session.updateRoutingPreferences(ReaderPreferences())
        assertIs<ReaderForegroundResult.Exhausted>(execution.await())
        val state = assertIs<ReaderExecutionState.Exhausted>(session.executionState)
        assertEquals(ReaderPlanRevision(0), state.identity.planRevision)
    }

    @Test
    fun everyForegroundUserIntentGetsNewGeneration() = runTest {
        val session = session(ReaderSessionId(11))
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        val first = assertIs<ReaderExecutionState.Exhausted>(session.executionState)
        session.execute(ReaderForegroundIntent(chapter("chapter-b")))
        val second = assertIs<ReaderExecutionState.Exhausted>(session.executionState)

        assertEquals(ReaderGenerationId(1), first.identity.generationId)
        assertEquals(ReaderGenerationId(2), second.identity.generationId)
    }

    @Test
    fun hardInvalidationInActiveUncommittedIntentOnlyIncrementsPlanRevision() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        var invalidated = false
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(12),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                seen += context.identity
                if (!invalidated) {
                    invalidated = true
                    owner.hardInvalidate()
                }
                exhausted(context)
            },
        )
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(2, seen.size)
        assertEquals(ReaderGenerationId(1), seen[0].generationId)
        assertEquals(ReaderGenerationId(1), seen[1].generationId)
        assertEquals(ReaderPlanRevision(0), seen[0].planRevision)
        assertEquals(ReaderPlanRevision(1), seen[1].planRevision)
    }

    @Test
    fun retryAfterExhaustionStartsANewGeneration() = runTest {
        val session = session(ReaderSessionId(13))
        ready(session)
        val intent = ReaderForegroundIntent(
            targetChapterId = chapter("chapter-a"),
            explicitReleaseId = ChapterReleaseId("release-explicit"),
        )

        val first = assertIs<ReaderForegroundResult.Exhausted>(session.execute(intent))
        val second = assertIs<ReaderForegroundResult.Exhausted>(session.execute(intent))

        assertEquals(ReaderGenerationId(1), first.identity.generationId)
        assertEquals(ReaderGenerationId(2), second.identity.generationId)
    }

    @Test
    fun twoSessionsMayBothUseGenerationOneWithoutIdentityCollision() = runTest {
        val firstSession = session(ReaderSessionId(21))
        val secondSession = session(ReaderSessionId(22))
        ready(firstSession)
        ready(secondSession)

        val first = assertIs<ReaderForegroundResult.Exhausted>(
            firstSession.execute(ReaderForegroundIntent(chapter("chapter-a"))),
        )
        val second = assertIs<ReaderForegroundResult.Exhausted>(
            secondSession.execute(ReaderForegroundIntent(chapter("chapter-a"))),
        )

        assertEquals(ReaderGenerationId(1), first.identity.generationId)
        assertEquals(ReaderGenerationId(1), second.identity.generationId)
        assertNotEquals(first.identity.sessionId, second.identity.sessionId)
    }

    @Test
    fun routingLanguagePreferenceChangeHardInvalidatesActiveGenerationInM4() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        var changed = false
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(23),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                seen += context.identity
                if (!changed) {
                    changed = true
                    owner.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("fr")))
                }
                exhausted(context)
            },
        )
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(2, seen.size)
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seen.map { it.planRevision })
    }

    @Test
    fun cancellationClosesCurrentGenerationEvenAfterPlanRevisionAdvances() = runTest {
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(24),
            delegate = ReaderRouteExecutionDelegate { owner, _ ->
                owner.hardInvalidate()
                throw CancellationException("cancel active generation")
            },
        )
        ready(session)

        assertFailsWith<CancellationException> {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }

        val state = assertIs<ReaderExecutionState.Cancelled>(session.executionState)
        assertEquals(ReaderGenerationId(1), state.identity.generationId)
        assertEquals(ReaderPlanRevision(1), state.identity.planRevision)
    }

    @Test
    fun executionIdentityHasOnePlanRevisionAndNoOpaqueExecutionRevisionOrPlanHash() {
        val fields = ReaderExecutionIdentity::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(fields.any { it.contains("executionRevision", ignoreCase = true) })
        assertFalse(fields.any { it.contains("planHash", ignoreCase = true) })
    }

    private fun session(id: ReaderSessionId) = ReaderRouteSession(
        storyId = StoryId("story"),
        sessionId = id,
        delegate = ReaderRouteExecutionDelegate { _, context -> exhausted(context) },
    )

    private suspend fun ready(session: ReaderRouteSession) {
        session.updateChapterGraph(emptyList())
        session.updateRoutingPreferences(ReaderPreferences())
    }

    private fun exhausted(context: ReaderRouteExecutionContext) = ReaderForegroundResult.Exhausted(
        identity = context.foregroundIdentity,
        code = "reader.no_release_available",
        retryable = false,
        attempts = emptyList(),
    )

    private fun group(
        value: String,
        releases: List<ChapterRelease> = emptyList(),
        releaseIds: Set<ChapterReleaseId> = releases.mapTo(linkedSetOf(), ChapterRelease::id),
        storyId: StoryId = StoryId("story"),
        displayLabel: String = value,
    ) = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = chapter(value),
            storyId = storyId,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            displayLabel = displayLabel,
            tombstoned = false,
            releaseIds = releaseIds,
        ),
        releases = releases,
    )

    private fun release(
        value: String,
        chapter: String,
        storyId: StoryId = StoryId("story"),
        languageTag: String = "en",
    ) = ChapterRelease(
        id = ChapterReleaseId(value),
        storyId = storyId,
        pluginId = PluginId("plugin-$value"),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$value",
        displayLabel = value,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = languageTag,
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapter(chapter),
    )

    private fun chapter(value: String) = CanonicalChapterId(value)
}
