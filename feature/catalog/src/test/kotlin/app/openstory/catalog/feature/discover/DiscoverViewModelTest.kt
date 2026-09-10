package app.openstory.catalog.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.failure.CatalogOperation
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.catalog.feature.state.toCatalogIssueUi
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.discover.DiscoverSessionState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun newViewModelDefaultsToMangaWithBothMediaOptionsEnabled() = runTest(dispatcher.scheduler) {
        val runtime = FakeDiscoverRuntime()
        val owner = TestViewModelOwner(runtime)

        advanceUntilIdle()

        assertEquals(CatalogMediaType.MANGA, owner.viewModel.state.value.selectedMediaType)
        assertEquals(
            listOf(CatalogMediaType.MANGA, CatalogMediaType.LIGHT_NOVEL),
            owner.viewModel.state.value.mediaOptions.map { it.mediaType },
        )
        assertTrue(owner.viewModel.state.value.mediaOptions.all { it.enabled })
        assertEquals(listOf(CatalogMediaType.MANGA), runtime.observedMedia)
        owner.clear()
    }

    @Test
    fun selectionLivesOnlyForTheExistingViewModelAndReplacesTheObservedScope() = runTest(dispatcher.scheduler) {
        val runtime = FakeDiscoverRuntime()
        val retainedStore = ViewModelStore()
        val owner = TestViewModelOwner(runtime, retainedStore)
        advanceUntilIdle()

        owner.viewModel.selectMedia(CatalogMediaType.LIGHT_NOVEL)
        advanceUntilIdle()

        assertEquals(CatalogMediaType.LIGHT_NOVEL, owner.viewModel.state.value.selectedMediaType)
        assertEquals(0, runtime.activeCollectors.getValue(CatalogMediaType.MANGA))
        assertEquals(1, runtime.activeCollectors.getValue(CatalogMediaType.LIGHT_NOVEL))
        assertEquals(
            listOf(CatalogMediaType.MANGA, CatalogMediaType.LIGHT_NOVEL),
            runtime.observedMedia,
        )
        val recreatedConfiguration = TestViewModelOwner(FakeDiscoverRuntime(), retainedStore)
        assertSame(owner.viewModel, recreatedConfiguration.viewModel)
        assertEquals(CatalogMediaType.LIGHT_NOVEL, recreatedConfiguration.viewModel.state.value.selectedMediaType)
        recreatedConfiguration.clear()

        val recreatedAfterProcessDeath = TestViewModelOwner(FakeDiscoverRuntime())
        advanceUntilIdle()
        assertEquals(CatalogMediaType.MANGA, recreatedAfterProcessDeath.viewModel.state.value.selectedMediaType)
        recreatedAfterProcessDeath.clear()
    }

    @Test
    fun reducerDistinguishesAbsentEmptyContentAndNonDestructiveRefresh() = runTest(dispatcher.scheduler) {
        val runtime = FakeDiscoverRuntime()
        val owner = TestViewModelOwner(runtime)
        advanceUntilIdle()

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(DiscoverPersistenceState.Absent, CatalogAcquisitionStatus.Running),
        )
        advanceUntilIdle()
        assertEquals(DiscoverContentState.NoContentLoading, owner.viewModel.state.value.content)

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(
                DiscoverPersistenceState.Absent,
                CatalogAcquisitionStatus.Failed(CatalogFailure.SourceUnavailable),
            ),
        )
        advanceUntilIdle()
        assertEquals(
            DiscoverContentState.NoContentFailure(
                CatalogIssueUi(CatalogIssueKind.SOURCE_UNAVAILABLE, retryable = false),
            ),
            owner.viewModel.state.value.content,
        )

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(published(emptyList()), CatalogAcquisitionStatus.Idle),
        )
        advanceUntilIdle()
        assertEquals(DiscoverContentState.Empty(), owner.viewModel.state.value.content)

        val cards = listOf(card(CatalogSectionKind.POPULAR, 0, "Persistent title"))
        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(published(cards), CatalogAcquisitionStatus.Idle),
        )
        advanceUntilIdle()
        val content = owner.viewModel.state.value.content as DiscoverContentState.Content
        assertEquals("Persistent title", content.sections.single().cards.single().title)
        assertEquals(cards.single().coverLocator, content.sections.single().cards.single().coverLocator)
        assertEquals(cards.single().coverAssetKey, content.sections.single().cards.single().coverAssetKey)
        assertFalse(content.refreshing)
        assertEquals(null, content.issue)

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(published(cards), CatalogAcquisitionStatus.Running),
        )
        advanceUntilIdle()
        val refreshing = owner.viewModel.state.value.content as DiscoverContentState.Content
        assertEquals("Persistent title", refreshing.sections.single().cards.single().title)
        assertTrue(refreshing.refreshing)

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(
                published(cards),
                CatalogAcquisitionStatus.Failed(CatalogFailure.Acquisition(CatalogOperation.DISCOVER)),
            ),
        )
        advanceUntilIdle()
        val failedRefresh = owner.viewModel.state.value.content as DiscoverContentState.Content
        assertEquals("Persistent title", failedRefresh.sections.single().cards.single().title)
        assertEquals(CatalogIssueKind.ACQUISITION_FAILED, failedRefresh.issue?.kind)
        owner.clear()
    }

    @Test
    fun storageReadFailureWithoutUsableSnapshotIsFatalAndSafe() = runTest(dispatcher.scheduler) {
        val runtime = FakeDiscoverRuntime()
        val owner = TestViewModelOwner(runtime)
        advanceUntilIdle()

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(
                persistence = null,
                acquisition = CatalogAcquisitionStatus.Failed(
                    CatalogFailure.Storage(CatalogStorageOperation.READ_DISCOVER),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            DiscoverContentState.NoContentFailure(
                CatalogIssueUi(CatalogIssueKind.STORAGE_FAILED, retryable = true),
            ),
            owner.viewModel.state.value.content,
        )
        owner.clear()
    }

    @Test
    fun unavailableActivationRemainsVisibleWhenMediaSelectionChanges() = runTest(dispatcher.scheduler) {
        val owner = TestViewModelOwner(
            object : DiscoverRuntime {
                override suspend fun activate(): DiscoverRuntimeActivation =
                    DiscoverRuntimeActivation.Unavailable(CatalogFailure.SourceUnavailable)

                override fun close() = Unit
            },
        )
        advanceUntilIdle()

        owner.viewModel.selectMedia(CatalogMediaType.LIGHT_NOVEL)

        assertEquals(CatalogMediaType.LIGHT_NOVEL, owner.viewModel.state.value.selectedMediaType)
        assertEquals(
            DiscoverContentState.NoContentFailure(
                CatalogIssueUi(CatalogIssueKind.SOURCE_UNAVAILABLE, retryable = false),
            ),
            owner.viewModel.state.value.content,
        )
        owner.clear()
    }

    @Test
    fun sectionPolicyKeepsSemanticOrderCapsAndOneSnapshotRead() = runTest(dispatcher.scheduler) {
        val runtime = FakeDiscoverRuntime()
        val owner = TestViewModelOwner(runtime)
        advanceUntilIdle()
        val oversized = buildList {
            repeat(6) { add(card(CatalogSectionKind.POPULAR, it, "Popular $it")) }
            repeat(10) { add(card(CatalogSectionKind.LATEST_UPDATES, it, "Latest $it")) }
            repeat(6) { add(card(CatalogSectionKind.TOP_RATED, it, "Top $it")) }
        }

        runtime.emit(
            CatalogMediaType.MANGA,
            DiscoverSessionState(published(oversized), CatalogAcquisitionStatus.Idle),
        )
        advanceUntilIdle()

        val content = owner.viewModel.state.value.content as DiscoverContentState.Content
        assertEquals(
            listOf(
                CatalogSectionKind.POPULAR,
                CatalogSectionKind.LATEST_UPDATES,
                CatalogSectionKind.TOP_RATED,
            ),
            content.sections.map { it.kind },
        )
        assertEquals(listOf(5, 9, 5), content.sections.map { it.cards.size })
        assertEquals(19, content.sections.sumOf { it.cards.size })
        assertEquals(listOf(CatalogMediaType.MANGA), runtime.observedMedia)
        assertTrue(runtime.refreshCalls.isEmpty())
        owner.clear()
    }

    @Test
    fun issueMappingContainsOnlyKindAndRetryability() {
        val cases = listOf(
            CatalogFailure.SourceUnavailable to CatalogIssueUi(CatalogIssueKind.SOURCE_UNAVAILABLE, false),
            CatalogFailure.Validation("https://private.example/payload", CatalogValidationReason.MALFORMED) to
                CatalogIssueUi(CatalogIssueKind.INVALID_SOURCE_DATA, false),
            CatalogFailure.IdentityCollision("plugin-secret-story-id") to
                CatalogIssueUi(CatalogIssueKind.INVALID_SOURCE_DATA, false),
            CatalogFailure.Acquisition(CatalogOperation.DISCOVER) to
                CatalogIssueUi(CatalogIssueKind.ACQUISITION_FAILED, true),
            CatalogFailure.Storage(CatalogStorageOperation.READ_DISCOVER) to
                CatalogIssueUi(CatalogIssueKind.STORAGE_FAILED, true),
            CatalogFailure.Artwork(CatalogArtworkFailureReason.IO_FAILED) to
                CatalogIssueUi(CatalogIssueKind.ARTWORK_FAILED, true),
            CatalogFailure.Artwork(CatalogArtworkFailureReason.INVALID_LOCATOR) to
                CatalogIssueUi(CatalogIssueKind.ARTWORK_FAILED, false),
            CatalogFailure.InternalInvariant("host-list=private.example") to
                CatalogIssueUi(CatalogIssueKind.INTERNAL_FAILURE, false),
        )

        cases.forEach { (failure, expected) ->
            val actual = failure.toCatalogIssueUi()
            assertEquals(expected, actual)
            assertFalse(actual.toString().contains("private.example"))
            assertFalse(actual.toString().contains("plugin-secret"))
        }
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverMappedToAnIssue() {
        CancellationException("caller cancelled").toCatalogIssueUi()
    }

    @Test
    fun unexpectedThrowableMapsToInternalWithoutLeakingItsMessage() {
        val issue = IllegalStateException("https://private.example/host-list").toCatalogIssueUi()

        assertEquals(CatalogIssueUi(CatalogIssueKind.INTERNAL_FAILURE, false), issue)
        assertFalse(issue.toString().contains("private.example"))
    }

    private class TestViewModelOwner(
        runtime: DiscoverRuntime,
        override val viewModelStore: ViewModelStore = ViewModelStore(),
    ) : ViewModelStoreOwner {
        val viewModel: DiscoverViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiscoverViewModel(runtime) as T
            },
        )[DiscoverViewModel::class.java]

        fun clear() = viewModelStore.clear()
    }

    private class FakeDiscoverRuntime : DiscoverRuntime {
        private val flows = CatalogMediaType.entries.associateWith {
            MutableSharedFlow<DiscoverSessionState>(replay = 1)
        }
        val observedMedia = mutableListOf<CatalogMediaType>()
        val refreshCalls = mutableListOf<CatalogMediaType>()
        val activeCollectors = CatalogMediaType.entries.associateWith { 0 }.toMutableMap()

        override suspend fun activate(): DiscoverRuntimeActivation = DiscoverRuntimeActivation.Available(
            observe = { mediaType ->
                flows.getValue(mediaType)
                    .onStart {
                        observedMedia += mediaType
                        activeCollectors[mediaType] = activeCollectors.getValue(mediaType) + 1
                    }
                    .onCompletion {
                        activeCollectors[mediaType] = activeCollectors.getValue(mediaType) - 1
                    }
            },
            refresh = { mediaType ->
                refreshCalls += mediaType
                CatalogAcquisitionResult.Success
            },
        )

        suspend fun emit(mediaType: CatalogMediaType, state: DiscoverSessionState) {
            flows.getValue(mediaType).emit(state)
        }

        override fun close() = Unit
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("discover-view-model-test")
        val COVER_LOCATOR = CoverLocator.TrustedLocalResource("debug:manga:cover-a", "1")

        fun published(cards: List<DiscoverCard>) = DiscoverPersistenceState.Published(
            generation = 7L,
            provenance = AcquisitionProvenance(SOURCE_KEY, "fixture-v1", 123L),
            cards = cards,
        )

        fun card(kind: CatalogSectionKind, position: Int, title: String): DiscoverCard {
            val sourceStoryId = "${kind.name.lowercase()}-$position"
            val sourceKey = SourceStoryKey(SOURCE_KEY, sourceStoryId)
            val ref = StorySourceRef(
                    storyId = SourceStoryIdV1.derive(sourceKey),
                    catalogSourceKey = SOURCE_KEY,
                    sourceStoryId = sourceStoryId,
                )
            val coverKey = CoverAssetKey(
                ref.storyId,
                CoverRevisionV1.local(COVER_LOCATOR.logicalAssetId, COVER_LOCATOR.assetVersion),
            )
            return DiscoverCard(
                ref = ref,
                sectionKind = kind,
                itemPosition = position,
                title = title,
                contentType = CatalogMediaType.MANGA,
                sourceVersion = "fixture-v1",
                coverLocator = COVER_LOCATOR,
                coverAssetKey = coverKey,
                rating = CatalogRating(8.5, 10.0),
                publicationStatusSummary = "Ongoing",
                latestUpdateEpochMs = 123L,
            )
        }
    }
}
