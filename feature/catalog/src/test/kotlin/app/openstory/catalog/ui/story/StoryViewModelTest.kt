package app.openstory.catalog.ui.story

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.chapters.repository.ChapterRepository
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
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun refreshUsesExactAvailableSourceSelectedByCatalogState() = runTest(dispatcher.scheduler) {
        val repository = StoryRepository(fixtureSnapshot(includeSecondSource = true))
        val sourceA = DetailsSource("catalog.a")
        val sourceB = DetailsSource("catalog.b")
        val viewModel = viewModel(repository, sourceA, sourceB)
        runCurrent()

        viewModel.selectSource(PluginId("catalog.b"), "source-b")
        viewModel.refresh()
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

        viewModel.refresh()
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

        viewModel.refresh()
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
        assertFalse(dependencyNames.any { it == ChapterRepository::class.java.name })
    }

    @Test
    fun heroPrefersSelectedSourceThenDeterministicBestArtwork() = runTest(dispatcher.scheduler) {
        val repository = StoryRepository(
            fixtureSnapshot(includeSecondSource = true).copy(
                entries = listOf(
                    fixtureEntry(StoryId("story-1"), "catalog.b", "source-b", "B").copy(
                        coverUrl = "best.jpg", score = app.openstory.catalog.model.Score(9.0, 10.0),
                    ),
                    fixtureEntry(StoryId("story-1"), "catalog.a", "source-a", "A").copy(
                        coverUrl = "selected.jpg", score = app.openstory.catalog.model.Score(7.0, 10.0),
                    ),
                ),
            ),
        )
        val viewModel = viewModel(repository, DetailsSource("catalog.a"), DetailsSource("catalog.b"))
        runCurrent()
        assertEquals("selected.jpg", viewModel.state.value.story?.coverUrl)

        viewModel.selectSource(PluginId("catalog.b"), "source-b")
        runCurrent()
        assertEquals("best.jpg", viewModel.state.value.story?.coverUrl)
    }

    @Test
    fun overviewAggregatesAuthorsGenresAliasesWithoutDuplicates() = runTest(dispatcher.scheduler) {
        val storyId = StoryId("story-1")
        val entries = listOf(
            fixtureEntry(storyId, "catalog.a", "a", "Title").copy(
                aliases = setOf("Alias", "Shared"), authors = setOf("One"), genres = setOf("Fantasy"),
            ),
            fixtureEntry(storyId, "catalog.b", "b", "Title").copy(
                aliases = setOf("Shared", "Other"), authors = setOf("One", "Two"), genres = setOf("Fantasy", "Drama"),
            ),
        )
        val viewModel = viewModel(StoryRepository(StoryCatalogSnapshot(Story(storyId, ContentType.WEB_NOVEL), entries)))
        runCurrent()

        val story = assertNotNull(viewModel.state.value.story)
        assertEquals(setOf("Alias", "Shared", "Other"), story.aliases)
        assertEquals(setOf("One", "Two"), story.authors)
        assertEquals(setOf("Fantasy", "Drama"), story.genres)
    }

    @Test
    fun sourceSelectionDoesNotChangeConfirmedMapping() = runTest(dispatcher.scheduler) {
        val repository = StoryRepository(fixtureSnapshot(includeSecondSource = true))
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.selectSource(PluginId("catalog.b"), "source-b")
        runCurrent()

        assertEquals(StorySourceIdentity(PluginId("catalog.b"), "source-b"), viewModel.state.value.selectedSource)
        assertFalse(StoryViewModel::class.java.declaredConstructors.flatMap { it.parameterTypes.asList() }
            .any { it.name.contains("ContentMappingRepository") })
    }

    @Test
    fun libraryStatusChangeUsesLibraryService() = runTest(dispatcher.scheduler) {
        val library = FakeLibraryRepository()
        val viewModel = viewModel(StoryRepository(fixtureSnapshot()), libraryRepository = library)
        runCurrent()

        viewModel.changeLibraryStatus(LibraryStatus.READING)
        runCurrent()

        assertEquals(LibraryStatus.READING, library.entries.value.single().status)
        assertEquals(LibraryStatus.READING, viewModel.state.value.libraryStatus)
    }

    @Test
    fun latestIncompleteProgressProducesResumeAction() = runTest(dispatcher.scheduler) {
        val progress = MutableStateFlow(
            listOf(
                readingProgress(10L, completed = false),
                readingProgress(30L, completed = true),
                readingProgress(20L, completed = false),
            ),
        )
        val viewModel = viewModel(StoryRepository(fixtureSnapshot()), progress = progress)
        runCurrent()

        assertEquals(ChapterReleaseId("release-20"), viewModel.state.value.resumeTarget?.releaseId)
    }

    private fun TestScope.viewModel(
        repository: StoryRepository,
        vararg sources: DetailsSource,
        storyId: StoryId = StoryId("story-1"),
        libraryRepository: FakeLibraryRepository = FakeLibraryRepository(),
        progress: Flow<List<ReadingProgress>> = MutableStateFlow(emptyList()),
    ): StoryViewModel {
        val details = CatalogDetailsService(
            DetailsRegistry(sources.toList()),
            repository,
            StoryMatcher(),
            Clock { 100L },
        )
        return StoryViewModel(
            StoryAssistedArgs(storyId), repository, details,
            LibraryService(libraryRepository, Clock { 100L }, NoOpMappingScheduler),
            FakeProgressRepository(progress),
        ).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
        }
    }
}

private class FakeLibraryRepository : LibraryRepository {
    val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    override fun observe(): Flow<List<LibraryEntry>> = entries
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry =
        LibraryEntry(storyId, status, addedAt, addedAt).also { entries.value = listOf(it) }
    override suspend fun remove(storyId: StoryId) { entries.value = entries.value.filterNot { it.storyId == storyId } }
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? =
        entries.value.firstOrNull { it.storyId == storyId }?.copy(status = status, updatedAt = updatedAt)
            ?.also { entries.value = listOf(it) }
}

private class FakeProgressRepository(private val records: Flow<List<ReadingProgress>>) : ReadingProgressRepository {
    override fun observeAll() = records
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = error("unused")
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = error("unused")
    override suspend fun save(progress: ReadingProgress) = Unit
}

private object NoOpMappingScheduler : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) = Unit
}

private fun readingProgress(updatedAt: Long, completed: Boolean) = ReadingProgress(
    StoryId("story-1"), CanonicalChapterId("chapter-$updatedAt"), ChapterReleaseId("release-$updatedAt"),
    "fingerprint-$updatedAt", ReadingPosition("block", 0, 0.5f), updatedAt.takeIf { completed }, updatedAt,
)

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
