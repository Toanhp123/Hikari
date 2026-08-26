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
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.LanguageFallbackMode
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteSnapshotAssemblerAdaptiveTest {
    @Test
    fun cacheAndNetworkPortsMaterializeAdaptiveFactsAndResumeLocatorIsOnlyCacheInput() = runTest {
        val cache = RecordingCacheFactsPort(
            mapOf(releaseId to ReaderLocalCacheFact.Exact("resume-fp")),
        )
        val progress = progress("resume-fp")
        val assembler = assembler(
            progress = progress,
            cacheFacts = cache,
            networkFacts = ReaderNetworkFactsPort { ReaderNetworkState.OFFLINE },
        )

        val assembled = requireNotNull(assembler.assemble(context()))

        assertEquals(CandidateLocalAccess.AvailableExact("resume-fp"), assembled.snapshot.candidates.single().localAccess)
        assertEquals(ReaderNetworkClass.OFFLINE, assembled.snapshot.networkClass)
        assertEquals(mapOf(releaseId to "resume-fp"), cache.resumeFingerprints)
        assertEquals(setOf(releaseId), cache.releaseIds)
        assembled.probeLeases.forEach(ReaderHalfOpenProbeLease::release)
    }

    @Test
    fun sessionProvenInvalidityOverlaysOnlyTheExactMaterializedLocator() = runTest {
        val assembler = assembler(
            cacheFacts = RecordingCacheFactsPort(
                mapOf(releaseId to ReaderLocalCacheFact.Exact("bad-fp")),
            ),
        )
        val assembled = requireNotNull(
            assembler.assemble(
                context().copy(
                    knownInvalidLocalFingerprints = mapOf(releaseId to setOf("bad-fp", "other-fp")),
                ),
            ),
        )

        assertEquals(CandidateLocalAccess.KnownInvalid("bad-fp"), assembled.snapshot.candidates.single().localAccess)
        assembled.probeLeases.forEach(ReaderHalfOpenProbeLease::release)
    }

    @Test
    fun committedSourceAndLanguagePolicyComeFromSessionFactsNotResumeCompatibilityKeys() = runTest {
        val committedRelease = release("committed", "committed-source", "fr")
        val targetRelease = release("target", "target-source", "en")
        val assembler = assembler(cacheFacts = RecordingCacheFactsPort(emptyMap()))
        val context = context(releases = listOf(targetRelease), extraGroups = listOf(group("other", listOf(committedRelease)))).copy(
            preferences = ReaderPreferences(languageOrder = listOf("FR", "en")),
            committedIdentity = ReaderCommittedIdentity(
                chapterId = CanonicalChapterId("other"),
                releaseId = committedRelease.id,
                sourceId = committedRelease.pluginId,
                documentFingerprint = "committed-fp",
            ),
        )

        val assembled = requireNotNull(assembler.assemble(context))

        assertEquals(PluginId("committed-source"), assembled.snapshot.continuity.committedSourceId)
        assertEquals("fr", assembled.snapshot.continuity.committedLanguageTag)
        assertEquals(listOf("fr", "en"), assembled.policy.languageOrder)
        assertEquals(LanguageFallbackMode.ORDERED_ALLOW, assembled.policy.languageFallbackMode)
        assembled.probeLeases.forEach(ReaderHalfOpenProbeLease::release)
    }

    private fun assembler(
        progress: ReadingProgress? = null,
        cacheFacts: ReaderCacheFactsPort,
        networkFacts: ReaderNetworkFactsPort = ReaderNetworkFactsPort { ReaderNetworkState.UNKNOWN },
    ) = RouteSnapshotAssembler(
        progress = AdaptiveProgressRepository(progress),
        sourceAvailability = ReaderSourceAvailability { setOf(PluginId("source"), PluginId("target-source"), PluginId("committed-source")) },
        healthRegistry = ReaderSourceHealthRegistry(),
        executionLimiter = ReaderSourceExecutionLimiter(),
        cacheFacts = cacheFacts,
        networkFacts = networkFacts,
        nowEpochMillis = { 10_000L },
    )

    private fun context(
        releases: List<ChapterRelease> = listOf(release("release", "source", "en")),
        extraGroups: List<CanonicalChapterGroup> = emptyList(),
    ) = ReaderRouteExecutionContext(
        storyId = StoryId("story"),
        identity = ReaderExecutionIdentity(
            sessionId = ReaderSessionId(1),
            generationId = ReaderGenerationId(1),
            planRevision = ReaderPlanRevision(2),
            targetChapterId = chapterId,
        ),
        chapterGraphRevision = ReaderChapterGraphRevision(3),
        chapterGraph = ReaderSessionChapterGraph.create(
            StoryId("story"),
            listOf(group("chapter", releases)) + extraGroups,
        ),
        preferences = ReaderPreferences(languageOrder = listOf("en")),
        committedIdentity = null,
        explicitReleaseId = null,
    )

    private fun progress(fingerprint: String) = ReadingProgress(
        storyId = StoryId("story"),
        canonicalChapterId = chapterId,
        releaseId = releaseId,
        contentFingerprint = fingerprint,
        position = ReadingPosition("block", 0, 0f),
        completedAtEpochMillis = null,
        updatedAtEpochMillis = 1L,
    )

    private fun group(id: String, releases: List<ChapterRelease>) = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = CanonicalChapterId(id),
            storyId = StoryId("story"),
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            displayLabel = id,
            tombstoned = false,
            releaseIds = releases.map { it.id }.toSet(),
        ),
        releases = releases,
    )

    private fun release(id: String, source: String, language: String) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = StoryId("story"),
        pluginId = PluginId(source),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$id",
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = language,
        publishedAtEpochMillis = 1L,
        canonicalChapterId = if (id == "committed") CanonicalChapterId("other") else chapterId,
    )

    private companion object {
        val chapterId = CanonicalChapterId("chapter")
        val releaseId = ChapterReleaseId("release")
    }
}

private class RecordingCacheFactsPort(
    private val result: Map<ChapterReleaseId, ReaderLocalCacheFact>,
) : ReaderCacheFactsPort {
    var releaseIds: Set<ChapterReleaseId> = emptySet()
    var resumeFingerprints: Map<ChapterReleaseId, String> = emptyMap()

    override suspend fun inspect(
        releaseIds: Set<ChapterReleaseId>,
        resumeFingerprints: Map<ChapterReleaseId, String>,
    ): Map<ChapterReleaseId, ReaderLocalCacheFact> {
        this.releaseIds = releaseIds
        this.resumeFingerprints = resumeFingerprints
        return result
    }
}

private class AdaptiveProgressRepository(
    private val value: ReadingProgress?,
) : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(listOfNotNull(value))
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(value)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = value
    override suspend fun save(progress: ReadingProgress) = Unit
}
