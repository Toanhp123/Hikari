package app.openstory.chapters.sync

import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncPhase
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.chapters.source.ChapterListMode
import app.openstory.chapters.source.ChapterSource
import app.openstory.chapters.source.ChapterSourcePage
import app.openstory.chapters.source.ChapterSourceRegistry
import app.openstory.chapters.source.ChapterSourceRelease
import app.openstory.chapters.source.ChapterSourceRequest
import app.openstory.chapters.source.ChapterSourceResult
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ChapterSyncServiceTest {
    @Test
    fun recentReleaseCommitsBeforeFullHistory() = runTest {
        val repository = RecordingChapterRepository()
        val source = RecordingSource { request ->
            when (request.mode) {
                ChapterListMode.RECENT -> page("recent-10", "10")
                ChapterListMode.FULL -> page("full-1", "1")
                ChapterListMode.INCREMENTAL -> error("not expected")
            }
        }
        val service = service(repository, source)

        assertIs<ChapterSyncReport.Success>(service.sync(STORY_ID))

        assertEquals(listOf("recent-10", "full-1"), repository.commits.map { it.releases.single().sourceReleaseId })
        assertEquals(
            listOf(ChapterSyncPhase.FULL, ChapterSyncPhase.FULL),
            repository.commits.map { it.syncState!!.phase },
        )
    }

    @Test
    fun rawNumberDefinesChapterIdentityWhenSourceTitleIsDescriptive() = runTest {
        val repository = RecordingChapterRepository()
        val source = RecordingSource { request ->
            when (request.mode) {
                ChapterListMode.RECENT -> ChapterSourceResult.Success(
                    ChapterSourcePage(
                        releases = listOf(
                            ChapterSourceRelease(
                                sourceReleaseId = "release-12",
                                title = "Volume 99: The Locked Constellation",
                                rawNumber = "12",
                                languageTag = "en",
                                publishedAtEpochMillis = 100L,
                            ),
                        ),
                        nextToken = null,
                    ),
                )
                ChapterListMode.FULL -> page("full-12", "12")
                ChapterListMode.INCREMENTAL -> error("not expected")
            }
        }

        assertIs<ChapterSyncReport.Success>(service(repository, source).sync(STORY_ID))

        val release = repository.commits.first().releases.single()
        assertEquals(java.math.BigDecimal("12"), release.parsedLabel.chapter)
        assertEquals(null, release.parsedLabel.volume)
        assertEquals("Chapter 12 · Volume 99: The Locked Constellation", release.displayLabel)
        assertEquals(
            java.math.BigDecimal("12"),
            repository.commits.first().plan.creates.single().parsedLabel.chapter,
        )
    }

    @Test
    fun completedFullSyncKeepsFingerprintInternalAndLeavesCheckpointEmpty() = runTest {
        val repository = RecordingChapterRepository()
        val source = RecordingSource { request ->
            when (request.mode) {
                ChapterListMode.RECENT -> page("recent-10", "10")
                ChapterListMode.FULL -> page("full-1", "1")
                ChapterListMode.INCREMENTAL -> error("incremental requires a source checkpoint")
            }
        }

        assertIs<ChapterSyncReport.Success>(service(repository, source).sync(STORY_ID))

        val state = repository.syncState(STORY_ID, PLUGIN_ID, SOURCE_STORY_ID)!!
        assertEquals(null, state.checkpoint)
        assertEquals(ChapterSyncPhase.FULL, state.phase)
        assertEquals(64, state.fingerprint?.length)
    }

    @Test
    fun refreshWithoutSourceCheckpointRunsSafeFullSyncAgain() = runTest {
        val repository = RecordingChapterRepository()
        val source = RecordingSource { request ->
            when (request.mode) {
                ChapterListMode.RECENT -> page("recent-10", "10")
                ChapterListMode.FULL -> page("full-${request.nextToken ?: "start"}", "1")
                ChapterListMode.INCREMENTAL -> error("incremental requires a source checkpoint")
            }
        }
        val service = service(repository, source)

        assertIs<ChapterSyncReport.Success>(service.sync(STORY_ID))
        source.requests.clear()
        assertIs<ChapterSyncReport.Success>(service.sync(STORY_ID))

        assertEquals(listOf(ChapterListMode.FULL), source.requests.map(ChapterSourceRequest::mode))
        assertEquals(listOf<String?>(null), source.requests.map(ChapterSourceRequest::checkpoint))
    }

    @Test
    fun legacyFingerprintCheckpointFallsBackToFullSync() = runTest {
        val fingerprint = "a".repeat(64)
        val repository = RecordingChapterRepository(
            initialState = ChapterSyncState(
                STORY_ID,
                PLUGIN_ID,
                SOURCE_STORY_ID,
                ChapterSyncPhase.INCREMENTAL,
                cursor = "incremental-page-2",
                checkpoint = fingerprint,
                fingerprint = fingerprint,
                updatedAtEpochMillis = 1L,
            ),
        )
        val source = RecordingSource { request ->
            assertEquals(ChapterListMode.FULL, request.mode)
            assertEquals(null, request.checkpoint)
            assertEquals(null, request.nextToken)
            page("full-1", "1")
        }

        assertIs<ChapterSyncReport.Success>(service(repository, source).sync(STORY_ID))

        val state = repository.syncState(STORY_ID, PLUGIN_ID, SOURCE_STORY_ID)!!
        assertEquals(null, state.checkpoint)
        assertEquals(ChapterSyncPhase.FULL, state.phase)
    }

    @Test
    fun incrementalSyncUsesOpaqueSourceCheckpointWhenItIsDistinctFromFingerprint() = runTest {
        val repository = RecordingChapterRepository(
            initialState = ChapterSyncState(
                STORY_ID,
                PLUGIN_ID,
                SOURCE_STORY_ID,
                ChapterSyncPhase.INCREMENTAL,
                cursor = null,
                checkpoint = "2026-08-19T00:00:00Z",
                fingerprint = "a".repeat(64),
                updatedAtEpochMillis = 1L,
            ),
        )
        val source = RecordingSource { request ->
            assertEquals(ChapterListMode.INCREMENTAL, request.mode)
            assertEquals("2026-08-19T00:00:00Z", request.checkpoint)
            page("delta-2", "2")
        }

        assertIs<ChapterSyncReport.Success>(service(repository, source).sync(STORY_ID))

        val state = repository.syncState(STORY_ID, PLUGIN_ID, SOURCE_STORY_ID)!!
        assertEquals(ChapterSyncPhase.INCREMENTAL, state.phase)
        assertEquals("2026-08-19T00:00:00Z", state.checkpoint)
    }

    @Test
    fun fullSyncResumesFromCommittedCursorAndFingerprint() = runTest {
        val initial = ChapterSyncState(
            STORY_ID,
            PLUGIN_ID,
            SOURCE_STORY_ID,
            ChapterSyncPhase.FULL,
            cursor = "page-2",
            checkpoint = null,
            fingerprint = "old-fingerprint",
            updatedAtEpochMillis = 1L,
        )
        val repository = RecordingChapterRepository(initialState = initial)
        val source = RecordingSource { request ->
            assertEquals(ChapterListMode.FULL, request.mode)
            assertEquals("page-2", request.nextToken)
            page("full-2", "2")
        }

        assertIs<ChapterSyncReport.Success>(service(repository, source).sync(STORY_ID))

        assertEquals(listOf(ChapterListMode.FULL), source.requests.map(ChapterSourceRequest::mode))
        val advanced = repository.commits.single().syncState!!
        assertEquals(ChapterSyncPhase.FULL, advanced.phase)
        assertEquals(null, advanced.cursor)
        assertEquals(false, advanced.fingerprint == "old-fingerprint")
    }

    @Test
    fun paginatedSyncLoadsChapterGraphOnlyOncePerRun() = runTest {
        val initial = ChapterSyncState(
            STORY_ID,
            PLUGIN_ID,
            SOURCE_STORY_ID,
            ChapterSyncPhase.FULL,
            cursor = null,
            checkpoint = null,
            fingerprint = "old-fingerprint",
            updatedAtEpochMillis = 1L,
        )
        val repository = RecordingChapterRepository(initialState = initial)
        val source = RecordingSource { request ->
            when (request.nextToken) {
                null -> page("full-1", "1", nextToken = "page-2")
                "page-2" -> page("full-2", "2")
                else -> error("unexpected cursor ${request.nextToken}")
            }
        }

        assertIs<ChapterSyncReport.Success>(service(repository, source).sync(STORY_ID))

        assertEquals(2, repository.commits.size)
        assertEquals(1, repository.snapshotCalls)
    }

    @Test
    fun failedCommitDoesNotAdvanceCursorOrFingerprint() = runTest {
        val initial = ChapterSyncState(
            STORY_ID,
            PLUGIN_ID,
            SOURCE_STORY_ID,
            ChapterSyncPhase.FULL,
            cursor = "page-2",
            checkpoint = null,
            fingerprint = "old-fingerprint",
            updatedAtEpochMillis = 1L,
        )
        val repository = RecordingChapterRepository(initialState = initial, failCommits = true)
        val source = RecordingSource { page("full-2", "2", nextToken = "page-3") }

        val report = assertIs<ChapterSyncReport.Failure>(service(repository, source).sync(STORY_ID))

        assertEquals("chapter.storage_commit_failed", report.failures.single().code)
        assertEquals(initial, repository.syncState(STORY_ID, PLUGIN_ID, SOURCE_STORY_ID))
    }

    @Test
    fun sourceFingerprintIgnoresReleasesOwnedByOtherPlugins() = runTest {
        val initial = ChapterSyncState(
            STORY_ID,
            PLUGIN_ID,
            SOURCE_STORY_ID,
            ChapterSyncPhase.FULL,
            cursor = null,
            checkpoint = null,
            fingerprint = "old-fingerprint",
            updatedAtEpochMillis = 1L,
        )
        val source = RecordingSource { page("full-2", "2") }
        val plain = RecordingChapterRepository(initialState = initial)
        val withPeer = RecordingChapterRepository(
            initialState = initial,
            initialReleases = listOf(release(PluginId("org.example.peer"), "peer-1")),
        )

        assertIs<ChapterSyncReport.Success>(service(plain, source).sync(STORY_ID))
        assertIs<ChapterSyncReport.Success>(service(withPeer, source).sync(STORY_ID))

        assertEquals(
            plain.commits.single().syncState!!.fingerprint,
            withPeer.commits.single().syncState!!.fingerprint,
        )
    }

    @Test
    fun registryFailureBecomesTypedGlobalFailure() = runTest {
        val service = ChapterSyncService(
            mappings = FixedMappingRepository,
            sources = object : ChapterSourceRegistry {
                override suspend fun enabled(): List<ChapterSource> = error("registry unavailable")
            },
            chapters = RecordingChapterRepository(),
            aggregation = ChapterAggregationEngine(),
            parser = ChapterLabelParser(),
            clock = FakeClock(1_000L),
        )

        val report = assertIs<ChapterSyncReport.Failure>(service.sync(STORY_ID))

        assertEquals(null, report.failures.single().pluginId)
        assertEquals("chapter.sync_failed", report.failures.single().code)
        assertEquals(true, report.failures.single().retryable)
    }
}

