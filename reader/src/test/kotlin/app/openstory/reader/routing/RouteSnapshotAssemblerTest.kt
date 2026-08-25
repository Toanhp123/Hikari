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
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceHealthOrigin
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteSnapshotAssemblerTest {
    @Test
    fun assemblesCanonicalAvailabilityHealthAndRevisionFactsWithoutAdaptiveRanking() = runTest {
        val health = ReaderSourceHealthRegistry()
        health.record(
            SourceOperationKey(PluginId("z-source")),
            SourceObservation.Success.Remote(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT, 100L),
            10L,
        )
        val assembler = RouteSnapshotAssembler(
            progress = SnapshotProgressRepository(),
            sourceAvailability = ReaderSourceAvailability { setOf(PluginId("z-source")) },
            healthRegistry = health,
            executionLimiter = ReaderSourceExecutionLimiter(),
            nowEpochMillis = { 1234L },
        )
        val context = context(
            releases = listOf(
                release("z-release", "z-source"),
                release("a-release", "a-source"),
            ),
        )

        val assembled = requireNotNull(assembler.assemble(context))
        val snapshot = assembled.snapshot

        assertEquals(ReaderPlanRevision(4), snapshot.planRevision)
        assertEquals(ReaderChapterGraphRevision(7), snapshot.chapterGraphRevision)
        assertEquals(1234L, snapshot.nowEpochMillis)
        assertEquals(
            listOf(ChapterReleaseId("a-release"), ChapterReleaseId("z-release")),
            snapshot.candidates.map { it.releaseId },
        )
        assertEquals(
            listOf(CandidateRemoteAccess.SOURCE_UNAVAILABLE, CandidateRemoteAccess.PERMITTED),
            snapshot.candidates.map { it.remoteAccess },
        )
        assertEquals(
            listOf(SourceHealthOrigin.STARTUP_NEUTRAL, SourceHealthOrigin.PROCESS_OBSERVED),
            snapshot.sourceHealth.map { it.origin },
        )
        assembled.probeLeases.forEach(ReaderHalfOpenProbeLease::release)
    }

    @Test
    fun observedHalfOpenSourceCarriesOnlyHeldProbePermissionFact() = runTest {
        val health = ReaderSourceHealthRegistry()
        repeat(3) { index ->
            health.record(
                SourceOperationKey(PluginId("source")),
                SourceObservation.TransportFailure.Timeout(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT),
                index.toLong(),
            )
        }
        val limiter = ReaderSourceExecutionLimiter()
        val assembler = RouteSnapshotAssembler(
            progress = SnapshotProgressRepository(),
            sourceAvailability = ReaderSourceAvailability { setOf(PluginId("source")) },
            healthRegistry = health,
            executionLimiter = limiter,
            nowEpochMillis = { 30_002L },
        )

        val assembled = requireNotNull(assembler.assemble(context(listOf(release("release", "source")))))
        assertEquals(true, assembled.snapshot.sourceHealth.single().halfOpenProbePermitted)
        assertEquals(1, assembled.probeLeases.size)
        assertEquals(null, limiter.tryAcquireHalfOpenProbe(SourceOperationKey(PluginId("source"))))

        assembled.probeLeases.single().release()
        requireNotNull(limiter.tryAcquireHalfOpenProbe(SourceOperationKey(PluginId("source")))).release()
    }


    @Test
    fun knownInvalidExactLocatorIsCarriedAsObservationalLocalFact() = runTest {
        val release = release("release", "source")
        val progress = ReadingProgress(
            storyId = StoryId("story"),
            canonicalChapterId = CanonicalChapterId("chapter"),
            releaseId = release.id,
            contentFingerprint = "bad-fingerprint",
            position = app.openstory.reader.progress.ReadingPosition("block", 0, 0f),
            completedAtEpochMillis = null,
            updatedAtEpochMillis = 1L,
        )
        val assembler = RouteSnapshotAssembler(
            progress = SnapshotProgressRepository(progress),
            sourceAvailability = ReaderSourceAvailability { setOf(PluginId("source")) },
            healthRegistry = ReaderSourceHealthRegistry(),
            executionLimiter = ReaderSourceExecutionLimiter(),
            nowEpochMillis = { 100L },
        )
        val context = context(listOf(release)).copy(
            knownInvalidLocalFingerprints = mapOf(release.id to setOf("bad-fingerprint")),
        )

        val assembled = requireNotNull(assembler.assemble(context))

        assertEquals(
            app.openstory.reader.engine.CandidateLocalAccess.KnownInvalid("bad-fingerprint"),
            assembled.snapshot.candidates.single().localAccess,
        )
        assembled.probeLeases.forEach(ReaderHalfOpenProbeLease::release)
    }

    private fun context(releases: List<ChapterRelease>): ReaderRouteExecutionContext {
        val chapterId = CanonicalChapterId("chapter")
        return ReaderRouteExecutionContext(
            storyId = StoryId("story"),
            identity = ReaderExecutionIdentity(
                sessionId = ReaderSessionId(1),
                generationId = ReaderGenerationId(1),
                planRevision = ReaderPlanRevision(4),
                targetChapterId = chapterId,
            ),
            chapterGraphRevision = ReaderChapterGraphRevision(7),
            chapterGroups = listOf(
                CanonicalChapterGroup(
                    chapter = CanonicalChapter(
                        id = chapterId,
                        storyId = StoryId("story"),
                        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
                        displayLabel = "chapter",
                        tombstoned = false,
                        releaseIds = releases.map { it.id }.toSet(),
                    ),
                    releases = releases,
                ),
            ),
            preferences = ReaderPreferences(languageOrder = listOf("en")),
            committedIdentity = null,
            explicitReleaseId = null,
            knownInvalidLocalFingerprints = emptyMap(),
        )
    }

    private fun release(id: String, source: String) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = StoryId("story"),
        pluginId = PluginId(source),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$id",
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = CanonicalChapterId("chapter"),
    )
}

private class SnapshotProgressRepository(
    private val value: ReadingProgress? = null,
) : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(listOfNotNull(value))
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(value)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = value
    override suspend fun save(progress: ReadingProgress) = Unit
}
