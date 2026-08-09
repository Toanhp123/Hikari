package app.openstory.story.ui

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.AppResult
import app.openstory.database.repository.CatalogRepository
import app.openstory.database.repository.LocalStoryRepository
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.ChapterRelease
import app.openstory.model.ContentMappingId
import app.openstory.model.ContentType
import app.openstory.model.LibraryEntry
import app.openstory.model.LibraryStatus
import app.openstory.model.PluginId
import app.openstory.model.ReadingProgress
import app.openstory.model.StoryId
import app.openstory.story.domain.CatalogDetailsMapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openingDetailsEnrichesExactSourceThroughCatalogRepository() = runTest(mainDispatcher.scheduler) {
        val story = fixtureStory()
        val storyRepository = FakeStoryRepository(story)
        val catalogRepository = FakeCatalogRepository(story.id)
        val plugin = RecordingDetailsPlugin(
            hostedId = PluginId("catalog.a"),
            details = fixtureDetails(),
        )
        val viewModel = StoryDetailViewModel(
            request = StoryDetailRequest(
                storyId = story.id,
                pluginId = PluginId("catalog.a"),
                sourceId = "source-a",
            ),
            storyRepository = storyRepository,
            catalogRepository = catalogRepository,
            sources = FakeCatalogSourceRegistry(plugin.source),
            detailsMapper = CatalogDetailsMapper(),
            scope = backgroundScope,
        )

        runCurrent()

        assertEquals(listOf("source-a"), plugin.requestedSourceIds)
        assertEquals(1, catalogRepository.upserts.size)
        val upsert = catalogRepository.upserts.single()
        assertEquals(PluginId("catalog.a"), upsert.pluginId)
        assertEquals("3.2.1", upsert.pluginVersion)
        assertEquals("https://catalog.example/source-a", upsert.metadata.sourceUrl)
        assertEquals(setOf("Alias A"), upsert.metadata.aliases)
        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun detailStateKeepsSourceScoresScalesAndTimestampsDistinct() = runTest(mainDispatcher.scheduler) {
        val story = fixtureStory()
        val viewModel = StoryDetailViewModel(
            storyFlow = MutableStateFlow(story),
            enrichAction = { AppResult.Success(Unit) },
            scope = backgroundScope,
        )

        runCurrent()

        val sources = viewModel.state.value.story?.sources.orEmpty()
        assertEquals(2, sources.size)
        assertEquals(
            setOf(
                Triple("catalog.a", 8.0, 10.0),
                Triple("catalog.b", 92.0, 100.0),
            ),
            sources.map { source -> Triple(source.pluginId.value, source.score, source.scoreScale) }.toSet(),
        )
        assertEquals(setOf(100L, 200L), sources.map { it.fetchedAtEpochMillis }.toSet())
    }

    @Test
    fun cachedStoryRemainsVisibleWhileDetailRefreshIsRunning() = runTest(mainDispatcher.scheduler) {
        val story = fixtureStory()
        val allowRefreshToFinish = CompletableDeferred<Unit>()
        val viewModel = StoryDetailViewModel(
            storyFlow = MutableStateFlow(story),
            enrichAction = {
                allowRefreshToFinish.await()
                AppResult.Success(Unit)
            },
            scope = backgroundScope,
        )

        runCurrent()

        assertTrue(viewModel.state.value.loading)
        assertEquals(story.id, viewModel.state.value.story?.storyId)

        allowRefreshToFinish.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals(story.id, viewModel.state.value.story?.storyId)
    }

    @Test
    fun detailFollowsCanonicalStoryIdReturnedByMetadataUpsert() = runTest(mainDispatcher.scheduler) {
        val resolvedStory = fixtureStory().copy(id = StoryId("story-resolved-by-database"))
        val viewModel = StoryDetailViewModel(
            request = StoryDetailRequest(
                storyId = StoryId("story-derived-by-search"),
                pluginId = PluginId("catalog.a"),
                sourceId = "source-a",
            ),
            storyRepository = FakeStoryRepository(resolvedStory),
            catalogRepository = FakeCatalogRepository(resolvedStory.id),
            sources = FakeCatalogSourceRegistry(
                RecordingDetailsPlugin(
                    hostedId = PluginId("catalog.a"),
                    details = fixtureDetails(),
                ).source,
            ),
            detailsMapper = CatalogDetailsMapper(),
            scope = backgroundScope,
        )

        runCurrent()

        assertEquals(resolvedStory.id, viewModel.state.value.story?.storyId)
    }

    @Test
    fun mismatchedDetailSourceIdFailsWithoutPersistingAnotherSource() = runTest(mainDispatcher.scheduler) {
        val story = fixtureStory()
        val catalogRepository = FakeCatalogRepository(story.id)
        val viewModel = StoryDetailViewModel(
            request = StoryDetailRequest(
                storyId = story.id,
                pluginId = PluginId("catalog.a"),
                sourceId = "source-a",
            ),
            storyRepository = FakeStoryRepository(story),
            catalogRepository = catalogRepository,
            sources = FakeCatalogSourceRegistry(
                RecordingDetailsPlugin(
                    hostedId = PluginId("catalog.a"),
                    details = fixtureDetails().copy(sourceId = "source-b"),
                ).source,
            ),
            detailsMapper = CatalogDetailsMapper(),
            scope = backgroundScope,
        )

        runCurrent()

        assertTrue(catalogRepository.upserts.isEmpty())
        assertEquals("catalog.details_source_mismatch", viewModel.state.value.error?.code)
    }
}

