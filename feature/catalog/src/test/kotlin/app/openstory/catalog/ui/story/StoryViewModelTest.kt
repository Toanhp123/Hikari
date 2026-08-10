package app.openstory.catalog.ui.story

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.details.CatalogDetailsService
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class StoryViewModelTest {
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
    fun cachedStoryRendersWithoutSourceRefresh() = runTest(dispatcher.scheduler) {
        val repository = StoryRepository(fixtureSnapshot())
        val source = DetailsSource("catalog.a")
        val viewModel = viewModel(repository, source)
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.story?.preferredTitle)
        assertEquals(0, source.detailsCalls)
    }

    @Test
    fun retryRefreshesExactAvailableSourceSelectedByCatalogState() = runTest(dispatcher.scheduler) {
        val repository = StoryRepository(fixtureSnapshot(includeSecondSource = true))
        val sourceA = DetailsSource("catalog.a")
        val sourceB = DetailsSource("catalog.b")
        val viewModel = viewModel(repository, sourceA, sourceB)
        runCurrent()

        viewModel.selectSource(PluginId("catalog.b"), "source-b")
        viewModel.retry()
        runCurrent()

        assertEquals(0, sourceA.detailsCalls)
        assertEquals(1, sourceB.detailsCalls)
        assertEquals("source-b", sourceB.lastSourceId)
    }

    @Test
    fun sourceDetailFailureDoesNotChangeCanonicalStoryId() = runTest(dispatcher.scheduler) {
        val canonicalId = StoryId("story-1")
        val repository = StoryRepository(fixtureSnapshot())
        val source = DetailsSource("catalog.a").apply {
            result = CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true))
        }
        val viewModel = viewModel(repository, source, storyId = canonicalId)
        runCurrent()

        viewModel.retry()
        runCurrent()

        assertEquals(canonicalId, viewModel.state.value.storyId)
        assertEquals(canonicalId, viewModel.state.value.story?.storyId)
        assertEquals("catalog.offline", viewModel.state.value.failure?.code)
    }

    @Test
    fun repositoryExceptionDuringRefreshBecomesRetryableFailure() = runTest(dispatcher.scheduler) {
        val repository = StoryRepository(fixtureSnapshot(), matchFailuresRemaining = 1)
        val viewModel = viewModel(repository, DetailsSource("catalog.a"))
        runCurrent()

        viewModel.retry()
        runCurrent()

        assertEquals("catalog.story.refresh_exception", viewModel.state.value.failure?.code)
        assertFalse(viewModel.state.value.refreshing)
        assertEquals(StoryId("story-1"), viewModel.state.value.storyId)
    }

    @Test
    fun viewModelHasNoPluginRuntimeOrRoomDependency() {
        val dependencyNames = StoryViewModel::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.map(Class<*>::getName) }

        assertFalse(dependencyNames.any { "plugins.runtime" in it })
        assertFalse(dependencyNames.any { "room" in it.lowercase() })
    }

    private fun viewModel(
        repository: StoryRepository,
        vararg sources: DetailsSource,
        storyId: StoryId = StoryId("story-1"),
    ): StoryViewModel {
        val details = CatalogDetailsService(
            DetailsRegistry(sources.toList()),
            repository,
            StoryMatcher(),
            Clock { 100L },
        )
        return StoryViewModel(StoryAssistedArgs(storyId), repository, details)
    }
}

private class DetailsRegistry(private val sources: List<CatalogSource>) : CatalogSourceRegistry {
    override suspend fun enabled() = sources
    override suspend fun source(pluginId: PluginId) = sources.firstOrNull { it.pluginId == pluginId }
}

private class DetailsSource(id: String) : CatalogSource {
    override val pluginId = PluginId(id)
    override val version = "1.0.0"
    var detailsCalls = 0
    var lastSourceId: String? = null
    var result: CatalogSourceResult<SourceDetails> = successDetails("source-a")

    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
        detailsCalls++
        lastSourceId = sourceId
        return when (val current = result) {
            is CatalogSourceResult.Failure -> current
            is CatalogSourceResult.Success -> CatalogSourceResult.Success(current.value.copy(sourceId = sourceId))
        }
    }

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("unused")
    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> = error("unused")
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
}

private class StoryRepository(
    initial: StoryCatalogSnapshot,
    private var matchFailuresRemaining: Int = 0,
) : CatalogRepository {
    private val story = MutableStateFlow<StoryCatalogSnapshot?>(initial)

    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = story
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = error("unused")
    override suspend fun matchSnapshot(): CatalogMatchSnapshot {
        if (matchFailuresRemaining > 0) {
            matchFailuresRemaining--
            error("catalog unavailable")
        }
        val snapshot = assertNotNull(story.value)
        return CatalogMatchSnapshot(
            listOf(
                CatalogMatchCandidate(
                    snapshot.story,
                    snapshot.entries.flatMap { setOf(it.title) + it.aliases }.toSet(),
                    snapshot.entries.flatMap { it.authors }.toSet(),
                    snapshot.entries.map { SourceKey(it.pluginId, it.sourceId) }.toSet(),
                ),
            ),
        )
    }

    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<StoryId, CatalogStoreFailure> {
        val current = assertNotNull(story.value)
        story.value = current.copy(
            entries = current.entries.filterNot {
                it.pluginId == mutation.entry.pluginId && it.sourceId == mutation.entry.sourceId
            } + mutation.entry.copy(storyId = current.story.id),
        )
        return Outcome.Success(current.story.id)
    }

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<Unit, CatalogStoreFailure> = error("unused")
}

private fun fixtureSnapshot(includeSecondSource: Boolean = false): StoryCatalogSnapshot {
    val storyId = StoryId("story-1")
    val entries = buildList {
        add(fixtureEntry(storyId, "catalog.a", "source-a", "Fixture Novel"))
        if (includeSecondSource) {
            add(fixtureEntry(storyId, "catalog.b", "source-b", "Fixture Novel B"))
        }
    }
    return StoryCatalogSnapshot(Story(storyId, ContentType.WEB_NOVEL), entries)
}

private fun fixtureEntry(
    storyId: StoryId,
    pluginId: String,
    sourceId: String,
    title: String,
) = CatalogEntry(
    storyId = storyId,
    pluginId = PluginId(pluginId),
    sourceId = sourceId,
    title = title,
    authors = setOf("Fixture Author"),
    contentType = ContentType.WEB_NOVEL,
)

private fun successDetails(sourceId: String) = CatalogSourceResult.Success(
    SourceDetails(
        sourceId = sourceId,
        sourceUrl = "https://example.test/$sourceId",
        title = "Fixture Novel",
        aliases = emptySet(),
        authors = setOf("Fixture Author"),
        description = "Fixture description",
        genres = setOf("Fantasy"),
        contentType = app.openstory.catalog.source.SourceContentType.WEB_NOVEL,
        languageTags = setOf("en"),
        coverUrl = null,
        scoreValue = 8.4,
        scoreScale = 10.0,
        popularityRank = 1L,
    ),
)
