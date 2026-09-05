package app.openstory.catalog.ui.discover
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
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
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.FakeClock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
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
    fun homesAndRankingShareOneRepositoryObservation() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(1, repository.observeHomesSubscriptions)
    }

    @Test
    fun discoverObservesOnlySemanticStoryIdsInsteadOfTheWholeCanonicalCatalog() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(cachedHome())
            val projections = RecordingScopedProjectionRepository(repository.projections())
            val viewModel = viewModel(
                repository = repository,
                source = FakeSource(),
                projections = projections,
            )

            backgroundScope.launch { viewModel.state.collect() }
            runCurrent()

            assertEquals(setOf(StoryId("story-1")), projections.observedStoryIds)
            assertEquals(0, projections.observeAllCalls)
            assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
        }

    @Test
    fun refreshReportUsesCommittedResultWithoutOpeningSecondHomeObservation() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome(), applyHomeRefreshMutation = true)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(1, repository.observeHomesSubscriptions)
        assertEquals(
            200L,
            viewModel.state.value.refreshReport?.refreshedAtEpochMillis?.get(PluginId("catalog.a")),
        )
    }

    @Test
    fun emptyCacheBootstrapIsPendingButNotRefreshing() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is app.openstory.catalog.ui.state.ContentState.Pending)
        assertFalse(viewModel.state.value.refresh.inProgress)

        release.complete(Unit)
        runCurrent()
    }

    @Test
    fun noEnabledProvidersBecomesReadyNoProviderReason() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource()
        val viewModel = viewModel(
            repository = repository,
            source = source,
            enabledSources = emptyList(),
        )
        backgroundScope.launch { viewModel.state.collect() }

        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals(
            DiscoverNoContentReason.NO_ENABLED_PROVIDERS,
            viewModel.state.value.readyContent().noContentReason,
        )
        assertEquals(0, source.homeCalls)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun noProviderBootstrapReasonSurvivesLaterAuthoritativeEmptyHomeEmission() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(emptyList())
            val viewModel = viewModel(
                repository = repository,
                source = FakeSource(),
                enabledSources = emptyList(),
            )
            backgroundScope.launch { viewModel.state.collect() }
            runCurrent()

            assertEquals(
                DiscoverNoContentReason.NO_ENABLED_PROVIDERS,
                viewModel.state.value.readyContent().noContentReason,
            )

            repository.replaceHomes(emptyList())
            runCurrent()

            assertEquals(
                DiscoverNoContentReason.NO_ENABLED_PROVIDERS,
                viewModel.state.value.readyContent().noContentReason,
            )
        }

    @Test
    fun allProviderFailuresWithNoCacheBecomeBlockingFailed() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource().apply {
            homeAction = { CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true)) }
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }

        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.discover.bootstrap_all_providers_failed", failed.failure.code)
        assertTrue(failed.failure.retryable)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun failedBootstrapIsNotClearedByAnotherEmptyHomeEmission() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource().apply {
            homeAction = { CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true)) }
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Failed)

        repository.replaceHomes(emptyList())
        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.discover.bootstrap_all_providers_failed", failed.failure.code)
    }

    @Test
    fun manualRefreshRecoversBlockingBootstrapFailureWithAuthoritativeEmptyFeed() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(emptyList(), applyHomeRefreshMutation = true)
            val source = FakeSource().apply {
                homeAction = {
                    CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", retryable = true))
                }
            }
            val viewModel = viewModel(repository, source)
            backgroundScope.launch { viewModel.state.collect() }
            runCurrent()

            assertTrue(viewModel.state.value.content is ContentState.Failed)

            source.homeAction = { CatalogSourceResult.Success(emptyList()) }
            viewModel.refresh()
            runCurrent()

            val content = viewModel.state.value.readyContent()
            assertEquals(DiscoverNoContentReason.EMPTY_FEED, content.noContentReason)
            assertEquals(null, viewModel.state.value.refresh.failure)
            assertEquals(2, source.homeCalls)
        }

    @Test
    fun manualProviderFailureKeepsReadyEmptyWithoutDuplicatingBootstrapIssue() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(emptyList(), applyHomeRefreshMutation = true)
            val source = FakeSource()
            val viewModel = viewModel(repository, source)
            backgroundScope.launch { viewModel.state.collect() }
            runCurrent()

            assertEquals(DiscoverNoContentReason.EMPTY_FEED, viewModel.state.value.readyContent().noContentReason)

            source.homeAction = {
                CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", retryable = true))
            }
            viewModel.refresh()
            runCurrent()

            assertEquals(DiscoverNoContentReason.EMPTY_FEED, viewModel.state.value.readyContent().noContentReason)
            assertEquals(null, viewModel.state.value.observationIssue)
            assertEquals(
                "catalog.offline",
                viewModel.state.value.refreshReport?.failed?.get(PluginId("catalog.a")),
            )
            assertEquals(2, source.homeCalls)
        }

    @Test
    fun authoritativeHomeRecoveryRetiresFailedBootstrapState() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource().apply {
            homeAction = { CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true)) }
        }
        val recoveredHome = cachedHome()
        val recoveredProjections = FakeRepository(recoveredHome).projections()
        val projections = MutableProjectionRepository(recoveredProjections)
        val bootstrap = CanonicalBootstrapUseCase(
            DiscoverCanonicalRepository(readyDiscoverState(StoryId("story-1"))),
            CanonicalGenerationRebuilder { storyId, _ -> CanonicalFusionResult.Preparing(storyId) },
        )
        val viewModel = viewModel(
            repository = repository,
            source = source,
            bootstrap = bootstrap,
            projections = projections,
        )
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Failed)

        repository.replaceHomes(recoveredHome)
        runCurrent()
        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)

        repository.replaceHomes(emptyList())
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertFalse(content.hasContent)
        assertEquals(DiscoverNoContentReason.EMPTY_FEED, content.noContentReason)
        assertEquals(null, viewModel.state.value.observationIssue)
    }

    @Test
    fun emptyFirstCacheEmissionBootstrapsRefreshExactlyOnce() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource()

        viewModel(repository, source)
        runCurrent()

        assertEquals(1, source.homeCalls)
        runCurrent()
        assertEquals(1, source.homeCalls)
    }

    @Test
    fun populatedFirstCacheEmissionDoesNotBootstrapRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()

        viewModel(repository, source)
        runCurrent()

        assertEquals(0, source.homeCalls)
    }

    @Test
    fun populatedMigratedCacheBootstrapsVisibleCanonicalStoriesWithoutNetwork() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(cachedHome())
            val source = FakeSource()
            val projections = MutableProjectionRepository(emptyList())
            val canonical = DiscoverCanonicalRepository(preparingDiscoverState(StoryId("story-1")))
            val rebuildCalls = mutableListOf<StoryId>()
            val bootstrap = CanonicalBootstrapUseCase(
                canonical,
                CanonicalGenerationRebuilder { storyId, _ ->
                    rebuildCalls += storyId
                    val ready = readyDiscoverState(storyId)
                    canonical.replace(ready)
                    projections.replace(repository.projections())
                    CanonicalFusionResult.Promoted(ready.generation)
                },
            )
            val viewModel = viewModel(
                repository = repository,
                source = source,
                bootstrap = bootstrap,
                projections = projections,
            )
            backgroundScope.launch { viewModel.state.collect() }

            runCurrent()

            assertEquals(listOf(StoryId("story-1")), rebuildCalls)
            assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
            assertEquals(0, source.homeCalls)
            assertEquals(0, source.detailsCalls)
        }

    @Test
    fun cachedLatestWithoutArtworkDoesNotCallDetailsAndKeepsMissingArtwork() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(0, source.detailsCalls)
        assertEquals(null, viewModel.state.value.readyContent().latestUpdates.single().coverUrl)
    }

    @Test
    fun cachedLatestWithArtworkUsesPluginListingValueWithoutDetails() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = "https://example.test/cached.jpg"))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(0, source.detailsCalls)
        assertEquals("https://example.test/cached.jpg", viewModel.state.value.readyContent().latestUpdates.single().coverUrl)
    }

    @Test
    fun manualRefreshDoesNotUseDetailsToRepairMissingArtwork() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(latestHome(coverUrl = null))
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(1, source.homeCalls)
        assertEquals(0, source.detailsCalls)
        assertEquals(null, viewModel.state.value.readyContent().latestUpdates.single().coverUrl)
    }

    @Test
    fun failedEmptyCacheBootstrapDoesNotRetryLoop() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource().apply {
            homeAction = {
                CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true))
            }
        }

        viewModel(repository, source)
        runCurrent()
        assertEquals(1, source.homeCalls)

        runCurrent()
        assertEquals(1, source.homeCalls)
    }

    @Test
    fun observationFailureFallbackDoesNotBootstrapNetworkRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), observeFailure = true)
        val source = FakeSource()

        viewModel(repository, source)
        runCurrent()

        assertEquals(0, source.homeCalls)
    }

    @Test
    fun mangaIsDefaultAndLightNovelSelectionIsIgnoredWhileDisabled() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(ContentType.MANGA, viewModel.state.value.readyContent().selectedContentType)

        viewModel.selectContentType(ContentType.LIGHT_NOVEL)
        runCurrent()

        assertEquals(ContentType.MANGA, viewModel.state.value.readyContent().selectedContentType)
        assertEquals(0, source.homeCalls)
    }

    @Test
    fun successfulProviderWithEmptyFeedBecomesReadyEmptyFeed() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), applyHomeRefreshMutation = true)
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Pending)
        assertFalse(viewModel.state.value.refresh.inProgress)

        release.complete(Unit)
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertFalse(viewModel.state.value.refresh.inProgress)
        assertEquals(DiscoverNoContentReason.EMPTY_FEED, viewModel.state.value.readyContent().noContentReason)
        assertFalse(viewModel.state.value.readyContent().hasContent)
    }

    @Test
    fun bootstrapWaitsForEverySuccessfulProviderHomeBeforePublishingEmpty() =
        runTest(dispatcher.scheduler) {
            val firstProviderHomes = emptyHome("catalog.a", refreshedAtEpochMillis = 200L)
            val committedHomes = firstProviderHomes + cachedHome("catalog.b").map { home ->
                home.copy(refreshedAtEpochMillis = 200L)
            }
            val repository = FakeRepository(initialHomes = emptyList())
            val candidateProjections = FakeRepository(committedHomes).projections()
            val storyId = StoryId("story-1")
            val sourceA = FakeSource(PluginId("catalog.a"))
            val sourceB = FakeSource(PluginId("catalog.b"))
            val bootstrap = CanonicalBootstrapUseCase(
                DiscoverCanonicalRepository(readyDiscoverState(storyId)),
                CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) },
            )
            val viewModel = viewModel(
                repository = repository,
                source = sourceA,
                bootstrap = bootstrap,
                projections = FakeProjectionRepository(candidateProjections),
                enabledSources = listOf(sourceA, sourceB),
            )
            val observedContent = mutableListOf<ContentState<DiscoverContent>>()
            backgroundScope.launch {
                viewModel.state.collect { state -> observedContent += state.content }
            }
            runCurrent()

            assertTrue(viewModel.state.value.content is ContentState.Pending)

            repository.replaceHomes(firstProviderHomes)
            runCurrent()
            assertTrue(viewModel.state.value.content is ContentState.Pending)

            repository.replaceHomes(committedHomes)
            runCurrent()

            assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
            assertFalse(
                observedContent.any { content ->
                    (content as? ContentState.Ready)?.value?.let { !it.hasContent } == true
                },
            )
        }

    @Test
    fun newerProviderCommitsSupersedeTheBootstrapCommitsStillAwaitingObservation() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(initialHomes = emptyList())
            val sourceA = FakeSource(PluginId("catalog.a"))
            val sourceB = FakeSource(PluginId("catalog.b"))
            val viewModel = viewModel(
                repository = repository,
                source = sourceA,
                enabledSources = listOf(sourceA, sourceB),
            )
            backgroundScope.launch { viewModel.state.collect() }
            runCurrent()

            assertTrue(viewModel.state.value.content is ContentState.Pending)
            assertEquals(
                mapOf(
                    PluginId("catalog.a") to 200L,
                    PluginId("catalog.b") to 200L,
                ),
                viewModel.state.value.refreshReport?.refreshedAtEpochMillis,
            )

            repository.replaceHomes(
                emptyHome("catalog.a", refreshedAtEpochMillis = 300L) +
                    emptyHome("catalog.b", refreshedAtEpochMillis = 300L),
            )
            runCurrent()

            val content = viewModel.state.value.readyContent()
            assertFalse(content.hasContent)
            assertEquals(DiscoverNoContentReason.EMPTY_FEED, content.noContentReason)
        }

    @Test
    fun refreshFlagsDoNotReprojectSemanticLists() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        val popular = viewModel.state.value.readyContent().popular

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)
        assertSame(popular, viewModel.state.value.readyContent().popular)

        release.complete(Unit)
        runCurrent()

        assertSame(popular, viewModel.state.value.readyContent().popular)
    }

    @Test
    fun cachedContentNeverReturnsToSkeletonDuringManualRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)
        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)

        release.complete(Unit)
        runCurrent()
    }

    @Test
    fun cachedHomeEmitsBeforeRefreshCompletes() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)
        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
        release.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun refreshFailureKeepsCachedSectionsAndExposesNonBlockingFailure() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource().apply {
            homeAction = {
                CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true))
            }
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
        assertEquals("catalog.offline", viewModel.state.value.refreshReport?.failed?.get(source.pluginId))
    }

    @Test
    fun refreshWorkUsesFeatureDefaultSchedulingBoundary() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val refreshScheduler = TestCoroutineScheduler()
        val refreshDispatcher = StandardTestDispatcher(refreshScheduler)
        val viewModel = viewModel(repository, source, refreshDispatcher)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)
        assertEquals(0, source.homeCalls)

        refreshScheduler.runCurrent()
        runCurrent()

        assertEquals(1, source.homeCalls)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun refreshActionInvokesCatalogRefreshServiceExactlyOnce() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(1, source.homeCalls)
    }

    @Test
    fun freshDiscoverRefreshDefersCanonicalConvergence() =
        runTest(dispatcher.scheduler) {
            val repository = FakeRepository(emptyList())
            val source = FakeSource().apply {
                homeAction = { CatalogSourceResult.Success(discoverSections(itemsPerSection = 10)) }
            }
            val engine = RecordingDiscoverEngine()

            viewModel(repository, source, engine = engine)
            runCurrent()

            assertEquals(emptySet(), engine.immediateStoryIdBatches.single())
        }

    @Test
    fun refreshBoundaryExceptionBecomesNonBlockingFailure() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome(), sourceRecordFailuresRemaining = 1)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertFalse(viewModel.state.value.refresh.inProgress)
        assertEquals("catalog.home.refresh_exception", viewModel.state.value.refresh.failure?.code)
        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
    }

    @Test
    fun observationFailureBeforeFirstEmissionIsVisible() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), observeFailure = true)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.home.observe_exception", failed.failure.code)
        assertEquals(null, viewModel.state.value.observationIssue)
    }

    @Test
    fun observationFailureAfterCachedEmissionRetainsCache() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome(), observeFailureAfterEmission = true)
        val viewModel = viewModel(repository, FakeSource())
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.readyContent().popular.single().title)
        assertEquals("catalog.home.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.observationIssue != null)
        assertEquals(null, viewModel.state.value.refresh.failure)
    }

    @Test
    fun oldFeedObservationIssueIsDiscardedImmediatelyWhenSettlementKeyChanges() = runTest(dispatcher.scheduler) {
        val story1 = StoryId("story-1")
        val story2 = StoryId("story-2")
        val repository = FakeRepository(popularHome("story-1"))
        val projectionFixtures = FakeRepository(popularHome("story-1", "story-2")).projections()
        val releaseNewFeed = CompletableDeferred<Unit>()
        val projections = SwitchingProjectionRepository(
            oldStoryId = story1,
            newStoryId = story2,
            projections = projectionFixtures,
            releaseNewFeed = releaseNewFeed,
        )
        val bootstrap = CanonicalBootstrapUseCase(
            DiscoverCanonicalRepository(listOf(readyDiscoverState(story1), readyDiscoverState(story2))),
            CanonicalGenerationRebuilder { storyId, _ -> CanonicalFusionResult.Preparing(storyId) },
        )
        val viewModel = viewModel(
            repository = repository,
            source = FakeSource(),
            bootstrap = bootstrap,
            projections = projections,
        )
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(listOf(story1), viewModel.state.value.readyContent().popular.map { it.storyId })
        assertEquals("catalog.home.ranking_exception", viewModel.state.value.observationIssue?.code)

        repository.replaceHomes(popularHome("story-2"))
        runCurrent()

        assertEquals(listOf(story1), viewModel.state.value.readyContent().popular.map { it.storyId })
        assertEquals(null, viewModel.state.value.observationIssue)

        releaseNewFeed.complete(Unit)
        runCurrent()

        assertEquals(listOf(story2), viewModel.state.value.readyContent().popular.map { it.storyId })
        assertEquals(null, viewModel.state.value.observationIssue)
    }

    @Test
    fun unresolvedCanonicalLeaderCannotCreateFalseEmptyOrHeroPromotion() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(popularHome("story-1", "story-2"))
        val allProjections = repository.projections()
        val projections = MutableProjectionRepository(
            allProjections.filter { it.storyId == StoryId("story-2") },
        )
        val canonical = DiscoverCanonicalRepository(
            listOf(
                preparingDiscoverState(StoryId("story-1")),
                readyDiscoverState(StoryId("story-2")),
            ),
        )
        val releaseLeader = CompletableDeferred<Unit>()
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { storyId, _ ->
                if (storyId == StoryId("story-1")) releaseLeader.await()
                CanonicalFusionResult.Preparing(storyId)
            },
        )
        val viewModel = viewModel(repository, FakeSource(), bootstrap = bootstrap, projections = projections)
        backgroundScope.launch { viewModel.state.collect() }

        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Pending)
        assertEquals(null, viewModel.state.value.readyContentOrNull())

        releaseLeader.complete(Unit)
        runCurrent()
    }

    @Test
    fun terminalPartialCanonicalFailureKeepsStableReadyContentAndIssue() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(popularHome("story-1", "story-2"))
        val projections = MutableProjectionRepository(
            repository.projections().filter { it.storyId == StoryId("story-1") },
        )
        val canonical = DiscoverCanonicalRepository(
            listOf(
                readyDiscoverState(StoryId("story-1")),
                preparingDiscoverState(StoryId("story-2")),
            ),
        )
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { storyId, _ ->
                if (storyId == StoryId("story-2")) error("bootstrap boundary")
                CanonicalFusionResult.Preparing(storyId)
            },
        )
        val viewModel = viewModel(repository, FakeSource(), bootstrap = bootstrap, projections = projections)
        backgroundScope.launch { viewModel.state.collect() }

        runCurrent()

        assertEquals(listOf(StoryId("story-1")), viewModel.state.value.readyContent().popular.map { it.storyId })
        assertEquals("catalog.discover.canonical_bootstrap_failed", viewModel.state.value.observationIssue?.code)
        assertTrue(viewModel.state.value.observationIssue?.retryable == true)
    }

    @Test
    fun terminalCanonicalFailureWithNoUsableContentIsFailed() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(popularHome("story-1"))
        val projections = MutableProjectionRepository(emptyList())
        val canonical = DiscoverCanonicalRepository(preparingDiscoverState(StoryId("story-1")))
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { _, _ -> error("bootstrap boundary") },
        )
        val viewModel = viewModel(repository, FakeSource(), bootstrap = bootstrap, projections = projections)
        backgroundScope.launch { viewModel.state.collect() }

        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.discover.canonical_bootstrap_failed", failed.failure.code)
        assertTrue(failed.failure.retryable)
    }

    @Test
    fun retryContentRestartsHomeObservationNotPullRefreshChrome() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(
            initialHomes = cachedHome(),
            observeFailuresBeforeSuccess = 1,
        )
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Failed)
        assertEquals(1, repository.observeHomesSubscriptions)

        viewModel.retryContent()
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals(2, repository.observeHomesSubscriptions)
        assertEquals(0, source.homeCalls)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun retryContentRestartsRetryableBootstrapWithoutPullRefreshChrome() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList(), applyHomeRefreshMutation = true)
        val source = FakeSource().apply {
            homeAction = { CatalogSourceResult.Failure(CatalogSourceFailure("catalog.offline", true)) }
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        assertTrue(viewModel.state.value.content is ContentState.Failed)
        assertEquals(1, source.homeCalls)

        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        viewModel.retryContent()
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Pending)
        assertFalse(viewModel.state.value.refresh.inProgress)
        assertEquals(2, source.homeCalls)

        release.complete(Unit)
        runCurrent()
        assertEquals(DiscoverNoContentReason.EMPTY_FEED, viewModel.state.value.readyContent().noContentReason)
    }

    @Test
    fun newManualRefreshAttemptClearsOnlyOldRefreshFailure() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(
            initialHomes = cachedHome(),
            sourceRecordFailuresRemaining = 1,
            observeFailureAfterEmission = true,
        )
        val source = FakeSource()
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        assertEquals("catalog.home.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.refresh()
        runCurrent()
        assertEquals("catalog.home.refresh_exception", viewModel.state.value.refresh.failure?.code)

        val release = CompletableDeferred<Unit>()
        source.homeAction = {
            release.await()
            CatalogSourceResult.Success(emptyList())
        }
        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.refresh.inProgress)
        assertEquals(null, viewModel.state.value.refresh.failure)
        assertEquals("catalog.home.observe_exception", viewModel.state.value.observationIssue?.code)
        assertTrue(viewModel.state.value.content is ContentState.Ready)

        release.complete(Unit)
        runCurrent()
    }


    @Test
    fun retryObservationRestartsProjectionObservationWithoutManualRefresh() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val projections = RetryingProjectionRepository(repository.projections())
        val viewModel = viewModel(repository, source, projections = projections)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals("catalog.home.ranking_exception", viewModel.state.value.observationIssue?.code)
        val beforeRetry = projections.scopedSubscriptions

        viewModel.retryObservation()
        runCurrent()

        assertEquals(beforeRetry + 1, projections.scopedSubscriptions)
        assertEquals(0, source.homeCalls)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun retryContentRestartsTerminalSettlementBoundaryWithoutManualRefresh() = runTest(dispatcher.scheduler) {
        val storyId = StoryId("story-1")
        val repository = FakeRepository(cachedHome())
        val source = FakeSource()
        val canonical = DiscoverCanonicalRepository(preparingDiscoverState(storyId))
        var bootstrapAttempts = 0
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { _, _ ->
                bootstrapAttempts += 1
                error("canonical bootstrap unavailable")
            },
        )
        val viewModel = viewModel(
            repository = repository,
            source = source,
            bootstrap = bootstrap,
            projections = MutableProjectionRepository(emptyList()),
        )
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        val firstFailure = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.discover.canonical_bootstrap_failed", firstFailure.failure.code)
        assertEquals(1, bootstrapAttempts)

        viewModel.retryContent()
        runCurrent()

        assertEquals(2, bootstrapAttempts)
        assertEquals(0, source.homeCalls)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun nonRetryableAutomaticBootstrapFailureDoesNotRetry() = runTest(dispatcher.scheduler) {
        val repository = FakeRepository(emptyList())
        val source = FakeSource().apply {
            homeAction = { CatalogSourceResult.Failure(CatalogSourceFailure("catalog.denied", retryable = false)) }
        }
        val viewModel = viewModel(repository, source)
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertFalse(failed.failure.retryable)
        assertEquals(1, source.homeCalls)

        viewModel.retryContent()
        runCurrent()

        assertEquals(1, source.homeCalls)
        assertFalse(viewModel.state.value.refresh.inProgress)
    }

    @Test
    fun discoverReadPathHasNoDetailsOrFusionDependency() {
        val dependencies = DiscoverViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }
        assertFalse(dependencies.any { it.endsWith("CatalogMetadataCoordinator") })
        assertFalse(dependencies.any { it.endsWith("CatalogDetailsLoader") })
        assertFalse(dependencies.any { it.endsWith("CatalogFusionEngine") })
        assertFalse(dependencies.any { it.endsWith("CanonicalFusionService") })
    }

    private fun TestScope.viewModel(
        repository: FakeRepository,
        source: FakeSource,
        refreshDispatcher: CoroutineDispatcher = dispatcher,
        bootstrap: CanonicalBootstrapUseCase = readyBootstrap(repository),
        projections: CatalogStoryProjectionRepository = FakeProjectionRepository(repository.projections()),
        engine: CanonicalEngineEventSink = app.openstory.catalog.FeatureNoOpCanonicalEngineEventSink,
        enabledSources: List<CatalogSource> = listOf(source),
    ): DiscoverViewModel {
        val registry = Registry(enabledSources)
        val clock = FakeClock(200L)
        val refreshService = CatalogRefreshService(
            sources = registry,
            repository = repository,
            reconciliationEngine = app.openstory.catalog.reconciliation.CatalogReconciliationEngine(
                app.openstory.catalog.reconciliation.ReconciliationPolicy(),
            ),
            storyIdFactory = app.openstory.catalog.identity.CatalogStoryIdFactory(),
            orchestrator = engine,
            clock = clock,
        )
        return DiscoverViewModel(
            repository,
            projections,
            DiscoverRefreshPipeline(
                refreshService,
                FixedAppDispatchers(dispatcher, refreshDispatcher, dispatcher),
            ),
            DiscoverProjectionPipeline(
                FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
            ),
            DiscoverCanonicalBootstrapPipeline(
                bootstrap,
                projections,
                FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
            ),
        )
    }

    private fun readyBootstrap(repository: FakeRepository): CanonicalBootstrapUseCase {
        val states = repository.projections().map { projection ->
            readyDiscoverState(projection.storyId)
        }
        val canonical = DiscoverCanonicalRepository(states)
        return CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { storyId, _ -> CanonicalFusionResult.Preparing(storyId) },
        )
    }
}

