package app.openstory.catalog.runtime.acquisition

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.DiscoverAcquisitionSection
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.runtime.concurrency.CatalogMutationGate
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogImporterTest {
    @Test
    fun publishDiscoverBuildsHostOwnedProvenanceAndDeterministicCards() = runBlocking {
        val writePort = RecordingWritePort()
        val importer = importer(writePort)
        val acquisition = DiscoverAcquisition(
            sections = listOf(
                DiscoverAcquisitionSection(
                    CatalogSectionKind.TOP_RATED,
                    listOf(item("shared", "Top-rated title", rating = CatalogRating(9.0, 10.0))),
                ),
                DiscoverAcquisitionSection(
                    CatalogSectionKind.POPULAR,
                    listOf(
                        item(
                            "shared",
                            "Popular title",
                            cover = AcquisitionCoverInput.RemoteHttps(
                                "HTTPS://EXAMPLE.COM/covers/one.jpg",
                                reviewedStableArtworkToken = "cover-v1",
                            ),
                        ),
                        item("popular-two", "Second"),
                    ),
                ),
            ),
        )

        importer.publishDiscover(BINDING, CatalogMediaType.MANGA, acquisition, 123L)

        val command = requireNotNull(writePort.discoverCommands.singleOrNull())
        assertEquals(SOURCE_KEY, command.provenance.catalogSourceKey)
        assertEquals("fixture-v7", command.provenance.sourceVersion)
        assertEquals(123L, command.provenance.acquiredAtEpochMs)
        assertEquals(
            listOf(CatalogSectionKind.POPULAR, CatalogSectionKind.POPULAR, CatalogSectionKind.TOP_RATED),
            command.cards.map { it.sectionKind },
        )
        assertEquals(listOf(0, 1, 0), command.cards.map { it.itemPosition })
        assertEquals("Popular title", command.cards.first().title)
        val remote = command.cards.first().coverLocator as CoverLocator.RemoteHttps
        assertEquals(SOURCE_KEY, remote.catalogSourceKey)
        assertEquals("https://example.com/covers/one.jpg", remote.normalizedUri.value)
        assertEquals(command.cards.first().ref.storyId, command.cards.first().coverAssetKey?.storyId)
    }

    @Test
    fun publishDiscoverPersistsAValidZeroCardPublication() = runBlocking {
        val writePort = RecordingWritePort()
        val importer = importer(writePort)

        importer.publishDiscover(
            BINDING,
            CatalogMediaType.LIGHT_NOVEL,
            DiscoverAcquisition(emptyList()),
            200L,
        )

        assertTrue(writePort.discoverCommands.single().cards.isEmpty())
        assertEquals(CatalogMediaType.LIGHT_NOVEL, writePort.discoverCommands.single().mediaType)
    }

    @Test
    fun identityCollisionPropagatesUnchangedWithoutRetryOrRepair() = runBlocking {
        val collision = CatalogFailure.IdentityCollision("source-story:v1:${"a".repeat(64)}")
        val writePort = RecordingWritePort(publishFailure = CatalogFailureException(collision))
        val importer = importer(writePort)

        val thrown = assertCatalogFailure {
            importer.publishDiscover(
                BINDING,
                CatalogMediaType.MANGA,
                DiscoverAcquisition(
                    listOf(DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, listOf(item("one", "One")))),
                ),
                10L,
            )
        }

        assertEquals(collision, thrown.failure)
        assertEquals(1, writePort.publishAttempts)
        assertEquals("one", writePort.discoverCommands.single().cards.single().ref.sourceStoryId)
    }

    @Test
    fun detailRejectsBindingMismatchBeforeStorageMutation() = runBlocking {
        val writePort = RecordingWritePort()
        val importer = importer(writePort)
        val otherSource = CatalogSourceKey("other.source")
        val ref = ref(otherSource, "one")

        val thrown = assertCatalogFailure {
            importer.upsertStoryDetail(BINDING, ref, detail("one"), 10L)
        }

        assertEquals(
            CatalogFailure.Validation("ref.catalogSourceKey", CatalogValidationReason.AUTHORITY_MISMATCH),
            thrown.failure,
        )
        assertEquals(0, writePort.detailCommands.size)
    }

    @Test
    fun detailBuildsHostOwnedAtomicPublicationCommand() = runBlocking {
        val writePort = RecordingWritePort()
        val importer = importer(writePort)
        val ref = ref(SOURCE_KEY, "one")

        importer.upsertStoryDetail(BINDING, ref, detail("one"), 77L)

        val command = writePort.detailCommands.single()
        assertEquals(ref, command.ref)
        assertEquals("fixture-v7", command.provenance.sourceVersion)
        assertEquals(77L, command.provenance.acquiredAtEpochMs)
        assertEquals("Detail", command.summary.title)
        assertEquals("Description", command.detail.description)
        assertEquals(listOf("Author"), command.detail.authors)
    }

    @Test
    fun importerDispatchesValidationProjectionAndHashWorkToInjectedCpuDispatcher() = runBlocking {
        val writePort = RecordingWritePort()
        val cpu = CountingDispatcher(Dispatchers.Default)
        val importer = importer(writePort, cpu)

        importer.publishDiscover(
            BINDING,
            CatalogMediaType.MANGA,
            DiscoverAcquisition(
                listOf(DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, listOf(item("one", "One")))),
            ),
            10L,
        )

        assertTrue(cpu.dispatches.get() > 0)
    }

    private fun importer(
        writePort: CatalogWritePort,
        cpu: CoroutineDispatcher = Dispatchers.Default,
    ): CatalogImporter {
        val gate = CatalogMutationGate()
        val pins = ActiveStoryPins(writePort, gate)
        return CatalogImporter(
            writePort = writePort,
            activeStoryPins = pins,
            dispatchers = CatalogExecutionDispatchers(cpu = cpu, io = Dispatchers.IO),
        )
    }

    private fun item(
        sourceStoryId: String,
        title: String,
        cover: AcquisitionCoverInput? = null,
        rating: CatalogRating? = null,
    ) = DiscoverAcquisitionItem(
        sourceStoryId = sourceStoryId,
        title = title,
        contentType = CatalogMediaType.MANGA,
        cover = cover,
        rating = rating,
        publicationStatusSummary = null,
        latestUpdateEpochMs = 1L,
    )

    private fun detail(sourceStoryId: String) = StoryDetailAcquisition(
        sourceStoryId = sourceStoryId,
        title = "Detail",
        contentType = CatalogMediaType.MANGA,
        cover = null,
        rating = null,
        publicationStatusSummary = null,
        latestUpdateEpochMs = null,
        description = "Description",
        authors = listOf("Author"),
        artists = listOf("Artist"),
        genres = listOf("Genre"),
        publicationStatus = "Ongoing",
        language = "English",
    )

    private fun ref(source: CatalogSourceKey, sourceStoryId: String) = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(source, sourceStoryId)),
        catalogSourceKey = source,
        sourceStoryId = sourceStoryId,
    )

    private suspend fun assertCatalogFailure(block: suspend () -> Unit): CatalogFailureException = try {
        block()
        throw AssertionError("Expected CatalogFailureException")
    } catch (error: CatalogFailureException) {
        error
    }

    private class CountingDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        val dispatches = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }

    private class RecordingWritePort(
        private val publishFailure: CatalogFailureException? = null,
    ) : CatalogWritePort {
        val discoverCommands = mutableListOf<DiscoverPublicationCommand>()
        val detailCommands = mutableListOf<StoryDetailPublicationCommand>()
        var publishAttempts = 0

        override suspend fun publishDiscover(
            command: DiscoverPublicationCommand,
            retentionProtectedStoryIds: Set<app.openstory.common.id.StoryId>,
        ): CatalogMutationDiagnostics {
            publishAttempts += 1
            discoverCommands += command
            publishFailure?.let { throw it }
            return CatalogMutationDiagnostics(command.cards.mapTo(linkedSetOf()) { it.ref.storyId })
        }

        override suspend fun publishStoryDetail(command: StoryDetailPublicationCommand) {
            detailCommands += command
        }

        override suspend fun touchStoryAccess(ref: StorySourceRef, accessedAtEpochMs: Long) = Unit

        override suspend fun releaseStoryDemand(
            ref: StorySourceRef,
            retentionProtectedStoryIds: Set<app.openstory.common.id.StoryId>,
            releasedAtEpochMs: Long,
        ) = CatalogMutationDiagnostics(setOf(ref.storyId))
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
        val BINDING = CatalogSourceBinding(SOURCE_KEY, "fixture-v7")
    }
}