private class FakeStoryRepository(
    story: CanonicalStory,
) : LocalStoryRepository {
    private val stories = mapOf(story.id to story)

    override fun observeStory(id: StoryId): Flow<CanonicalStory?> = MutableStateFlow(stories[id])
    override fun observeLibrary(): Flow<List<LibraryEntry>> = emptyFlow()
    override suspend fun addToLibrary(story: CanonicalStory, status: LibraryStatus): AppResult<Unit> = error("Not used")
    override suspend fun purgeStory(storyId: StoryId): AppResult<Unit> = error("Not used")
    override suspend fun replaceSourceReleases(
        mappingId: ContentMappingId,
        releases: List<ChapterRelease>,
    ): AppResult<Unit> = error("Not used")

    override suspend fun upsertProgress(progress: ReadingProgress): AppResult<Unit> = error("Not used")
}

private class FakeCatalogRepository(
    private val storyId: StoryId,
) : CatalogRepository {
    data class Upsert(
        val pluginId: PluginId,
        val pluginVersion: String,
        val metadata: CatalogSourceMetadata,
    )

    val upserts = mutableListOf<Upsert>()

    override suspend fun ingest(snapshot: CatalogSnapshot): AppResult<Unit> = error("Not used")

    override suspend fun upsertSourceMetadata(
        pluginId: PluginId,
        pluginVersion: String,
        metadata: CatalogSourceMetadata,
    ): AppResult<CatalogEntryWithStory> {
        upserts += Upsert(pluginId, pluginVersion, metadata)
        return AppResult.Success(
            CatalogEntryWithStory(
                storyId = storyId,
                entry = fixtureEntry(
                    pluginId = pluginId.value,
                    sourceId = metadata.sourceId,
                    score = metadata.score,
                    scale = metadata.scoreScale,
                    fetchedAt = 300L,
                ),
            ),
        )
    }

    override suspend fun catalogEntry(
        pluginId: PluginId,
        sourceId: String,
    ): AppResult<CatalogEntryWithStory?> = error("Not used")

    override fun observeCatalogHome(pluginId: PluginId): Flow<CatalogHomeSnapshot?> = emptyFlow()
    override fun observeCatalogHomes(): Flow<List<CatalogHomeSnapshot>> = emptyFlow()
}

private class RecordingDetailsPlugin(
    hostedId: PluginId,
    details: SourceDetails,
) {
    val requestedSourceIds = mutableListOf<String>()

    val source: CatalogSource = object : CatalogSource {
        override val pluginId = hostedId
        override val version = "3.2.1"
        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
            CatalogSourceResult.Success(emptyList())
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
            CatalogSourceResult.Success(SourceSearchPage(emptyList(), null))
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
            requestedSourceIds += sourceId
            return CatalogSourceResult.Success(details)
        }
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> =
            CatalogSourceResult.Success(emptyList())
    }
}

private class FakeCatalogSourceRegistry(
    private val catalog: CatalogSource,
) : CatalogSourceRegistry {
    override suspend fun source(pluginId: PluginId): CatalogSource? {
        assertEquals(catalog.pluginId, pluginId)
        return catalog
    }
    override suspend fun enabled(): List<CatalogSource> = listOf(catalog)
}

private fun fixtureDetails(): SourceDetails = SourceDetails(
    sourceId = "source-a",
    sourceUrl = "https://catalog.example/source-a",
    title = "Fixture Novel",
    aliases = setOf("Alias A"),
    authors = setOf("Author"),
    description = "Rich description",
    genres = setOf("Fantasy"),
    contentType = SourceContentType.WEB_NOVEL,
    languageTags = setOf("en"),
    coverUrl = null,
    scoreValue = 8.5,
    scoreScale = 10.0,
    popularityRank = 5,
)

private fun fixtureStory(): CanonicalStory = CanonicalStory(
    id = StoryId("story-1"),
    contentType = ContentType.WEB_NOVEL,
    preferredTitle = "Fixture Novel",
    aliases = setOf("Fixture"),
    catalogEntries = listOf(
        fixtureEntry("catalog.a", "source-a", 8.0, 10.0, 100L),
        fixtureEntry("catalog.b", "source-b", 92.0, 100.0, 200L),
    ),
)

private fun fixtureEntry(
    pluginId: String,
    sourceId: String,
    score: Double?,
    scale: Double?,
    fetchedAt: Long,
): CatalogEntry = CatalogEntry(
    id = CatalogEntryId("$pluginId:$sourceId"),
    catalogPluginId = PluginId(pluginId),
    externalStoryId = sourceId,
    sourceUrl = "https://example.com/$sourceId",
    title = "Fixture Novel",
    aliases = emptySet(),
    authors = setOf("Author"),
    description = "Description from $pluginId",
    genres = setOf("Fantasy"),
    contentType = ContentType.WEB_NOVEL,
    languageTags = emptySet(),
    coverReference = null,
    publicationStatus = null,
    score = score,
    scoreScale = scale,
    popularityRank = null,
    pluginVersion = "1.0.0",
    fetchedAtEpochMillis = fetchedAt,
)