private fun DiscoverUiState.readyContent(): DiscoverContent =
    (content as ContentState.Ready<DiscoverContent>).value

private fun DiscoverUiState.readyContentOrNull(): DiscoverContent? =
    (content as? ContentState.Ready<DiscoverContent>)?.value


private class Registry(private val sources: List<CatalogSource>) : CatalogSourceRegistry {
    override suspend fun enabled() = sources
    override suspend fun source(pluginId: PluginId) = sources.firstOrNull { it.pluginId == pluginId }
}

private class FakeSource(
    override val pluginId: PluginId = PluginId("catalog.a"),
) : CatalogSource {
    override val version = "1.0.0"
    var homeCalls = 0
    var detailsCalls = 0
    var homeAction: suspend () -> CatalogSourceResult<List<SourceSection>> = {
        CatalogSourceResult.Success(emptyList())
    }

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> {
        homeCalls++
        return homeAction()
    }

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
        error("unused")
    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
        detailsCalls++
        return CatalogSourceResult.Success(
            SourceDetails(
                sourceId = sourceId,
                sourceUrl = "https://example.test/$sourceId",
                title = "Fixture Novel $sourceId",
                aliases = emptySet(),
                authors = setOf("Fixture Author"),
                description = "Hydrated details",
                genres = setOf("Fantasy"),
                contentType = app.openstory.catalog.source.SourceContentType.MANGA,
                languageTags = setOf("en"),
                coverUrl = "https://example.test/$sourceId.jpg",
                scoreValue = 8.4,
                scoreScale = 10.0,
                popularityRank = 1L,
            ),
        )
    }
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
}

