package app.openstory.story.feature

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryArtworkSnapshot
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPresentationSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryLibraryFacetTest {
    @Test
    fun addUsesVisibleSnapshotIgnoresDuplicateTapAndRestoresNotSavedOnFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val catalog = FakeCatalogFacet()
        val addStarted = CompletableDeferred<Unit>()
        val finishAdd = CompletableDeferred<Unit>()
        val library = FakeLibraryFacet(
            addBlock = { _, _, snapshot ->
                addStarted.complete(Unit)
                finishAdd.await()
                error("write failed")
            },
        )
        val owner = StoryPresentationOwner(
            args = StoryRouteArgs(REF, CatalogMediaType.MANGA),
            catalogFacet = catalog,
            libraryFacet = library,
            coroutineScope = CoroutineScope(dispatcher),
        )

        owner.activate()
        runCurrent()
        library.emit(null)
        catalog.emit(detailState())
        runCurrent()

        owner.toggleLibraryMembership()
        owner.toggleLibraryMembership()
        addStarted.await()

        assertEquals(LibraryMembershipUi.Saving, owner.state.value.libraryMembership)
        assertEquals(1, library.addCalls)
        assertEquals(1, catalog.activationCalls)
        assertEquals(
            LibraryPresentationSnapshot(
                title = "Story 17",
                artwork = LibraryArtworkSnapshot(COVER_KEY, COVER_LOCATOR),
                supportingText = "Manga",
            ),
            library.addedSnapshots.single(),
        )

        finishAdd.complete(Unit)
        advanceUntilIdle()

        assertEquals(LibraryMembershipUi.NotSaved, owner.state.value.libraryMembership)
        assertTrue(owner.state.value.libraryMutationFailed)
        assertEquals(1, catalog.activationCalls)
    }

    @Test
    fun removeFailureRestoresSavedMembership() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = FakeLibraryFacet(
            initialMembership = savedEntry(),
            removeBlock = { error("delete failed") },
        )
        val owner = owner(dispatcher, FakeCatalogFacet(), library)

        owner.activate()
        runCurrent()
        assertEquals(LibraryMembershipUi.Saved, owner.state.value.libraryMembership)

        owner.toggleLibraryMembership()
        advanceUntilIdle()

        assertEquals(1, library.removeCalls)
        assertEquals(LibraryMembershipUi.Saved, owner.state.value.libraryMembership)
        assertTrue(owner.state.value.libraryMutationFailed)
    }

    @Test
    fun retainedRouteQuiescesMembershipObservationAndReactivationReconcilesLocalTruth() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = FakeLibraryFacet()
        val catalog = FakeCatalogFacet()
        val owner = owner(dispatcher, catalog, library)

        owner.activate()
        runCurrent()
        assertEquals(1, library.observationStarts)

        owner.quiesce()
        advanceUntilIdle()
        assertEquals(1, library.observationStops)

        library.setMembership(savedEntry())
        runCurrent()
        assertEquals(LibraryMembershipUi.NotSaved, owner.state.value.libraryMembership)

        owner.activate()
        runCurrent()

        assertEquals(2, library.observationStarts)
        assertEquals(LibraryMembershipUi.Saved, owner.state.value.libraryMembership)
    }

    @Test
    fun trustedCatalogChangesEnrichSavedSnapshotWithoutRepeatingIdenticalWrites() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val initial = savedEntry(title = "Story 17")
        val library = FakeLibraryFacet(initialMembership = initial)
        val catalog = FakeCatalogFacet()
        val owner = owner(dispatcher, catalog, library)

        owner.activate()
        runCurrent()
        catalog.emit(detailState(title = "Story 17"))
        advanceUntilIdle()
        assertEquals(0, library.enrichCalls)

        catalog.emit(detailState(title = "Story 17 Remastered"))
        advanceUntilIdle()
        catalog.emit(detailState(title = "Story 17 Remastered"))
        advanceUntilIdle()

        assertEquals(1, library.enrichCalls)
        assertEquals("Story 17 Remastered", library.enrichedSnapshots.single().title)
        assertEquals(initial.savedAtEpochMs, library.currentMembership()?.savedAtEpochMs)
    }

    @Test
    fun catalogReductionPreservesInFlightLibraryState() {
        val previous = initialStoryDetailUiState(
            StoryRouteArgs(REF, CatalogMediaType.MANGA),
        ).copy(libraryMembership = LibraryMembershipUi.Saving)

        val reduced = detailState().toStoryDetailUiState(previous)

        assertEquals(LibraryMembershipUi.Saving, reduced.libraryMembership)
    }

    @Test
    fun visiblePreviewIsEnoughToOfferLibraryActionBeforeRichDetailLoads() {
        val visiblePreview = initialStoryDetailUiState(
            StoryRouteArgs(
                ref = REF,
                originMediaContext = CatalogMediaType.MANGA,
                preview = app.openstory.catalog.domain.read.StoryRoutePreview(title = "Preview"),
            ),
        )
        val noVisibleStory = initialStoryDetailUiState(
            StoryRouteArgs(REF, CatalogMediaType.MANGA),
        )

        assertTrue(visiblePreview.canPresentLibraryAction)
        assertTrue(!noVisibleStory.canPresentLibraryAction)
    }

    @Test
    fun membershipObservationCannotOverwriteInFlightMutationState() {
        val saving = initialStoryDetailUiState(
            StoryRouteArgs(REF, CatalogMediaType.MANGA),
        ).copy(libraryMembership = LibraryMembershipUi.Saving)
        val removing = saving.copy(libraryMembership = LibraryMembershipUi.Removing)

        assertEquals(LibraryMembershipUi.Saving, saving.withLibraryMembership(savedEntry()).libraryMembership)
        assertEquals(LibraryMembershipUi.Removing, removing.withLibraryMembership(null).libraryMembership)
    }

    @Test
    fun releasedRouteLetsStartedLibraryCommitFinish() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val addStarted = CompletableDeferred<Unit>()
        val finishAdd = CompletableDeferred<Unit>()
        val addFinished = CompletableDeferred<Unit>()
        val catalog = FakeCatalogFacet()
        val library = FakeLibraryFacet(
            addBlock = { _, _, _ ->
                addStarted.complete(Unit)
                finishAdd.await()
                addFinished.complete(Unit)
                LibraryMutationResult.CHANGED
            },
        )
        val owner = owner(dispatcher, catalog, library)

        owner.activate()
        runCurrent()
        catalog.emit(detailState())
        runCurrent()
        owner.toggleLibraryMembership()
        addStarted.await()

        owner.release()
        finishAdd.complete(Unit)
        advanceUntilIdle()

        assertTrue(addFinished.isCompleted)
        assertEquals(LibraryMembershipUi.Saving, owner.state.value.libraryMembership)
    }

    private fun owner(
        dispatcher: CoroutineDispatcher,
        catalog: FakeCatalogFacet,
        library: FakeLibraryFacet,
    ) = StoryPresentationOwner(
        args = StoryRouteArgs(REF, CatalogMediaType.MANGA),
        catalogFacet = catalog,
        libraryFacet = library,
        coroutineScope = CoroutineScope(dispatcher),
    )

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("task9.test")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
        val COVER_LOCATOR = CoverLocator.TrustedLocalResource("cover-17", "1.0")
        val COVER_KEY = CoverAssetKey(REF.storyId, CoverRevisionV1.local("cover-17", "1.0"))

        fun detailState(title: String = "Story 17") = StoryDetailSessionState(
            projection = StoryDetailProjection(
                ref = REF,
                summary = StorySummaryProjection(
                    ref = REF,
                    title = title,
                    contentType = CatalogMediaType.MANGA,
                    sourceVersion = "1.0",
                    coverLocator = COVER_LOCATOR,
                    coverAssetKey = COVER_KEY,
                    rating = null,
                    publicationStatusSummary = "Ongoing",
                    latestUpdateEpochMs = null,
                ),
                detail = StoryRichDetailProjection(
                    description = "Visible synopsis",
                    authors = emptyList(),
                    artists = emptyList(),
                    genres = emptyList(),
                    publicationStatus = "Ongoing",
                    language = "en",
                ),
                detailProvenance = AcquisitionProvenance(SOURCE_KEY, "1.0", 1L),
            ),
            acquisition = CatalogAcquisitionStatus.Success,
        )

        fun savedEntry(title: String = "Story 17") = LibraryEntry(
            ref = REF,
            originMediaContext = CatalogMediaType.MANGA,
            savedAtEpochMs = 41L,
            snapshot = LibraryPresentationSnapshot(
                title = title,
                artwork = LibraryArtworkSnapshot(COVER_KEY, COVER_LOCATOR),
                supportingText = "Manga",
            ),
        )
    }

    private class FakeCatalogFacet : StoryCatalogFacet {
        private val states = MutableSharedFlow<StoryDetailSessionState>()
        var activationCalls = 0

        override suspend fun activate(ref: StorySourceRef): StoryCatalogFacetActivation {
            activationCalls += 1
            return StoryCatalogFacetActivation.Available(
                states = states,
                retry = { CatalogAcquisitionResult.Success },
                release = {},
            )
        }

        suspend fun emit(state: StoryDetailSessionState) = states.emit(state)
    }

    private class FakeLibraryFacet(
        initialMembership: LibraryEntry? = null,
        private val addBlock: suspend (
            StorySourceRef,
            CatalogMediaType,
            LibraryPresentationSnapshot,
        ) -> LibraryMutationResult = { _, _, _ -> LibraryMutationResult.CHANGED },
        private val removeBlock: suspend (StorySourceRef) -> LibraryMutationResult = {
            LibraryMutationResult.CHANGED
        },
    ) : StoryLibraryFacet {
        private val memberships = MutableStateFlow(initialMembership)
        val addedSnapshots = mutableListOf<LibraryPresentationSnapshot>()
        val enrichedSnapshots = mutableListOf<LibraryPresentationSnapshot>()
        var addCalls = 0
        var removeCalls = 0
        var enrichCalls = 0
        var observationStarts = 0
        var observationStops = 0

        override fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> = memberships
            .onStart { observationStarts += 1 }
            .onCompletion { observationStops += 1 }

        override suspend fun add(
            ref: StorySourceRef,
            originMediaContext: CatalogMediaType,
            snapshot: LibraryPresentationSnapshot,
        ): LibraryMutationResult {
            addCalls += 1
            addedSnapshots += snapshot
            return addBlock(ref, originMediaContext, snapshot)
        }

        override suspend fun remove(ref: StorySourceRef): LibraryMutationResult {
            removeCalls += 1
            return removeBlock(ref)
        }

        override suspend fun enrichSnapshot(
            ref: StorySourceRef,
            snapshot: LibraryPresentationSnapshot,
        ): LibraryMutationResult {
            enrichCalls += 1
            enrichedSnapshots += snapshot
            memberships.value = memberships.value?.copy(snapshot = snapshot)
            return LibraryMutationResult.CHANGED
        }

        suspend fun emit(entry: LibraryEntry?) = memberships.emit(entry)
        fun setMembership(entry: LibraryEntry?) {
            memberships.value = entry
        }
        fun currentMembership(): LibraryEntry? = memberships.value
    }
}