private fun service(
    repository: ChapterRepository,
    source: ChapterSource,
) = ChapterSyncService(
    mappings = FixedMappingRepository,
    sources = object : ChapterSourceRegistry {
        override suspend fun enabled(): List<ChapterSource> = listOf(source)
    },
    chapters = repository,
    aggregation = ChapterAggregationEngine(),
    parser = ChapterLabelParser(),
    clock = FakeClock(1_000L),
)

private fun page(
    releaseId: String,
    number: String,
    nextToken: String? = null,
) = ChapterSourceResult.Success(
    ChapterSourcePage(
        listOf(ChapterSourceRelease(releaseId, "Chapter $number", number, "en", 100L)),
        nextToken,
    ),
)

private class RecordingSource(
    private val result: suspend (ChapterSourceRequest) -> ChapterSourceResult,
) : ChapterSource {
    override val pluginId = PLUGIN_ID
    override val version = "1.0.0"
    val requests = mutableListOf<ChapterSourceRequest>()

    override suspend fun chapters(request: ChapterSourceRequest): ChapterSourceResult {
        requests += request
        return result(request)
    }
}

private class RecordingChapterRepository(
    initialState: ChapterSyncState? = null,
    private val failCommits: Boolean = false,
    initialReleases: List<ChapterRelease> = emptyList(),
) : ChapterRepository {
    val commits = mutableListOf<ChapterMutation>()
    var snapshotCalls = 0
        private set
    private val groups = MutableStateFlow<List<CanonicalChapterGroup>>(emptyList())
    private var state = initialState
    private val releases = initialReleases.associateByTo(linkedMapOf()) { release -> release.id.value }

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = groups
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = groups

    override suspend fun snapshot(storyId: StoryId): ChapterGraphSnapshot {
        snapshotCalls += 1
        return ChapterGraphSnapshot(
            chapters = emptyList(),
            releases = releases.values.toList(),
            overrides = emptyList(),
        )
    }

    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult {
        commits += mutation
        if (failCommits) return ChapterCommitResult.Failure("chapter.storage_commit_failed", true)
        mutation.releases.forEach { release -> releases[release.id.value] = release }
        state = mutation.syncState ?: state
        return ChapterCommitResult.Success
    }

    override suspend fun saveOverride(
        storyId: StoryId,
        override: app.openstory.chapters.model.ChapterAggregationOverride,
    ) = Unit

    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = state
}

private fun release(pluginId: PluginId, sourceReleaseId: String): ChapterRelease {
    val displayLabel = "Chapter 1"
    return ChapterRelease(
        id = ChapterReleaseId("release:$sourceReleaseId"),
        storyId = STORY_ID,
        pluginId = pluginId,
        sourceStoryId = "peer-story",
        sourceReleaseId = sourceReleaseId,
        displayLabel = displayLabel,
        parsedLabel = ChapterLabelParser().parse(displayLabel),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = null,
    )
}

private object FixedMappingRepository : ContentMappingRepository {
    private val mapping = ContentMapping(
        STORY_ID,
        PLUGIN_ID,
        SOURCE_STORY_ID,
        ContentMappingOrigin.USER_APPROVED,
        1,
        1L,
    )

    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = flowOf(listOf(mapping))
    override fun observeAll(): Flow<List<ContentMapping>> = flowOf(listOf(mapping))
    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult = ContentMappingWriteResult.Written(mapping, true)
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean = false
}

private val STORY_ID = StoryId("story:sync")
private val PLUGIN_ID = PluginId("org.example.content")
private const val SOURCE_STORY_ID = "source-story"