private class FakeRepository(
    initialHomes: List<CatalogHomeSnapshot>,
    private var sourceRecordFailuresRemaining: Int = 0,
    private val observeFailure: Boolean = false,
    private val observeFailureAfterEmission: Boolean = false,
    private var observeFailuresBeforeSuccess: Int = 0,
    private val applyHomeRefreshMutation: Boolean = false,
) : CatalogRepository {
    private val homes = MutableStateFlow(initialHomes)
    var observeHomesSubscriptions = 0
        private set

    fun replaceHomes(value: List<CatalogHomeSnapshot>) {
        homes.value = value
    }

    fun projections(): List<CatalogStoryProjection> = homes.value
        .flatMap { it.sections }
        .flatMap { it.items }
        .distinctBy { it.storyId }
        .map { entry ->
            CatalogStoryProjection(
                storyId = entry.storyId,
                title = entry.title,
                contentType = entry.contentType,
                coverUrl = entry.coverUrl,
                authors = entry.authors,
                publicationStatus = entry.publicationStatus,
                latestUpdate = entry.latestUpdate,
                score = entry.score?.let { score ->
                    app.openstory.catalog.canonical.CanonicalScore(score.value / score.scale, 1)
                },
            )
        }

    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flow {
        observeHomesSubscriptions++
        when {
            observeFailure -> error("catalog observation unavailable")
            observeFailuresBeforeSuccess > 0 -> {
                observeFailuresBeforeSuccess -= 1
                error("catalog observation unavailable")
            }
            observeFailureAfterEmission -> {
                emit(homes.value)
                error("catalog observation unavailable")
            }
            else -> homes.collect { emit(it) }
        }
    }
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null

    override suspend fun sourceRecord(key: CatalogMetadataKey): app.openstory.catalog.evidence.CatalogSourceRecord? = null

    override suspend fun sourceRecords(storyId: StoryId): List<app.openstory.catalog.evidence.CatalogSourceRecord> = emptyList()

    override suspend fun sourceRecords(): List<app.openstory.catalog.evidence.CatalogSourceRecord> {
        if (sourceRecordFailuresRemaining > 0) {
            sourceRecordFailuresRemaining--
            error("catalog unavailable")
        }
        return emptyList()
    }

    override suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> {
        if (applyHomeRefreshMutation) {
            val refreshed = CatalogHomeSnapshot(
                pluginId = mutation.pluginId,
                pluginVersion = mutation.pluginVersion,
                refreshedAtEpochMillis = mutation.refreshedAtEpochMillis,
                sections = mutation.sections,
            )
            replaceHomes(homes.value.filterNot { it.pluginId == mutation.pluginId } + refreshed)
        }
        return Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))
    }

    override suspend fun commitSearchSummaries(
        mutation: app.openstory.catalog.repository.CatalogSearchSummaryMutation,
    ) = app.openstory.common.Outcome.Failure(
        app.openstory.catalog.CatalogStoreFailure("test.search.unsupported", retryable = false),
    )

    override suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> = error("Discover must not commit Details")
}

