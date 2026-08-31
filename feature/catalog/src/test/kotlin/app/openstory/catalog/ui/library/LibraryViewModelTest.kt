package app.openstory.catalog.ui.library

import androidx.lifecycle.SavedStateHandle
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun titleSearchFiltersCaseInsensitively() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING), entry("b", LibraryStatus.READING))
        fixtures.catalog.value = listOf(projection("a", "Moon Archive"), projection("b", "Solar Index"))
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        viewModel.updateQuery("moon")
        runCurrent()

        assertEquals(listOf(StoryId("a")), viewModel.state.value.readyItems().map { it.storyId })
    }

    @Test
    fun statusCountsRemainIndependentFromSelectedFilter() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(
            entry("a", LibraryStatus.READING),
            entry("b", LibraryStatus.READING),
            entry("c", LibraryStatus.COMPLETED),
        )
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        viewModel.selectStatus(LibraryStatus.COMPLETED)
        runCurrent()

        assertEquals(2, viewModel.state.value.readyContent().statusCounts[LibraryStatus.READING])
        assertEquals(1, viewModel.state.value.readyContent().statusCounts[LibraryStatus.COMPLETED])
        assertEquals(listOf(StoryId("c")), viewModel.state.value.readyItems().map { it.storyId })
    }

    @Test
    fun sortModesUseActivityTitleAndDateAdded() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(
            entry("b", LibraryStatus.READING, addedAt = 30L, updatedAt = 10L),
            entry("a", LibraryStatus.READING, addedAt = 10L, updatedAt = 30L),
        )
        fixtures.catalog.value = listOf(projection("a", "Zulu"), projection("b", "Alpha"))
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        assertEquals(listOf(StoryId("a"), StoryId("b")), viewModel.state.value.readyItems().map { it.storyId })
        viewModel.selectSort(LibrarySort.TITLE)
        runCurrent()
        assertEquals(listOf(StoryId("b"), StoryId("a")), viewModel.state.value.readyItems().map { it.storyId })
        viewModel.selectSort(LibrarySort.DATE_ADDED)
        runCurrent()
        assertEquals(listOf(StoryId("b"), StoryId("a")), viewModel.state.value.readyItems().map { it.storyId })
    }

    @Test
    fun mappingStateFilterUsesExistingVocabulary() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("linked", LibraryStatus.READING), entry("local", LibraryStatus.READING))
        fixtures.mappings.value = listOf(mapping("linked"))
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        viewModel.selectSourceFilter(LibrarySourceState.LINKED)
        runCurrent()

        assertEquals(listOf(StoryId("linked")), viewModel.state.value.readyItems().map { it.storyId })
    }

    @Test
    fun latestProgressPerStoryEnrichesItem() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.progress.value = listOf(progress("a", 0.2f, 10L), progress("a", 0.8f, 20L))
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        assertEquals(0.8f, viewModel.state.value.readyItems().single().progressFraction)
    }

    @Test
    fun canonicalProjectionOwnsLibraryPresentationFields() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.catalog.value = listOf(
            CatalogStoryProjection(
                storyId = StoryId("a"),
                title = "Canonical title",
                contentType = ContentType.MANGA,
                coverUrl = "https://example.test/canonical.jpg",
                publicationStatus = PublicationStatus.COMPLETED,
                score = CanonicalScore(0.82, 2),
            ),
        )
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        val item = viewModel.state.value.readyItems().single()
        assertEquals("Canonical title", item.title)
        assertEquals("https://example.test/canonical.jpg", item.coverUrl)
        assertEquals(PublicationStatus.COMPLETED, item.publicationStatus)
        assertEquals(8.2, item.score?.value)
        assertEquals(10.0, item.score?.scale)
        assertEquals(LibraryStatus.READING, item.status)
    }

    @Test
    fun savedStateRestoresQueryFiltersSortAndDisplayMode() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        val savedState = SavedStateHandle(
            mapOf(
                "library.query" to "moon",
                "library.status" to LibraryStatus.READING.name,
                "library.sort" to LibrarySort.TITLE.name,
                "library.display-mode" to LibraryDisplayMode.LIST.name,
                "library.source-filter" to LibrarySourceState.NO_MAPPING.name,
            ),
        )
        val viewModel = fixtures.viewModel(this, savedState)
        runCurrent()

        assertEquals("moon", viewModel.state.value.query)
        assertEquals(LibraryStatus.READING, viewModel.state.value.selectedStatus)
        assertEquals(LibrarySort.TITLE, viewModel.state.value.sort)
        assertEquals(LibraryDisplayMode.LIST, viewModel.state.value.displayMode)
        assertEquals(LibrarySourceState.NO_MAPPING, viewModel.state.value.sourceFilter)
    }

    @Test
    fun resetFilterSelectionsPreservesQueryAndDisplayMode() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(
                mapOf(
                    "library.query" to "moon",
                    "library.status" to LibraryStatus.READING.name,
                    "library.sort" to LibrarySort.TITLE.name,
                    "library.display-mode" to LibraryDisplayMode.LIST.name,
                    "library.source-filter" to LibrarySourceState.NO_MAPPING.name,
                ),
            ),
        )
        runCurrent()

        viewModel.resetFilterSelections()
        runCurrent()

        assertEquals("moon", viewModel.state.value.query)
        assertEquals(null, viewModel.state.value.selectedStatus)
        assertEquals(LibrarySort.LAST_ACTIVITY, viewModel.state.value.sort)
        assertEquals(LibraryDisplayMode.LIST, viewModel.state.value.displayMode)
        assertEquals(null, viewModel.state.value.sourceFilter)
    }

    @Test
    fun firstEmptyMembershipIsReadyTrueEmpty() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.catalogFlow = MutableSharedFlow()
        fixtures.mappingFlow = MutableSharedFlow()
        fixtures.progressFlow = MutableSharedFlow()

        val viewModel = fixtures.viewModel(this)
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertEquals(0, content.totalCount)
        assertTrue(viewModel.state.value.readyItems().isEmpty())
    }

    @Test
    fun dateAddedMembershipRendersBeforeCatalogMappingAndProgressWhenControlsDoNotRequireThem() =
        runTest(dispatcher.scheduler) {
            val fixtures = Fixtures()
            fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
            fixtures.catalogFlow = MutableSharedFlow()
            fixtures.mappingFlow = MutableSharedFlow()
            fixtures.progressFlow = MutableSharedFlow()
            val viewModel = fixtures.viewModel(
                this,
                SavedStateHandle(mapOf("library.sort" to LibrarySort.DATE_ADDED.name)),
            )
            runCurrent()

            val item = viewModel.state.value.readyItems().single()
            assertEquals(StoryId("a"), item.storyId)
            assertEquals("a", item.title)
            assertEquals(LibrarySourceState.UNKNOWN, item.sourceState)
        }

    @Test
    fun unresolvedMappingUsesUnknownNotNoMapping() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.mappingFlow = MutableSharedFlow()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.sort" to LibrarySort.DATE_ADDED.name)),
        )
        runCurrent()

        assertEquals(LibrarySourceState.UNKNOWN, viewModel.state.value.readyItems().single().sourceState)
    }

    @Test
    fun unresolvedMappingDoesNotUseSearchingWithoutLifecycleSignal() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.mappingFlow = MutableSharedFlow()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.sort" to LibrarySort.DATE_ADDED.name)),
        )
        runCurrent()

        assertTrue(viewModel.state.value.readyItems().none { it.sourceState == LibrarySourceState.SEARCHING })
    }

    @Test
    fun sourceFilterShowsLocalResolvingInsteadOfFalseFilteredEmpty() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.mappingFlow = MutableSharedFlow()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(
                mapOf(
                    "library.sort" to LibrarySort.DATE_ADDED.name,
                    "library.source-filter" to LibrarySourceState.NO_MAPPING.name,
                ),
            ),
        )
        runCurrent()

        assertIs<LibraryCollectionState.Resolving>(viewModel.state.value.readyContent().collection)
    }

    @Test
    fun titleQueryWaitsLocallyForFirstCatalogSnapshot() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.catalogFlow = MutableSharedFlow()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(
                mapOf(
                    "library.query" to "a",
                    "library.sort" to LibrarySort.DATE_ADDED.name,
                ),
            ),
        )
        runCurrent()

        assertIs<LibraryCollectionState.Resolving>(viewModel.state.value.readyContent().collection)
    }

    @Test
    fun titleSortWaitsLocallyForFirstCatalogSnapshot() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.catalogFlow = MutableSharedFlow()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.sort" to LibrarySort.TITLE.name)),
        )
        runCurrent()

        assertIs<LibraryCollectionState.Resolving>(viewModel.state.value.readyContent().collection)
    }

    @Test
    fun lastActivitySortWaitsLocallyForFirstProgressSnapshot() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.progressFlow = MutableSharedFlow()
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        assertIs<LibraryCollectionState.Resolving>(viewModel.state.value.readyContent().collection)
    }

    @Test
    fun membershipFirstFailureIsBlockingFailed() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entryFlow = flow { throw IllegalStateException("membership unavailable") }
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        val failed = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("library.membership.observe_failed", failed.failure.code)
        assertTrue(failed.failure.retryable)
    }

    @Test
    fun enrichmentFailurePreservesMembershipAndSurfacesIssue() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.catalogFlow = flow { throw IllegalStateException("catalog unavailable") }
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.sort" to LibrarySort.DATE_ADDED.name)),
        )
        runCurrent()

        val item = viewModel.state.value.readyItems().single()
        assertEquals(StoryId("a"), item.storyId)
        assertEquals("a", item.title)
        assertEquals("library.catalog.observe_failed", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun stateRemainsPendingUntilLibraryMembershipEmits() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.entryFlow = MutableSharedFlow()

        val viewModel = fixtures.viewModel(this)
        runCurrent()

        assertIs<ContentState.Pending>(viewModel.state.value.content)
    }

    @Test
    fun unsupportedRestoredSourceStateFallsBackToAllSources() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.source-filter" to LibrarySourceState.REVIEW.name)),
        )
        runCurrent()

        assertEquals(null, viewModel.state.value.sourceFilter)
    }

    @Test
    fun unknownSourceStateCannotBecomePersistedFilter() = runTest(dispatcher.scheduler) {
        val savedState = SavedStateHandle()
        val viewModel = Fixtures().viewModel(this, savedState)
        runCurrent()

        viewModel.selectSourceFilter(LibrarySourceState.UNKNOWN)
        runCurrent()

        assertNull(viewModel.state.value.sourceFilter)
        assertNull(savedState.get<String>("library.source-filter"))
    }

    @Test
    fun retryContentRestartsOnlyUnavailableMembership() = runTest(dispatcher.scheduler) {
        var membershipAttempts = 0
        val fixtures = Fixtures()
        fixtures.entryFlow = flow {
            membershipAttempts += 1
            if (membershipAttempts == 1) throw IllegalStateException("membership unavailable")
            emit(emptyList())
        }
        val viewModel = fixtures.viewModel(this)
        runCurrent()

        viewModel.retryContent()
        runCurrent()

        assertEquals(2, membershipAttempts)
        assertEquals(0, viewModel.state.value.readyContent().totalCount)
    }

    @Test
    fun retryCollectionRestartsOnlyUnavailableActiveDependencies() = runTest(dispatcher.scheduler) {
        var catalogAttempts = 0
        var mappingAttempts = 0
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.catalogFlow = flow {
            catalogAttempts += 1
            if (catalogAttempts == 1) throw IllegalStateException("catalog unavailable")
            emit(listOf(projection("a", "Alpha")))
        }
        fixtures.mappingFlow = flow {
            mappingAttempts += 1
            emit(emptyList())
        }
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.query" to "alpha", "library.sort" to LibrarySort.DATE_ADDED.name)),
        )
        runCurrent()
        assertIs<LibraryCollectionState.Unavailable>(viewModel.state.value.readyContent().collection)
        assertNull(viewModel.state.value.observationIssue)

        viewModel.retryCollection()
        runCurrent()

        assertEquals(listOf(StoryId("a")), viewModel.state.value.readyItems().map { it.storyId })
        assertEquals(2, catalogAttempts)
        assertEquals(1, mappingAttempts)
    }

    @Test
    fun retryObservationRestartsOnlySurfacedInactiveIssue() = runTest(dispatcher.scheduler) {
        var catalogAttempts = 0
        var progressAttempts = 0
        val fixtures = Fixtures()
        fixtures.entries.value = listOf(entry("a", LibraryStatus.READING))
        fixtures.catalogFlow = flow {
            catalogAttempts += 1
            if (catalogAttempts == 1) throw IllegalStateException("catalog unavailable")
            emit(listOf(projection("a", "Alpha")))
        }
        fixtures.progressFlow = flow {
            progressAttempts += 1
            emit(emptyList())
        }
        val viewModel = fixtures.viewModel(
            this,
            SavedStateHandle(mapOf("library.sort" to LibrarySort.DATE_ADDED.name)),
        )
        runCurrent()
        assertEquals("library.catalog.observe_failed", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(2, catalogAttempts)
        assertEquals(1, progressAttempts)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun viewModelDoesNotDependOnRoomOrPluginRuntime() {
        val dependencies = LibraryViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.map(Class<*>::getName) }
        assertTrue(dependencies.none { "storage.room" in it || "plugins.runtime" in it })
    }
}

