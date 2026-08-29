package app.openstory.catalog.ui.mapping

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.ContentState
import app.openstory.chapters.sync.InitialChapterSyncScheduler
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.content.ContentSource
import app.openstory.library.content.ContentSourceRegistry
import app.openstory.library.content.ContentSourceResult
import app.openstory.library.content.ContentSourceStory
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingSearchPolicy
import app.openstory.library.mapping.ContentMappingSearchService
import app.openstory.library.mapping.ContentMappingService
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.library.matching.ContentStoryMatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MappingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchExposesEvidenceAndApprovalUpdatesMappingImmediately() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val scheduler = RecordingChapterSyncScheduler()
        val viewModel = viewModel(repository, scheduler = scheduler)
        assertIs<ContentState.Pending>(viewModel.state.value.content)
        runCurrent()
        assertIs<ContentState.Ready<List<MappingItemUiModel>>>(viewModel.state.value.content)

        viewModel.search()
        runCurrent()

        val candidate = viewModel.state.value.candidates.single()
        assertTrue(candidate.evidenceLabels.any { it.startsWith("Title ") })
        assertFalse(candidate.fromUrl)

        viewModel.approve(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()

        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals(ContentMappingOrigin.USER_APPROVED, viewModel.state.value.readyMappings().single().origin)
        assertEquals(listOf(STORY_ID), scheduler.scheduled)
    }

    @Test
    fun successfulApprovalEmitsLinkedEvent() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.search()
        runCurrent()
        val candidate = viewModel.state.value.candidates.single()
        val event = async { viewModel.events.first() }
        runCurrent()

        viewModel.approve(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()

        assertEquals(MappingEvent.SOURCE_LINKED, event.await())
    }

    @Test
    fun staleAlreadyLinkedApprovalDoesNotScheduleChapterSyncAgain() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val scheduler = RecordingChapterSyncScheduler()
        val viewModel = viewModel(repository, scheduler = scheduler)
        runCurrent()
        viewModel.search()
        runCurrent()
        val candidate = viewModel.state.value.candidates.single()
        repository.seed(
            ContentMapping(
                storyId = STORY_ID,
                pluginId = candidate.pluginId,
                sourceStoryId = candidate.sourceStoryId,
                origin = ContentMappingOrigin.USER_APPROVED,
                policyVersion = 1,
                updatedAt = 100L,
            ),
        )
        runCurrent()
        val event = async { viewModel.events.first() }
        runCurrent()

        viewModel.approve(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(MappingEvent.SOURCE_ALREADY_LINKED, event.await())
    }

    @Test
    fun approvalInvalidatesSiblingCandidatesAndLaterSearchMarksReplacement() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val scheduler = RecordingChapterSyncScheduler()
        val source = FakeContentSource(sourceStoryIds = listOf("source-1", "source-2"))
        val viewModel = viewModel(repository, source, scheduler)
        runCurrent()

        viewModel.search()
        runCurrent()
        assertEquals(
            listOf("source-1", "source-2"),
            viewModel.state.value.candidates.map { it.sourceStoryId }.sorted(),
        )

        val first = viewModel.state.value.candidates.first { it.sourceStoryId == "source-1" }
        viewModel.approve(first.pluginId, first.sourceStoryId)
        runCurrent()

        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals("source-1", viewModel.state.value.readyMappings().single().sourceStoryId)
        assertEquals(listOf(STORY_ID), scheduler.scheduled)

        viewModel.search()
        runCurrent()

        val replacement = viewModel.state.value.candidates.single()
        assertEquals("source-2", replacement.sourceStoryId)
        assertEquals("source-1", replacement.replacesSourceStoryId)

        val replacementEvent = async { viewModel.events.first() }
        runCurrent()
        viewModel.approve(replacement.pluginId, replacement.sourceStoryId)
        runCurrent()

        assertEquals(MappingEvent.SOURCE_REPLACED, replacementEvent.await())
        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals("source-2", viewModel.state.value.readyMappings().single().sourceStoryId)
        assertEquals(listOf(STORY_ID, STORY_ID), scheduler.scheduled)

        viewModel.search()
        runCurrent()

        val reverseReplacement = viewModel.state.value.candidates.single()
        assertEquals("source-1", reverseReplacement.sourceStoryId)
        assertEquals("source-2", reverseReplacement.replacesSourceStoryId)
    }

    @Test
    fun secondApprovalIsIgnoredWhileFirstMappingWriteIsPending() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val source = FakeContentSource(sourceStoryIds = listOf("source-1", "source-2"))
        val viewModel = viewModel(repository, source)
        runCurrent()
        viewModel.search()
        runCurrent()

        viewModel.approve(PLUGIN_ID, "source-1")
        viewModel.approve(PLUGIN_ID, "source-2")
        runCurrent()

        assertEquals("source-1", viewModel.state.value.readyMappings().single().sourceStoryId)
        assertTrue(viewModel.state.value.candidates.isEmpty())
    }

    @Test
    fun rejectionRemovesCandidateAndSuppressesSamePolicySearch() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.search()
        runCurrent()
        val candidate = viewModel.state.value.candidates.single()
        viewModel.reject(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()
        viewModel.search()
        runCurrent()

        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals(1, repository.rejections.size)
    }

    @Test
    fun resolvedUrlUsesUrlProtectedOrigin() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.updateUrl("https://reader.example/story/source-1")
        viewModel.resolveUrl()
        runCurrent()

        val candidate = viewModel.state.value.candidates.single()
        assertTrue(candidate.fromUrl)
        viewModel.approve(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()

        assertEquals(ContentMappingOrigin.USER_URL, viewModel.state.value.readyMappings().single().origin)
    }

    @Test
    fun invalidUrlReportsHostSafeFailureWithoutCandidate() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val source = FakeContentSource()
        val viewModel = viewModel(repository, source)
        runCurrent()

        viewModel.updateUrl("http://reader.example/story/source-1")
        viewModel.resolveUrl()
        runCurrent()

        assertEquals(listOf("content.url_invalid"), viewModel.state.value.searchFailures.map { it.code })
        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals(0, source.resolveCalls)

        viewModel.updateUrl("https://reader.example/story/source-1")
        runCurrent()

        assertTrue(viewModel.state.value.searchFailures.isEmpty())
    }

    @Test
    fun editingUrlAfterUrlResultClearsOnlyUrlOwnedSearchOutput() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            writeFailuresRemaining = 1
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.search()
        runCurrent()
        val discoveryCandidate = viewModel.state.value.candidates.single()
        viewModel.approve(discoveryCandidate.pluginId, discoveryCandidate.sourceStoryId)
        runCurrent()
        assertEquals("library.mapping.action_failed", viewModel.state.value.actionFailure?.code)

        viewModel.updateUrl("https://reader.example/story/source-1")
        viewModel.resolveUrl()
        runCurrent()
        assertTrue(viewModel.state.value.candidates.single().fromUrl)

        viewModel.updateUrl("https://reader.example/story/source-2")
        runCurrent()

        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertTrue(viewModel.state.value.searchFailures.isEmpty())
        assertEquals("library.mapping.action_failed", viewModel.state.value.actionFailure?.code)
    }

    @Test
    fun editingUrlDuringResolveDiscardsSupersededUrlCompletion() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository()
        val gate = CompletableDeferred<Unit>()
        val source = FakeContentSource().apply { resolveGate = gate }
        val viewModel = viewModel(repository, source)
        runCurrent()

        viewModel.updateUrl("https://reader.example/story/source-1")
        viewModel.resolveUrl()
        runCurrent()
        assertTrue(viewModel.state.value.busy)
        assertEquals(1, source.resolveCalls)

        viewModel.updateUrl("https://reader.example/story/source-2")
        runCurrent()
        assertFalse(viewModel.state.value.busy)
        source.resolveGate = null
        viewModel.resolveUrl()
        runCurrent()

        assertEquals("https://reader.example/story/source-2", viewModel.state.value.urlInput)
        assertFalse(viewModel.state.value.busy)
        assertEquals(2, source.resolveCalls)
        assertTrue(viewModel.state.value.candidates.single().fromUrl)
        assertTrue(viewModel.state.value.searchFailures.isEmpty())
        assertFalse(gate.isCompleted)
    }

    @Test
    fun firstObservationFailureIsBlockingFailedInsteadOfAuthoritativeEmpty() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            observationFailuresBeforeEmission = 1
        }
        val viewModel = viewModel(repository)

        runCurrent()

        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("library.mapping.observe_failed", failed.failure.code)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun postSnapshotObservationFailureRetainsMappingsAndSurfacesObservationIssue() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            seed(existingMapping("linked"))
            failObservationAfterSnapshot = true
        }
        val viewModel = viewModel(repository)

        runCurrent()

        val ready = assertIs<ContentState.Ready<List<MappingItemUiModel>>>(viewModel.state.value.content)
        assertEquals("linked", ready.value.single().sourceStoryId)
        assertEquals("library.mapping.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun retryObservationResubscribesAndClearsIssueOnlyAfterSuccessfulSnapshot() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            observationFailuresBeforeEmission = 1
        }
        val viewModel = viewModel(repository)
        runCurrent()
        assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals(1, repository.observeCalls)

        viewModel.retryObservation()
        assertIs<ContentState.Failed>(viewModel.state.value.content)
        runCurrent()

        val ready = assertIs<ContentState.Ready<List<MappingItemUiModel>>>(viewModel.state.value.content)
        assertTrue(ready.value.isEmpty())
        assertNull(viewModel.state.value.observationIssue)
        assertEquals(2, repository.observeCalls)
    }

    @Test
    fun mappingCommandsWaitForAuthoritativeObservationSnapshot() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            observationPending = true
        }
        val source = FakeContentSource()
        val viewModel = viewModel(repository, source)
        runCurrent()
        assertIs<ContentState.Pending>(viewModel.state.value.content)

        viewModel.search()
        viewModel.updateUrl("https://reader.example/story/source-1")
        viewModel.resolveUrl()
        runCurrent()

        assertEquals(0, source.searchCalls)
        assertEquals(0, source.resolveCalls)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun commandAttemptsDoNotClearRetainedObservationIssue() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            failObservationAfterSnapshot = true
        }
        val viewModel = viewModel(repository)
        runCurrent()
        assertEquals("library.mapping.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.search()
        runCurrent()

        assertEquals("library.mapping.observe_failed", viewModel.state.value.observationIssue?.code)
        assertTrue(viewModel.state.value.candidates.isNotEmpty())
    }

    @Test
    fun searchAndActionFailureChannelsDoNotClearEachOther() = runTest(dispatcher.scheduler) {
        val repository = FakeMappingRepository().apply {
            writeFailuresRemaining = 1
        }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.search()
        runCurrent()
        val candidate = viewModel.state.value.candidates.single()

        viewModel.approve(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()
        assertEquals("library.mapping.action_failed", viewModel.state.value.actionFailure?.code)

        viewModel.updateUrl("http://reader.example/story/source-1")
        viewModel.resolveUrl()
        runCurrent()

        assertEquals(listOf("content.url_invalid"), viewModel.state.value.searchFailures.map { it.code })
        assertEquals("library.mapping.action_failed", viewModel.state.value.actionFailure?.code)
    }

}

