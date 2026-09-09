package app.openstory.catalog.runtime.retention

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.runtime.acquisition.CatalogImporter
import app.openstory.catalog.runtime.concurrency.CatalogMutationGate
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveStoryPinsTest {
    @Test
    fun activePinSetRejectsAThirdDistinctStoryWithoutGrowing() = runBlocking {
        val writePort = BlockingWritePort()
        val pins = ActiveStoryPins(writePort, CatalogMutationGate())
        pins.register(ref("one"))
        pins.register(ref("two"))

        val thrown = assertThrows(CatalogFailureException::class.java) { runBlocking { pins.register(ref("three")) } }

        assertEquals(CatalogFailure.InternalInvariant("active_story_pin_limit"), thrown.failure)
        assertEquals(setOf(ref("one").storyId, ref("two").storyId), pins.snapshot())
    }

    @Test
    fun releaseRemovesPinThenSuppliesOnlyRemainingProtectedStories() = runBlocking {
        val writePort = BlockingWritePort()
        val pins = ActiveStoryPins(writePort, CatalogMutationGate())
        val first = ref("one")
        val second = ref("two")
        pins.register(first)
        pins.register(second)

        pins.release(first, 44L)

        assertEquals(listOf(ReleaseCall(first, setOf(second.storyId), 44L)), writePort.releaseCalls)
        assertEquals(setOf(second.storyId), pins.snapshot())
    }

    @Test
    fun pinRegisteredBeforePublicationIsIncludedInProtectedSnapshot() = runBlocking {
        val writePort = BlockingWritePort()
        val pins = ActiveStoryPins(writePort, CatalogMutationGate())
        val pinned = ref("pinned")
        pins.register(pinned)
        val importer = importer(writePort, pins)

        importer.publishDiscover(
            CatalogSourceBinding(SOURCE_KEY, "v1"),
            app.openstory.catalog.domain.model.CatalogMediaType.MANGA,
            app.openstory.catalog.domain.source.DiscoverAcquisition(emptyList()),
            1L,
        )

        assertEquals(setOf(pinned.storyId), writePort.publishProtected.single())
    }

    @Test
    fun pinRegistrationWaitsWhenPublicationAlreadyOwnsMutationGate() = runBlocking {
        val writePort = BlockingWritePort(blockPublication = true)
        val pins = ActiveStoryPins(writePort, CatalogMutationGate())
        val importer = importer(writePort, pins)
        val publication = async {
            importer.publishDiscover(
                CatalogSourceBinding(SOURCE_KEY, "v1"),
                app.openstory.catalog.domain.model.CatalogMediaType.MANGA,
                app.openstory.catalog.domain.source.DiscoverAcquisition(emptyList()),
                1L,
            )
        }
        writePort.publicationEntered.await()

        val registration = async { pins.register(ref("selected")) }
        assertFalse(registration.isCompleted)
        writePort.continuePublication.complete(Unit)
        publication.await()
        registration.await()

        assertTrue(ref("selected").storyId in pins.snapshot())
    }

    @Test
    fun releaseWaitsForPublicationThenReclassifiesWithoutTheReleasedPin() = runBlocking {
        val writePort = BlockingWritePort(blockPublication = true)
        val pins = ActiveStoryPins(writePort, CatalogMutationGate())
        val selected = ref("selected")
        pins.register(selected)
        val importer = importer(writePort, pins)
        val publication = async {
            importer.publishDiscover(
                CatalogSourceBinding(SOURCE_KEY, "v1"),
                app.openstory.catalog.domain.model.CatalogMediaType.MANGA,
                app.openstory.catalog.domain.source.DiscoverAcquisition(emptyList()),
                1L,
            )
        }
        writePort.publicationEntered.await()

        val release = async { pins.release(selected, 2L) }
        assertFalse(release.isCompleted)
        writePort.continuePublication.complete(Unit)
        publication.await()
        release.await()

        assertEquals(setOf(selected.storyId), writePort.publishProtected.single())
        assertTrue(writePort.releaseCalls.single().protected.isEmpty())
        assertTrue(pins.snapshot().isEmpty())
    }

    private fun importer(writePort: CatalogWritePort, pins: ActiveStoryPins) = CatalogImporter(
        writePort = writePort,
        activeStoryPins = pins,
        dispatchers = CatalogExecutionDispatchers(Dispatchers.Default, Dispatchers.IO),
    )

    private fun ref(sourceStoryId: String) = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
        catalogSourceKey = SOURCE_KEY,
        sourceStoryId = sourceStoryId,
    )

    private data class ReleaseCall(
        val ref: StorySourceRef,
        val protected: Set<app.openstory.common.id.StoryId>,
        val releasedAtEpochMs: Long,
    )

    private class BlockingWritePort(
        private val blockPublication: Boolean = false,
    ) : CatalogWritePort {
        val publicationEntered = CompletableDeferred<Unit>()
        val continuePublication = CompletableDeferred<Unit>()
        val publishProtected = mutableListOf<Set<app.openstory.common.id.StoryId>>()
        val releaseCalls = mutableListOf<ReleaseCall>()

        override suspend fun publishDiscover(
            command: DiscoverPublicationCommand,
            retentionProtectedStoryIds: Set<app.openstory.common.id.StoryId>,
        ): CatalogMutationDiagnostics {
            publishProtected += retentionProtectedStoryIds
            publicationEntered.complete(Unit)
            if (blockPublication) continuePublication.await()
            return CatalogMutationDiagnostics(emptySet())
        }

        override suspend fun publishStoryDetail(command: StoryDetailPublicationCommand) = Unit

        override suspend fun touchStoryAccess(ref: StorySourceRef, accessedAtEpochMs: Long) = Unit

        override suspend fun releaseStoryDemand(
            ref: StorySourceRef,
            retentionProtectedStoryIds: Set<app.openstory.common.id.StoryId>,
            releasedAtEpochMs: Long,
        ): CatalogMutationDiagnostics {
            releaseCalls += ReleaseCall(ref, retentionProtectedStoryIds, releasedAtEpochMs)
            return CatalogMutationDiagnostics(setOf(ref.storyId))
        }
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
    }
}