private class FakeProjectionRepository(
    private val projections: List<CatalogStoryProjection>,
) : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(projections)
}

private class RecordingScopedProjectionRepository(
    private val projections: List<CatalogStoryProjection>,
) : CatalogStoryProjectionRepository {
    var observedStoryIds: Set<StoryId>? = null
        private set
    var observeAllCalls: Int = 0
        private set

    override fun observe(): Flow<List<CatalogStoryProjection>> {
        observeAllCalls += 1
        return flowOf(projections)
    }

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> {
        observedStoryIds = storyIds
        return flowOf(projections.filter { it.storyId in storyIds })
    }
}


private class SwitchingProjectionRepository(
    private val oldStoryId: StoryId,
    private val newStoryId: StoryId,
    projections: List<CatalogStoryProjection>,
    private val releaseNewFeed: CompletableDeferred<Unit>,
) : CatalogStoryProjectionRepository {
    private val byStory = projections.associateBy(CatalogStoryProjection::storyId)

    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(byStory.values.toList())

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> = when (storyIds) {
        setOf(oldStoryId) -> flow {
            emit(listOfNotNull(byStory[oldStoryId]))
            error("old projection observation unavailable")
        }
        setOf(newStoryId) -> flow {
            releaseNewFeed.await()
            emit(listOfNotNull(byStory[newStoryId]))
        }
        else -> flowOf(storyIds.mapNotNull(byStory::get))
    }

    override suspend fun find(storyId: StoryId): CatalogStoryProjection? = byStory[storyId]
}