private val STORY_ID = StoryId("story:mapping-ui")
private val PLUGIN_ID = PluginId("org.example.reader")

private fun TestScope.viewModel(
    repository: FakeMappingRepository,
    source: FakeContentSource = FakeContentSource(),
    scheduler: InitialChapterSyncScheduler = RecordingChapterSyncScheduler(),
): MappingViewModel {
    val search = ContentMappingSearchService(
        projections = FakeProjectionRepository,
        sources = FakeRegistry(source),
        matcher = ContentStoryMatcher(),
        policy = ContentMappingSearchPolicy(quickSourceCount = 1, maxQueryVariants = 1),
    )
    val service = ContentMappingService(repository, search, FakeClock(100L))
    return MappingViewModel(MappingAssistedArgs(STORY_ID), service, scheduler).also { viewModel ->
        backgroundScope.launch { viewModel.state.collect {} }
    }
}

private class RecordingChapterSyncScheduler : InitialChapterSyncScheduler {
    val scheduled = mutableListOf<StoryId>()

    override fun schedule(storyId: StoryId) {
        scheduled += storyId
    }
}

private object FakeProjectionRepository : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(
        listOf(
            CatalogStoryProjection(
                storyId = STORY_ID,
                title = "The Story",
                contentType = ContentType.WEB_NOVEL,
                coverUrl = null,
                aliases = setOf("Story"),
                authors = setOf("Author"),
            ),
        ),
    )
}

