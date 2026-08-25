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
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.selection.ReleaseCandidate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderCoordinatorModelTest {
    @Test
    fun `seeded completion and delivery permutations preserve one deterministic winner`() {
        repeat(256) { seed ->
            val primary = completion("attempt-primary", AttemptRole.PRIMARY, 700L)
            val hedge = completion("attempt-hedge", AttemptRole.HEDGE, 700L)
            val registry = CompetitiveCompletionRegistry()
            listOf(primary, hedge).shuffled(Random(seed)).forEach(registry::record)

            val notifications = listOf(primary.attempt.attemptId, hedge.attempt.attemptId)
                .shuffled(Random(seed xor 0x5A5A))

            assertEquals(2, notifications.distinct().size)
            assertEquals(primary, registry.winner())
        }
    }

    @Test
    fun `new user intent supersedes an older completion without changing committed state`() = runTest {
        val gates = mutableMapOf<Long, CompletableDeferred<Unit>>()
        val contexts = mutableListOf<ReaderRouteExecutionContext>()
        val session = session { _, context ->
            contexts += context
            gates.getOrPut(context.identity.generationId.value, ::CompletableDeferred).await()
            committed(context)
        }
        ready(session)

        val first = async { session.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        val second = async { session.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()

        gates.getValue(1L).complete(Unit)
        runCurrent()
        assertIs<ReaderForegroundResult.Superseded>(first.await())

        gates.getValue(2L).complete(Unit)
        val committed = assertIs<ReaderForegroundResult.Committed>(second.await())
        assertEquals(ReaderGenerationId(2), committed.identity.generationId)
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(ReaderGenerationId(2), state.identity.generationId)
        assertEquals(listOf(ReaderGenerationId(1), ReaderGenerationId(2)), contexts.map { it.identity.generationId })
    }

    @Test
    fun `hard invalidation rejects stale plan completion and commits only the revised plan`() = runTest {
        val firstPlanGate = CompletableDeferred<Unit>()
        val seenRevisions = mutableListOf<ReaderPlanRevision>()
        val session = session { _, context ->
            seenRevisions += context.identity.planRevision
            if (context.identity.planRevision == ReaderPlanRevision(0)) firstPlanGate.await()
            committed(context)
        }
        ready(session)

        val execution = async { session.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        session.hardInvalidate()
        firstPlanGate.complete(Unit)

        val result = assertIs<ReaderForegroundResult.Committed>(execution.await())
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seenRevisions)
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(ReaderPlanRevision(1), state.identity.planRevision)
        assertEquals(ReaderGenerationId(1), result.identity.generationId)
    }

    private fun session(
        delegate: ReaderRouteExecutionDelegate,
    ) = ReaderRouteSession(
        storyId = STORY_ID,
        sessionId = ReaderSessionId(91),
        delegate = delegate,
    )

    private suspend fun ready(session: ReaderRouteSession) {
        session.updateChapterGraph(listOf(GROUP))
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))
    }

    private fun committed(context: ReaderRouteExecutionContext) = ReaderForegroundResult.Committed(
        identity = context.foregroundIdentity,
        chapterGroup = GROUP,
        release = RELEASE,
        document = document("committed-${context.identity.planRevision.value}"),
        fromLocal = false,
        previousChapterId = null,
        nextChapterId = null,
        restoration = null,
    )

    private fun completion(
        attemptId: String,
        role: AttemptRole,
        completedAtNanos: Long,
    ): ReaderValidCompletion {
        val attempt = RouteAttempt(
            attemptId = attemptId,
            releaseId = RELEASE.id,
            sourceId = RELEASE.pluginId,
            accessMode = AccessMode.REMOTE,
            localFingerprint = null,
            role = role,
        )
        return ReaderValidCompletion(
            attempt = attempt,
            loaded = ReaderLoadResult.Success(
                release = ReleaseCandidate(RELEASE),
                document = document(attemptId),
                fromStore = false,
            ),
            completedAtNanos = completedAtNanos,
        )
    }

    private fun document(fingerprint: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", fingerprint)),
        fingerprint = fingerprint,
    )

    private companion object {
        val STORY_ID = StoryId("story")
        val CHAPTER_ID = CanonicalChapterId("chapter")
        val RELEASE = ChapterRelease(
            id = ChapterReleaseId("release"),
            storyId = STORY_ID,
            pluginId = PluginId("source"),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-release",
            displayLabel = "release",
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = CHAPTER_ID,
        )
        val GROUP = CanonicalChapterGroup(
            chapter = CanonicalChapter(
                id = CHAPTER_ID,
                storyId = STORY_ID,
                parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
                displayLabel = "chapter",
                tombstoned = false,
                releaseIds = setOf(RELEASE.id),
            ),
            releases = listOf(RELEASE),
        )
    }
}