private class Fixtures {
    val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val catalog = MutableStateFlow<List<CatalogStoryProjection>>(emptyList())
    val mappings = MutableStateFlow<List<ContentMapping>>(emptyList())
    val progress = MutableStateFlow<List<ReadingProgress>>(emptyList())
    var entryFlow: Flow<List<LibraryEntry>> = entries
    var catalogFlow: Flow<List<CatalogStoryProjection>> = catalog
    var mappingFlow: Flow<List<ContentMapping>> = mappings
    var progressFlow: Flow<List<ReadingProgress>> = progress

    fun viewModel(
        scope: TestScope,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): LibraryViewModel = LibraryViewModel(
        library = LibraryService(FakeLibraryRepository(entryFlow), Clock { 100L }, NoOpMappingScheduler),
        catalog = FakeProjectionRepository(catalogFlow),
        mappings = FakeMappingRepository(mappingFlow),
        progress = FakeProgressRepository(progressFlow),
        savedState = savedState,
    ).also { viewModel ->
        scope.backgroundScope.launch { viewModel.state.collect {} }
    }
}

private class FakeLibraryRepository(private val entries: Flow<List<LibraryEntry>>) : LibraryRepository {
    override fun observe() = entries
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry = error("unused")
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? = error("unused")
}

private class FakeProjectionRepository(private val projections: Flow<List<CatalogStoryProjection>>) : CatalogStoryProjectionRepository {
    override fun observe() = projections
}