private class FakeRegistry(private val source: ContentSource) : ContentSourceRegistry {
    override suspend fun enabled(): List<ContentSource> = listOf(source)
}

private class FakeContentSource(
    private val sourceStoryIds: List<String> = listOf("source-1"),
) : ContentSource {
    override val pluginId = PLUGIN_ID
    override val version = "1.0.0"
    override val allowedHosts = setOf("reader.example")
    var searchCalls = 0
        private set
    var resolveCalls = 0
        private set
    var resolveGate: CompletableDeferred<Unit>? = null

    override suspend fun search(
        query: String,
        limit: Int,
    ): ContentSourceResult<List<ContentSourceStory>> {
        searchCalls += 1
        return ContentSourceResult.Success(sourceStoryIds.map(::story))
    }

    override suspend fun resolveUrl(url: String): ContentSourceResult<ContentSourceStory> {
        resolveCalls += 1
        resolveGate?.await()
        return ContentSourceResult.Success(story(sourceStoryIds.first()))
    }

    private fun story(sourceStoryId: String) = ContentSourceStory(
        sourceStoryId = sourceStoryId,
        title = "The Story",
        aliases = setOf("Story"),
        authors = setOf("Author"),
        contentType = ContentType.WEB_NOVEL,
        sourceUrl = "https://reader.example/story/$sourceStoryId",
    )
}

