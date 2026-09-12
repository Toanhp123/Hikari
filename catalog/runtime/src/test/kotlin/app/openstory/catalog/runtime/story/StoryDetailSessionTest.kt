package app.openstory.catalog.runtime.story

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.RecordingSource
import app.openstory.catalog.runtime.RuntimeFakeStorage
import app.openstory.catalog.runtime.TEST_BINDING
import app.openstory.catalog.runtime.TEST_SOURCE_KEY
import app.openstory.catalog.runtime.assertCatalogFailure
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.testRef
import app.openstory.catalog.runtime.testStoryAcquisition
import app.openstory.catalog.runtime.trace.CatalogTrace
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailSessionTest {
    @Test
    fun activeStoryDemandEmitsRequestedAndFirstContentReadyOnce() = runTest {
        val storage = RuntimeFakeStorage()
        val traces = mutableListOf<String>()
        val ref = testRef()
        val activation = available(storage, RecordingSource(), CatalogTraceSink(traces::add))
        traces.clear()
        val session = activation.storyDetailSession(ref)

        session.activate().launchIn(backgroundScope)
        runCurrent()
        storage.storyFlow(ref).emit(cachedDetail(ref))
        storage.storyFlow(ref).emit(cachedDetail(ref))
        advanceUntilIdle()

        assertEquals(
            listOf(
                CatalogTrace.STORY_DETAIL_REQUESTED,
                CatalogTrace.STORY_DETAIL_CONTENT_READY,
            ),
            traces,
        )
    }

    @Test
    fun twoMissingDetailDemandsJoinOneKeyedAcquisition() = runTest {
        val storage = RuntimeFakeStorage()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val source = RecordingSource(story = { ref ->
            entered.complete(Unit)
            release.await()
            testStoryAcquisition(ref.sourceStoryId)
        })
        val activation = available(storage, source)
        val ref = testRef()
        val firstSession = activation.storyDetailSession(ref)
        val secondSession = activation.storyDetailSession(ref)
        firstSession.activate().launchIn(backgroundScope)
        secondSession.activate().launchIn(backgroundScope)
        runCurrent()

        storage.storyFlow(ref).emit(missingDetail(ref))
        runCurrent()
        entered.await()

        assertEquals(listOf(ref), source.storyCalls)
        assertEquals(1, storage.storyObserveCount)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, storage.storyCommands.size)
    }

    @Test
    fun multipleStateCollectorsShareOneKeyedPersistenceObserver() = runTest {
        val storage = RuntimeFakeStorage()
        val ref = testRef()
        val states = available(storage, RecordingSource()).storyDetailSession(ref).activate()

        states.launchIn(backgroundScope)
        states.launchIn(backgroundScope)
        runCurrent()

        assertEquals(1, storage.storyObserveCount)
    }

    @Test
    fun releaseRemovesTheKeyedDemandSession() = runTest {
        val storage = RuntimeFakeStorage()
        val activation = available(storage, RecordingSource())
        val ref = testRef()
        val first = activation.storyDetailSession(ref)
        first.activate()

        first.release()
        val second = activation.storyDetailSession(ref)

        assertNotSame(first, second)
        assertEquals(listOf(ref), storage.releases)
    }

    @Test
    fun cachedDetailDoesNotAcquire() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource()
        val ref = testRef()
        available(storage, source).storyDetailSession(ref).activate().launchIn(backgroundScope)
        runCurrent()

        storage.storyFlow(ref).emit(cachedDetail(ref))
        advanceUntilIdle()

        assertTrue(source.storyCalls.isEmpty())
    }

    @Test
    fun explicitRetryMayRerunAfterFailedMissingDetailAcquisition() = runTest {
        val storage = RuntimeFakeStorage()
        var attempts = 0
        val source = RecordingSource(story = { ref ->
            attempts += 1
            if (attempts == 1) error("first attempt")
            testStoryAcquisition(ref.sourceStoryId)
        })
        val ref = testRef()
        val session = available(storage, source).storyDetailSession(ref)
        val states = mutableListOf<StoryDetailSessionState>()
        session.activate().onEach(states::add).launchIn(backgroundScope)
        runCurrent()
        storage.storyFlow(ref).emit(missingDetail(ref))
        runCurrent()
        advanceUntilIdle()

        assertTrue(states.any { it.acquisition is CatalogAcquisitionStatus.Failed })
        session.retry()
        runCurrent()
        advanceUntilIdle()

        assertEquals(2, source.storyCalls.size)
        assertEquals(1, storage.storyCommands.size)
    }

    @Test
    fun mismatchedRefFailsBeforeSourceExecution() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource()
        val otherRef = testRef(sourceKey = CatalogSourceKey("other.source"))
        val session = available(storage, source).storyDetailSession(otherRef)

        val thrown = assertCatalogFailure { session.activate() }

        assertEquals(
            CatalogFailure.Validation("ref.catalogSourceKey", CatalogValidationReason.AUTHORITY_MISMATCH),
            thrown.failure,
        )
        assertTrue(source.storyCalls.isEmpty())
        assertTrue(storage.touches.isEmpty())
    }

    @Test
    fun activationTouchesAccessExactlyOnceDespiteRepeatedEmissions() = runTest {
        val storage = RuntimeFakeStorage()
        val source = RecordingSource()
        val ref = testRef()
        val session = available(storage, source).storyDetailSession(ref)
        session.activate().launchIn(backgroundScope)
        session.activate()
        runCurrent()

        storage.storyFlow(ref).emit(cachedDetail(ref))
        storage.storyFlow(ref).emit(cachedDetail(ref))
        advanceUntilIdle()

        assertEquals(listOf(ref to 700L), storage.touches)
    }

    @Test
    fun failedAccessTouchRollsBackTheRegisteredPin() = runTest {
        val storage = RuntimeFakeStorage().apply { touchFailure = IllegalStateException("touch failed") }
        val activation = available(storage, RecordingSource())

        assertCatalogFailure { activation.storyDetailSession(testRef("failed")).activate() }
        storage.touchFailure = null
        activation.storyDetailSession(testRef("one")).activate()
        activation.storyDetailSession(testRef("two")).activate()

        assertEquals(2, storage.touches.size)
    }

    @Test
    fun storyReadFailureDoesNotTriggerAcquisition() = runTest {
        val storage = RuntimeFakeStorage().apply { storyReadFailure = IllegalStateException("read failed") }
        val source = RecordingSource()
        val states = mutableListOf<StoryDetailSessionState>()
        available(storage, source).storyDetailSession(testRef()).activate()
            .onEach(states::add).launchIn(backgroundScope)
        runCurrent()

        assertTrue(
            states.any {
                it.acquisition == CatalogAcquisitionStatus.Failed(
                    CatalogFailure.Storage(CatalogStorageOperation.READ_STORY),
                )
            },
        )
        assertTrue(source.storyCalls.isEmpty())
    }

    private suspend fun TestScope.available(
        storage: RuntimeFakeStorage,
        source: RecordingSource,
        traceSink: CatalogTraceSink = CatalogTraceSink {},
    ): CatalogCapabilityActivation.Available {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val activation = CatalogRuntimeFactory(
            binding = TEST_BINDING.copy(acquisitionSource = source),
            openStorage = { storage },
            wallClockEpochMs = { 700L },
            dispatchers = CatalogExecutionDispatchers(dispatcher, dispatcher),
            traceSink = traceSink,
        ).createSession().activate()
        return activation as CatalogCapabilityActivation.Available
    }

    private fun missingDetail(ref: StorySourceRef) = StoryDetailProjection(
        ref = ref,
        summary = summary(ref),
        detail = null,
        detailProvenance = null,
    )

    private fun cachedDetail(ref: StorySourceRef) = StoryDetailProjection(
        ref = ref,
        summary = summary(ref),
        detail = StoryRichDetailProjection(
            description = "Cached",
            authors = emptyList(),
            artists = emptyList(),
            genres = emptyList(),
            publicationStatus = null,
            language = null,
        ),
        detailProvenance = AcquisitionProvenance(TEST_SOURCE_KEY, "host-v7", 3L),
    )

    private fun summary(ref: StorySourceRef) = StorySummaryProjection(
        ref = ref,
        title = "Story One",
        contentType = CatalogMediaType.MANGA,
        sourceVersion = "host-v7",
        coverLocator = null,
        coverAssetKey = null,
        rating = null,
        publicationStatusSummary = null,
        latestUpdateEpochMs = null,
    )
}
