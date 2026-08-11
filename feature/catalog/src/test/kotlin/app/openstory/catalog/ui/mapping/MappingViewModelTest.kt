package app.openstory.catalog.ui.mapping

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.search()
        runCurrent()

        val candidate = viewModel.state.value.candidates.single()
        assertTrue(candidate.evidenceLabels.any { it.startsWith("Title ") })
        assertFalse(candidate.fromUrl)

        viewModel.approve(candidate.pluginId, candidate.sourceStoryId)
        runCurrent()

        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals(ContentMappingOrigin.USER_APPROVED, viewModel.state.value.mappings.single().origin)
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

        assertEquals(ContentMappingOrigin.USER_URL, viewModel.state.value.mappings.single().origin)
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

        assertEquals(listOf("content.url_invalid"), viewModel.state.value.failures)
        assertTrue(viewModel.state.value.candidates.isEmpty())
        assertEquals(0, source.resolveCalls)
    }
}

private val STORY_ID = StoryId("story:mapping-ui")
private val PLUGIN_ID = PluginId("org.example.reader")

private fun viewModel(
    repository: FakeMappingRepository,
    source: FakeContentSource = FakeContentSource(),
): MappingViewModel {
    val search = ContentMappingSearchService(
        projections = FakeProjectionRepository,
        sources = FakeRegistry(source),
        matcher = ContentStoryMatcher(),
        policy = ContentMappingSearchPolicy(quickSourceCount = 1, maxQueryVariants = 1),
    )
    val service = ContentMappingService(repository, search, FakeClock(100L))
    return MappingViewModel(MappingAssistedArgs(STORY_ID), service)
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

private class FakeContentSource : ContentSource {
    override val pluginId = PLUGIN_ID
    override val version = "1.0.0"
    override val allowedHosts = setOf("reader.example")
    var resolveCalls = 0
        private set

    override suspend fun search(
        query: String,
        limit: Int,
    ): ContentSourceResult<List<ContentSourceStory>> = ContentSourceResult.Success(listOf(story()))

    override suspend fun resolveUrl(url: String): ContentSourceResult<ContentSourceStory> {
        resolveCalls += 1
        return ContentSourceResult.Success(story())
    }

    private fun story() = ContentSourceStory(
        sourceStoryId = "source-1",
        title = "The Story",
        aliases = setOf("Story"),
        authors = setOf("Author"),
        contentType = ContentType.WEB_NOVEL,
        sourceUrl = "https://reader.example/story/source-1",
    )
}

private class FakeMappingRepository : ContentMappingRepository {
    private val current = MutableStateFlow<List<ContentMapping>>(emptyList())
    val rejections = mutableSetOf<ContentMappingRejection>()

    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = current

    override fun observeAll(): Flow<List<ContentMapping>> = current

    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult {
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