private class FakeMappingRepository : ContentMappingRepository {
    private val current = MutableStateFlow<List<ContentMapping>>(emptyList())

    var observationFailuresBeforeEmission: Int = 0
    var failObservationAfterSnapshot: Boolean = false
    var observationPending: Boolean = false
    var writeFailuresRemaining: Int = 0
    var observeCalls: Int = 0
        private set

    fun seed(mapping: ContentMapping) {
        current.value = current.value.filterNot { existing ->
            existing.storyId == mapping.storyId && existing.pluginId == mapping.pluginId
        } + mapping
    }

    val rejections = mutableSetOf<ContentMappingRejection>()

    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> {
        observeCalls += 1
        if (observationPending) return flow { awaitCancellation() }
        if (observationFailuresBeforeEmission > 0) {
            observationFailuresBeforeEmission -= 1
            return flow { error("mapping observation unavailable") }
        }
        if (failObservationAfterSnapshot) {
            return flow {
                emit(current.value)
                error("mapping observation unavailable after snapshot")
            }
        }
        return current
    }

    override fun observeAll(): Flow<List<ContentMapping>> = current

    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult {
        if (writeFailuresRemaining > 0) {
            writeFailuresRemaining -= 1
            error("mapping write unavailable")
        }
        val existing = current.value.firstOrNull { it.storyId == mapping.storyId && it.pluginId == mapping.pluginId }
        return if (existing != null && existing.origin !in replaceableOrigins) {
            ContentMappingWriteResult.Protected(existing)
        } else {
            current.value = current.value.filterNot {
                it.storyId == mapping.storyId && it.pluginId == mapping.pluginId
            } + mapping
            ContentMappingWriteResult.Written(mapping, changed = existing != mapping)
        }
    }

    override suspend fun reject(rejection: ContentMappingRejection) {
        rejections += rejection
    }

    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean = rejections.any { rejection ->
        rejection.storyId == storyId &&
            rejection.pluginId == pluginId &&
            rejection.sourceStoryId == sourceStoryId &&
            rejection.policyVersion == policyVersion
    }
}

private fun MappingUiState.readyMappings(): List<MappingItemUiModel> =
    assertIs<ContentState.Ready<List<MappingItemUiModel>>>(content).value

private fun existingMapping(sourceStoryId: String) = ContentMapping(
    storyId = STORY_ID,
    pluginId = PLUGIN_ID,
    sourceStoryId = sourceStoryId,
    origin = ContentMappingOrigin.USER_APPROVED,
    policyVersion = 1,
    updatedAt = 100L,
)