private class FakeMappingRepository(private val mappings: Flow<List<ContentMapping>>) : ContentMappingRepository {
    override fun observe(storyId: StoryId) = mappings
    override fun observeAll() = mappings
    override suspend fun compareAndWrite(mapping: ContentMapping, replaceableOrigins: Set<ContentMappingOrigin>): ContentMappingWriteResult = error("unused")
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(storyId: StoryId, pluginId: PluginId, sourceStoryId: String, policyVersion: Int) = false
}

private class FakeProgressRepository(private val progress: Flow<List<ReadingProgress>>) : ReadingProgressRepository {
    override fun observeAll() = progress
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = error("unused")
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = error("unused")
    override suspend fun save(progress: ReadingProgress) = Unit
}

private fun entry(id: String, status: LibraryStatus, addedAt: Long = 1L, updatedAt: Long = 2L) =
    LibraryEntry(StoryId(id), status, addedAt, updatedAt)

private fun projection(id: String, title: String) = CatalogStoryProjection(
    StoryId(id), title, ContentType.WEB_NOVEL, null,
)

private fun mapping(id: String) = ContentMapping(
    StoryId(id), PluginId("content.a"), "source-$id", ContentMappingOrigin.AUTOMATED, 1, 1L,
)

private fun progress(id: String, fraction: Float, updatedAt: Long) = ReadingProgress(
    StoryId(id), CanonicalChapterId("chapter-$updatedAt"), ChapterReleaseId("release-$updatedAt"),
    "fingerprint-$updatedAt", ReadingPosition("block", 0, fraction), null, updatedAt,
)

private object NoOpMappingScheduler : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) = Unit
}

private fun LibraryUiState.readyContent(): LibraryContent =
    assertIs<ContentState.Ready<LibraryContent>>(content).value

private fun LibraryUiState.readyItems(): List<LibraryItemUiModel> =
    assertIs<LibraryCollectionState.Ready>(readyContent().collection).items
