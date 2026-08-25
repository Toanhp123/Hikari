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
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.preferences.ReaderPreferences
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderRuntimeStressTest {
    @Test
    fun rapidGenerationsGraphRevisionsAndHardReplansKeepOnlyCurrentIdentityActive() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        val session = ReaderRouteSession(
            storyId = STORY_ID,
            sessionId = ReaderSessionId(801),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                seen += context.identity
                if (context.identity.planRevision.value == 0L) owner.hardInvalidate()
                exhausted(context)
            },
        )
        session.updateChapterGraph(emptyList())
        session.updateRoutingPreferences(ReaderPreferences())

        repeat(100) { index ->
            val result = session.execute(ReaderForegroundIntent(chapter("chapter-$index")))
            assertEquals(index + 1L, result.identity.generationId.value)
            assertEquals(801L, result.identity.sessionId.value)
            session.updateChapterGraph(listOf(group("graph-$index", source = "source-${index % 10}")))
        }

        assertEquals(200, seen.size)
        seen.chunked(2).forEachIndexed { index, pair ->
            assertEquals(2, pair.size)
            assertEquals(index + 1L, pair[0].generationId.value)
            assertEquals(pair[0].generationId, pair[1].generationId)
            assertEquals(0L, pair[0].planRevision.value)
            assertEquals(1L, pair[1].planRevision.value)
        }
    }

    @Test
    fun twoSessionsKeepExecutionStateIndependentWhileSharingProcessHealthUnderLoad() = runTest {
        val health = ReaderSourceHealthRegistry()
        val sourceKey = SourceOperationKey(PluginId("shared-source"))
        repeat(100) { index ->
            health.record(
                key = sourceKey,
                observation = SourceObservation.Success.Remote(
                    RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
                    latencyMillis = index.toLong(),
                ),
                nowEpochMillis = index.toLong(),
            )
        }

        val first = stressSession(ReaderSessionId(901))
        val second = stressSession(ReaderSessionId(902))
        ready(first)
        ready(second)

        repeat(100) { index ->
            val firstResult = first.execute(ReaderForegroundIntent(chapter("a-$index")))
            val secondResult = second.execute(ReaderForegroundIntent(chapter("b-$index")))
            assertEquals(index + 1L, firstResult.identity.generationId.value)
            assertEquals(index + 1L, secondResult.identity.generationId.value)
            assertNotEquals(firstResult.identity.sessionId, secondResult.identity.sessionId)
        }

        val firstHealthView = health.snapshot(sourceKey, 1_000L)
        val secondHealthView = health.snapshot(sourceKey, 1_000L)
        assertEquals(firstHealthView, secondHealthView)
        assertTrue(firstHealthView.state.recentLatencySamplesMillis.size <= 20)
    }

    @Test
    fun processSourceLaneStaysSerializedAcrossConcurrentForegroundPressure() = runTest {
        val limiter = ReaderSourceExecutionLimiter()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val sourceId = PluginId("serialized-source")

        List(100) {
            async {
                limiter.withRemotePermit(sourceId, ReaderRemoteWorkPriority.FOREGROUND) {
                    val nowActive = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, nowActive) }
                    try {
                        repeat(3) { yield() }
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
        }.awaitAll()

        assertEquals(0, active.get())
        assertEquals(1, maximum.get())
    }

    @Test
    fun repeatedForegroundNavigationCancelsObsoletePrefetchWithoutLeakingSessionAuthority() = runTest {
        var prefetchStarted = 0
        var prefetchCancelled = 0
        val groups = listOf(
            group("chapter-1", source = "source-1"),
            group("chapter-2", source = "source-2"),
            group("chapter-3", source = "source-3"),
        )
        val session = ReaderRouteSession(
            storyId = STORY_ID,
            sessionId = ReaderSessionId(950),
            delegate = ReaderRouteExecutionDelegate { _, context -> committed(context) },
            prefetchDelegate = ReaderPrefetchExecutionDelegate { _, _ ->
                prefetchStarted += 1
                try {
                    awaitCancellation()
                } finally {
                    prefetchCancelled += 1
                }
            },
            prefetchScope = this,
        )
        session.updateChapterGraph(groups)
        session.updateRoutingPreferences(ReaderPreferences())

        repeat(25) {
            session.execute(ReaderForegroundIntent(chapter("chapter-1")))
            runCurrent()
            session.execute(ReaderForegroundIntent(chapter("chapter-2")))
            runCurrent()
        }
        session.execute(ReaderForegroundIntent(chapter("chapter-3")))
        runCurrent()

        assertTrue(prefetchStarted >= 50)
        assertEquals(prefetchStarted, prefetchCancelled)
        assertEquals(51L, session.executionState.identityGenerationValueOrNull())
    }

    private fun stressSession(id: ReaderSessionId): ReaderRouteSession = ReaderRouteSession(
        storyId = STORY_ID,
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

    private fun committed(context: ReaderRouteExecutionContext): ReaderForegroundResult.Committed {
        val group = context.chapterGroups.first { it.chapter.id == context.identity.targetChapterId }
        val release = group.releases.single()
        val index = context.chapterGroups.indexOf(group)
        return ReaderForegroundResult.Committed(
            identity = context.foregroundIdentity,
            chapterGroup = group,
            release = release,
            document = ReaderDocument(
                title = release.displayLabel,
                blocks = listOf(ReaderBlock.Paragraph("block", release.displayLabel)),
                fingerprint = "fp-${release.id.value}",
            ),
            fromLocal = false,
            previousChapterId = context.chapterGroups.getOrNull(index - 1)?.chapter?.id,
            nextChapterId = context.chapterGroups.getOrNull(index + 1)?.chapter?.id,
            restoration = null,
        )
    }

    private fun group(value: String, source: String): CanonicalChapterGroup {
        val chapterId = chapter(value)
        val release = ChapterRelease(
            id = ChapterReleaseId("release-$value"),
            storyId = STORY_ID,
            pluginId = PluginId(source),
            sourceStoryId = "remote-story",
            sourceReleaseId = "remote-$value",
            displayLabel = value,
            parsedLabel = LABEL,
            languageTag = "vi",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = chapterId,
        )
        return CanonicalChapterGroup(
            chapter = CanonicalChapter(
                id = chapterId,
                storyId = STORY_ID,
                parsedLabel = LABEL,
                displayLabel = value,
                tombstoned = false,
                releaseIds = setOf(release.id),
            ),
            releases = listOf(release),
        )
    }

    private fun ReaderExecutionState.identityGenerationValueOrNull(): Long? = when (this) {
        is ReaderExecutionState.Committed -> identity.generationId.value
        is ReaderExecutionState.Exhausted -> identity.generationId.value
        is ReaderExecutionState.Cancelled -> identity.generationId.value
        is ReaderExecutionState.Planning -> identity.generationId.value
        is ReaderExecutionState.Executing -> attempt.generationId.value
        is ReaderExecutionState.Competing -> primary.generationId.value
        is ReaderExecutionState.Recovering -> attempt.generationId.value
        is ReaderExecutionState.Validating -> attempt.generationId.value
        ReaderExecutionState.Idle -> null
    }

    private fun chapter(value: String) = CanonicalChapterId(value)

    private companion object {
        val STORY_ID = StoryId("story")
        val LABEL = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null)
    }
}