private class RetryingProjectionRepository(
    private val projections: List<CatalogStoryProjection>,
) : CatalogStoryProjectionRepository {
    var scopedSubscriptions: Int = 0
        private set

    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(projections)

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> = flow {
        scopedSubscriptions += 1
        emit(projections.filter { it.storyId in storyIds })
        error("projection observation unavailable")
    }

    override suspend fun find(storyId: StoryId): CatalogStoryProjection? =
        projections.firstOrNull { it.storyId == storyId }
}

private fun cachedHome(pluginId: String = "catalog.a"): List<CatalogHomeSnapshot> {
    val pluginId = PluginId(pluginId)
    val storyId = StoryId("story-1")
    val entry = CatalogEntry(
        storyId = storyId,
        pluginId = pluginId,
        sourceId = "source-1",
        title = "Fixture Novel",
        authors = setOf("Fixture Author"),
        contentType = ContentType.MANGA,
        score = Score(8.4, 10.0),
    )
    return listOf(
        CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "trending",
                    title = "Trending",
                    items = listOf(entry),
                    kind = CatalogFeedKind.POPULAR,
                ),
            ),
        ),
    )
}

private fun emptyHome(
    pluginId: String,
    refreshedAtEpochMillis: Long,
): List<CatalogHomeSnapshot> = listOf(
    CatalogHomeSnapshot(
        pluginId = PluginId(pluginId),
        pluginVersion = "1.0.0",
        refreshedAtEpochMillis = refreshedAtEpochMillis,
        sections = emptyList(),
    ),
)

