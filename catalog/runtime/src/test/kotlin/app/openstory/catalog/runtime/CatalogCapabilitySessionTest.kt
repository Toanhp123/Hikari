package app.openstory.catalog.runtime

import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.trace.CatalogTrace
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CatalogCapabilitySessionTest {
    @Test
    fun storageRemainsClosedUntilDemandActivationAndOpensOnlyOnce() = runTest {
        val storage = RuntimeFakeStorage()
        var openCount = 0
        val session = testFactory(
            binding = TEST_BINDING,
            openStorage = {
                openCount += 1
                storage
            },
        ).createSession()

        assertEquals(0, openCount)

        val first = session.activate()
        val second = session.activate()

        assertEquals(1, openCount)
        assertSame(first, second)
    }

    @Test
    fun successfulActivationAndFirstDiscoverSnapshotEmitOrderedSessionTracesOnce() = runTest {
        val storage = RuntimeFakeStorage()
        val traces = mutableListOf<String>()
        val session = testFactory(
            binding = TEST_BINDING,
            openStorage = { storage },
            traceSink = CatalogTraceSink(traces::add),
        ).createSession()

        val first = session.activate() as CatalogCapabilityActivation.Available
        val second = session.activate() as CatalogCapabilityActivation.Available
        storage.discoverFlows.getValue(CatalogMediaType.MANGA)
            .emit(app.openstory.catalog.domain.read.DiscoverPersistenceState.Absent)
        storage.discoverFlows.getValue(CatalogMediaType.LIGHT_NOVEL)
            .emit(app.openstory.catalog.domain.read.DiscoverPersistenceState.Absent)
        first.discoverSession(CatalogMediaType.MANGA).states.first()
        second.discoverSession(CatalogMediaType.MANGA).states.first()
        first.discoverSession(CatalogMediaType.LIGHT_NOVEL).states.first()

        assertEquals(
            listOf(
                CatalogTrace.ACTIVATION_START,
                CatalogTrace.STORAGE_READY,
                CatalogTrace.DISCOVER_FIRST_SNAPSHOT,
            ),
            traces,
        )
    }

    @Test
    fun absentReleaseBindingReturnsUnavailableWithoutOpeningStorage() = runTest {
        var openCount = 0
        val session = testFactory(
            binding = null,
            openStorage = {
                openCount += 1
                RuntimeFakeStorage()
            },
        ).createSession()

        val activation = session.activate()

        assertEquals(CatalogCapabilityActivation.Unavailable(CatalogFailure.SourceUnavailable), activation)
        assertEquals(0, openCount)
    }

    @Test
    fun storageOpenFailureIsTyped() = runTest {
        val session = testFactory(
            binding = TEST_BINDING,
            openStorage = { error("open failed") },
        ).createSession()

        val thrown = assertCatalogFailure { session.activate() }

        assertEquals(CatalogFailure.Storage(CatalogStorageOperation.OPEN), thrown.failure)
    }

    @Test
    fun storageOpenCancellationPropagatesUnchanged() = runTest {
        val cancellation = CancellationException("cancel open")
        val session = testFactory(
            binding = TEST_BINDING,
            openStorage = { throw cancellation },
        ).createSession()

        val thrown = try {
            session.activate()
            throw AssertionError("Expected cancellation")
        } catch (error: CancellationException) {
            error
        }

        assertEquals(cancellation.message, thrown.message)
    }

    @Test
    fun activatedRuntimeExposesOnlyTheImmutableBindingAssetPolicy() = runTest {
        val policy = SourceAssetPolicy(TEST_SOURCE_KEY, setOf("images.example.com"))
        val activation = testFactory(
            binding = TEST_BINDING.copy(assetPolicy = policy),
            openStorage = { RuntimeFakeStorage() },
        ).createSession().activate() as CatalogCapabilityActivation.Available

        assertSame(policy, activation.assetPolicyProvider.policyFor(TEST_SOURCE_KEY))
        assertEquals(
            null,
            activation.assetPolicyProvider.policyFor(
                app.openstory.catalog.domain.identity.CatalogSourceKey("other.source"),
            ),
        )
    }

    @Test
    fun explicitDiscoverAcquisitionRunsThroughTheBoundSourceAndImporter() = runTest {
        val source = RecordingSource()
        val storage = RuntimeFakeStorage()
        val activation = testFactory(
            binding = TEST_BINDING.copy(acquisitionSource = source),
            openStorage = { storage },
        ).createSession().activate() as CatalogCapabilityActivation.Available

        val result = activation.acquireDiscover(CatalogMediaType.LIGHT_NOVEL)

        assertEquals(CatalogAcquisitionResult.Success, result)
        assertEquals(listOf(CatalogMediaType.LIGHT_NOVEL), source.discoverCalls)
        assertEquals(CatalogMediaType.LIGHT_NOVEL, storage.discoverCommands.single().mediaType)
    }

    @Test
    fun runtimeDiagnosticsReportWorkAndPinOwnershipReturningToZero() = runTest {
        val activeWorkCounts = mutableListOf<Int>()
        val activePinCounts = mutableListOf<Int>()
        val discoverTouchedCounts = mutableListOf<Int>()
        val releaseTouchedCounts = mutableListOf<Int>()
        val activation = testFactory(
            binding = TEST_BINDING.copy(acquisitionSource = RecordingSource()),
            openStorage = { RuntimeFakeStorage() },
            ownershipCallbacks = CatalogRuntimeOwnershipCallbacks(
                onActiveWorkChanged = activeWorkCounts::add,
                onActiveStoryPinsChanged = activePinCounts::add,
                onDiscoverMutationTouched = discoverTouchedCounts::add,
                onStoryReleaseMutationTouched = releaseTouchedCounts::add,
            ),
        ).createSession().activate() as CatalogCapabilityActivation.Available

        assertEquals(CatalogAcquisitionResult.Success, activation.acquireDiscover(CatalogMediaType.MANGA))
        val storySession = activation.storyDetailSession(testRef())
        storySession.activate()
        storySession.release()

        assertEquals(listOf(1, 0), activeWorkCounts)
        assertEquals(listOf(1, 0), activePinCounts)
        assertEquals(listOf(1), discoverTouchedCounts)
        assertEquals(listOf(1), releaseTouchedCounts)
    }

    private fun TestScope.testFactory(
        binding: CatalogSourceBinding?,
        openStorage: suspend () -> RuntimeFakeStorage,
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    ): CatalogRuntimeFactory {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CatalogRuntimeFactory(
            binding = binding,
            openStorage = openStorage,
            wallClockEpochMs = { 44L },
            dispatchers = CatalogExecutionDispatchers(cpu = dispatcher, io = dispatcher),
            traceSink = traceSink,
            ownershipCallbacks = ownershipCallbacks,
        )
    }
}
