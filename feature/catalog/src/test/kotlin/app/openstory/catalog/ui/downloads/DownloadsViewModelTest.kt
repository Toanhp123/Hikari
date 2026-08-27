package app.openstory.catalog.ui.downloads

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.ContentState
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadFetchResult
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstDownloadSnapshotRendersBeforeChapterAndCatalogMetadata() = runTest(dispatcher) {
        val chapters = MutableSharedFlow<List<CanonicalChapterGroup>>()
        val catalog = MutableSharedFlow<List<CatalogStoryProjection>>()
        val viewModel = viewModel(
            records = MutableStateFlow(listOf(record("queued", DownloadState.QUEUED, 1L))),
            chapterFlow = chapters,
            catalogFlow = catalog,
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        val item = content.active.single()
        assertEquals("queued", item.storyTitle)
        assertEquals("queued", item.chapterLabel)
        assertNull(item.storyId)
    }

    @Test
    fun firstEmptyDownloadSnapshotIsReadyEmpty() = runTest(dispatcher) {
        val viewModel = viewModel(
            records = MutableStateFlow(emptyList()),
            chapterFlow = MutableSharedFlow<List<CanonicalChapterGroup>>(),
            catalogFlow = MutableSharedFlow<List<CatalogStoryProjection>>(),
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        assertEquals(true, content.isEmpty)
    }

    @Test
    fun firstDownloadObservationFailureIsBlockingFailed() = runTest(dispatcher) {
        val repository = repository(
            observe = { flow { throw IllegalStateException("database unavailable") } },
        )
        val viewModel = viewModel(repository = repository)

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("downloads.observe_failed", failed.failure.code)
        assertEquals(true, failed.failure.retryable)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun postValueDownloadFailureRetainsReadyContentAndIssue() = runTest(dispatcher) {
        val repository = repository(
            observe = {
                flow {
                    emit(listOf(record("completed", DownloadState.COMPLETED, 1L)))
                    throw IllegalStateException("database unavailable")
                }
            },
        )
        val viewModel = viewModel(repository = repository)

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        assertEquals(listOf("completed"), content.completed.map { it.releaseId.value })
        assertEquals("downloads.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun chapterMetadataFailureDoesNotRemoveDownloadContent() = runTest(dispatcher) {
        val viewModel = viewModel(
            records = MutableStateFlow(listOf(record("completed", DownloadState.COMPLETED, 1L))),
            chapterFlow = flow { throw IllegalStateException("chapters unavailable") },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        assertEquals("completed", content.completed.single().chapterLabel)
        assertEquals("downloads.chapters.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun catalogMetadataFailureDoesNotRemoveDownloadContent() = runTest(dispatcher) {
        val viewModel = viewModel(
            records = MutableStateFlow(listOf(record("completed", DownloadState.COMPLETED, 1L))),
            chapterFlow = flowOf(listOf(group("completed"))),
            catalogFlow = flow { throw IllegalStateException("catalog unavailable") },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        assertEquals("story", content.completed.single().storyTitle)
        assertEquals("downloads.catalog.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun metadataArrivalEnrichesReadyContentWithoutReturningToPending() = runTest(dispatcher) {
        val chapters = MutableSharedFlow<List<CanonicalChapterGroup>>()
        val catalog = MutableSharedFlow<List<CatalogStoryProjection>>()
        val viewModel = viewModel(
            records = MutableStateFlow(listOf(record("completed", DownloadState.COMPLETED, 1L))),
            chapterFlow = chapters,
            catalogFlow = catalog,
        )
        val states = mutableListOf<DownloadsUiState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        assertTrue(chapters.subscriptionCount.value > 0)
        assertTrue(catalog.subscriptionCount.value > 0)

        val chapterEmission = backgroundScope.launch {
            chapters.emit(listOf(group("completed")))
        }
        runCurrent()
        assertTrue(chapterEmission.isCompleted)

        val catalogEmission = backgroundScope.launch {
            catalog.emit(listOf(projection()))
        }
        runCurrent()
        assertTrue(catalogEmission.isCompleted)

        val readyStates = states.map { it.content }.filterIsInstance<ContentState.Ready<DownloadsContent>>()
        assertEquals("completed", readyStates.first().value.completed.single().chapterLabel)
        assertEquals("Fixture Story", readyStates.last().value.completed.single().storyTitle)
        assertEquals(false, states.dropWhile { it.content !is ContentState.Ready }.any { it.content is ContentState.Pending })
    }

    @Test
    fun workerDrivenRecordUpdateIsReadyToReady() = runTest(dispatcher) {
        val records = MutableStateFlow(listOf(record("queued", DownloadState.QUEUED, 1L)))
        val viewModel = viewModel(records = records)
        val states = mutableListOf<DownloadsUiState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()

        records.value = listOf(record("queued", DownloadState.RUNNING, 2L))
        runCurrent()

        val readyStates = states.map { it.content }.filterIsInstance<ContentState.Ready<DownloadsContent>>()
        assertEquals(DownloadState.QUEUED, readyStates.first().value.active.single().state)
        assertEquals(DownloadState.RUNNING, readyStates.last().value.active.single().state)
        assertEquals(false, states.dropWhile { it.content !is ContentState.Ready }.any { it.content is ContentState.Pending })
    }

    @Test
    fun retryContentRestartsTheDownloadObservationOnly() = runTest(dispatcher) {
        var downloadAttempts = 0
        var chapterAttempts = 0
        var catalogAttempts = 0
        val repository = repository(
            observe = {
                downloadAttempts += 1
                if (downloadAttempts == 1) {
                    flow { throw IllegalStateException("first attempt") }
                } else {
                    flowOf(listOf(record("completed", DownloadState.COMPLETED, 1L)))
                }
            },
        )
        val viewModel = viewModel(
            repository = repository,
            chapterFlow = flow {
                chapterAttempts += 1
                emit(emptyList())
            },
            catalogFlow = flow {
                catalogAttempts += 1
                emit(emptyList())
            },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertIs<ContentState.Failed>(viewModel.state.value.content)

        viewModel.retryContent()
        runCurrent()

        assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content)
        assertEquals(2, downloadAttempts)
        assertEquals(1, chapterAttempts)
        assertEquals(1, catalogAttempts)
    }

    @Test
    fun retryObservationFollowsRenderedIssuePriorityWithoutClearingOtherIssues() = runTest(dispatcher) {
        var downloadAttempts = 0
        var chapterAttempts = 0
        val repository = repository(
            observe = {
                downloadAttempts += 1
                if (downloadAttempts == 1) {
                    flow {
                        emit(listOf(record("completed", DownloadState.COMPLETED, 1L)))
                        throw IllegalStateException("download observation")
                    }
                } else {
                    flowOf(listOf(record("completed", DownloadState.COMPLETED, 1L)))
                }
            },
        )
        val viewModel = viewModel(
            repository = repository,
            chapterFlow = flow {
                chapterAttempts += 1
                if (chapterAttempts == 1) throw IllegalStateException("chapter observation")
                emit(listOf(group("completed")))
            },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertEquals("downloads.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()
        assertEquals(2, downloadAttempts)
        assertEquals(1, chapterAttempts)
        assertEquals("downloads.chapters.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()
        assertEquals(2, chapterAttempts)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun retryObservationTargetsTheSurfacedEnrichmentIssue() = runTest(dispatcher) {
        var chapterAttempts = 0
        val viewModel = viewModel(
            records = MutableStateFlow(listOf(record("completed", DownloadState.COMPLETED, 1L))),
            chapterFlow = flow {
                chapterAttempts += 1
                if (chapterAttempts == 1) throw IllegalStateException("first attempt")
                emit(listOf(group("completed")))
            },
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertEquals("downloads.chapters.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(2, chapterAttempts)
        assertNull(viewModel.state.value.observationIssue)
        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        assertEquals("Chapter completed", content.completed.single().chapterLabel)
    }

    @Test
    fun commandFailureDoesNotOverwriteObservationIssue() = runTest(dispatcher) {
        val repository = repository(
            observe = {
                flow {
                    emit(listOf(record("failed", DownloadState.FAILED, 1L)))
                    throw IllegalStateException("observation failed")
                }
            },
            find = { null },
            save = { throw IllegalStateException("write failed") },
        )
        val viewModel = viewModel(repository = repository)

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        assertEquals("downloads.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retry(ChapterReleaseId("failed"))
        advanceUntilIdle()

        assertEquals("downloads.observe_failed", viewModel.state.value.observationIssue?.code)
        assertEquals("downloads.command_failed", viewModel.state.value.commandFailure?.code)
    }

    @Test
    fun groupsActiveCompletedAndFailedRecordsWhileRetainingMissingMetadata() = runTest(dispatcher) {
        val records = MutableStateFlow(
            listOf(
                record("queued", DownloadState.QUEUED, 4L),
                record("running", DownloadState.RUNNING, 3L),
                record("completed", DownloadState.COMPLETED, 2L, sizeBytes = 2048L),
                record("failed", DownloadState.FAILED, 1L, failure = "network.timeout"),
                record("orphan", DownloadState.COMPLETED, 5L),
                record("cancelled", DownloadState.CANCELLED, 6L),
            ),
        )
        val viewModel = viewModel(
            records = records,
            chapterFlow = flowOf(listOf(group("queued"), group("running"), group("completed"), group("failed"))),
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()

        val content = assertIs<ContentState.Ready<DownloadsContent>>(viewModel.state.value.content).value
        assertEquals(listOf("queued", "running"), content.active.map { it.releaseId.value })
        assertEquals(listOf("orphan", "completed"), content.completed.map { it.releaseId.value })
        assertEquals(listOf("failed"), content.failed.map { it.releaseId.value })
        assertEquals("orphan", content.completed.first().chapterLabel)
        assertEquals("network.timeout", content.failed.single().failureReason)
    }

    @Test
    fun retryQueuesBeforeSchedulingTheExactFailedRelease() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val repository = FakeDownloadsRepository(
            MutableStateFlow(listOf(record("failed", DownloadState.FAILED, 1L))),
        ) { events += "queue:${it.key.releaseId.value}" }
        val viewModel = viewModel(
            repository = repository,
            scheduler = scheduler(schedule = { events += "schedule:${it.value}" }),
        )

        viewModel.retry(ChapterReleaseId("failed"))
        advanceUntilIdle()

        assertEquals(listOf("queue:failed", "schedule:failed"), events)
    }

    @Test
    fun cancelStopsSchedulerBeforeServiceCancellationAndRemovalWaitsForConfirmation() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val repository = FakeDownloadsRepository(
            MutableStateFlow(listOf(record("running", DownloadState.RUNNING, 1L))),
        ) { saved ->
            if (saved.state == DownloadState.CANCELLED) events += "service:${saved.key.releaseId.value}"
        }
        val viewModel = viewModel(
            repository = repository,
            scheduler = scheduler(cancel = { events += "scheduler:${it.value}" }),
        )

        viewModel.state.collectForTest(backgroundScope)
        runCurrent()
        viewModel.requestRemoval(ChapterReleaseId("running"))
        runCurrent()
        assertEquals(ChapterReleaseId("running"), viewModel.state.value.pendingRemoval)
        viewModel.confirmRemoval()
        advanceUntilIdle()

        assertEquals(listOf("scheduler:running", "service:running"), events)
        assertNull(viewModel.state.value.pendingRemoval)
    }

    private fun viewModel(
        records: MutableStateFlow<List<DownloadRecord>> = MutableStateFlow(emptyList()),
        repository: DownloadRepository = FakeDownloadsRepository(records),
        chapterFlow: Flow<List<CanonicalChapterGroup>> = flowOf(emptyList()),
        catalogFlow: Flow<List<CatalogStoryProjection>> = flowOf(listOf(projection())),
        scheduler: DownloadScheduler = scheduler(),
    ) = DownloadsViewModel(
        repository = repository,
        service = DownloadService(
            repository,
            EmptyBlobStore,
            DownloadContentSource { DownloadFetchResult.Failure("unused", false) },
        ),
        scheduler = scheduler,
        chapters = FakeChapterRepository(chapterFlow),
        catalog = object : CatalogStoryProjectionRepository {
            override fun observe() = catalogFlow
        },
        clock = Clock { 99L },
    )
}

private fun kotlinx.coroutines.flow.StateFlow<*>.collectForTest(scope: kotlinx.coroutines.CoroutineScope) =
    scope.launch { collect {} }

private fun repository(
    observe: () -> Flow<List<DownloadRecord>>,
    find: suspend (ChapterReleaseId) -> DownloadRecord? = { null },
    save: suspend (DownloadRecord) -> Unit = {},
) = object : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadRecord>> = observe()
    override suspend fun find(releaseId: ChapterReleaseId) = find(releaseId)
    override fun observe(releaseId: ChapterReleaseId) = MutableStateFlow<DownloadRecord?>(null)
    override suspend fun save(record: DownloadRecord) = save(record)
}

private class FakeDownloadsRepository(
    private val records: MutableStateFlow<List<DownloadRecord>>,
    private val onSave: (DownloadRecord) -> Unit = {},
) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadRecord>> = records
    override suspend fun find(releaseId: ChapterReleaseId) = records.value.find { it.key.releaseId == releaseId }
    override fun observe(releaseId: ChapterReleaseId) = MutableStateFlow(records.value.find { it.key.releaseId == releaseId })
    override suspend fun save(record: DownloadRecord) {
        records.value = records.value.filterNot { it.key.releaseId == record.key.releaseId } + record
        onSave(record)
    }
}

private class FakeChapterRepository(
    private val groups: Flow<List<CanonicalChapterGroup>>,
) : ChapterRepository {
    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = groups
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = groups
    override suspend fun snapshot(storyId: StoryId) = error("unused")
    override suspend fun commit(mutation: app.openstory.chapters.repository.ChapterMutation) = error("unused")
    override suspend fun saveOverride(storyId: StoryId, override: app.openstory.chapters.model.ChapterAggregationOverride) = error("unused")
    override suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String) = null
}

private object EmptyBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}

private fun scheduler(
    schedule: (ChapterReleaseId) -> Unit = {},
    cancel: (ChapterReleaseId) -> Unit = {},
) = object : DownloadScheduler {
    override fun schedule(releaseId: ChapterReleaseId) = schedule(releaseId)
    override fun cancel(releaseId: ChapterReleaseId) = cancel(releaseId)
}

private fun record(
    id: String,
    state: DownloadState,
    updatedAt: Long,
    sizeBytes: Long = 0L,
    failure: String? = null,
) = DownloadRecord(
    ChapterBlobKey(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, ChapterReleaseId(id), "fixture"),
    state,
    sizeBytes = sizeBytes,
    failureReason = failure,
    updatedAtEpochMillis = updatedAt,
)

private fun group(id: String): CanonicalChapterGroup {
    val storyId = StoryId("story")
    val chapterId = CanonicalChapterId("chapter-$id")
    val releaseId = ChapterReleaseId(id)
    val parsed = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal.ONE, null, null)
    return CanonicalChapterGroup(
        CanonicalChapter(chapterId, storyId, parsed, "Chapter $id", false, setOf(releaseId)),
        listOf(
            ChapterRelease(
                releaseId,
                storyId,
                PluginId("content.fixture"),
                "source-story",
                "source-$id",
                "Chapter $id",
                parsed,
                "en",
                10L,
                chapterId,
            ),
        ),
    )
}

private fun projection() = CatalogStoryProjection(
    StoryId("story"),
    "Fixture Story",
    ContentType.WEB_NOVEL,
    null,
)