private fun popularHome(vararg storyIds: String): List<CatalogHomeSnapshot> {
    val pluginId = PluginId("catalog.a")
    val entries = storyIds.mapIndexed { index, rawStoryId ->
        CatalogEntry(
            storyId = StoryId(rawStoryId),
            pluginId = pluginId,
            sourceId = "source-${index + 1}",
            title = "Fixture Novel ${index + 1}",
            authors = setOf("Fixture Author"),
            contentType = ContentType.MANGA,
            popularityRank = (index + 1).toLong(),
        )
    }
    return listOf(
        CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "popular",
                    title = "Popular",
                    items = entries,
                    kind = CatalogFeedKind.POPULAR,
                ),
            ),
        ),
    )
}

private fun latestHome(
    pluginId: String = "catalog.a",
    count: Int = 1,
    coverUrl: String?,
): List<CatalogHomeSnapshot> {
    val pluginId = PluginId(pluginId)
    val items = (1..count).map { index ->
        CatalogEntry(
            storyId = StoryId("story-$index"),
            pluginId = pluginId,
            sourceId = "source-$index",
            title = "Fixture Novel $index",
            authors = setOf("Fixture Author"),
            contentType = ContentType.MANGA,
            coverUrl = coverUrl,
            latestUpdate = app.openstory.catalog.model.CatalogLatestUpdate(
                atEpochMillis = 1_000L - index,
                releaseLabel = index.toString(),
            ),
        )
    }
    return listOf(
        CatalogHomeSnapshot(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "latest",
                    title = "Latest",
                    items = items,
                    kind = CatalogFeedKind.LATEST_UPDATES,
                ),
            ),
        ),
    )
}
