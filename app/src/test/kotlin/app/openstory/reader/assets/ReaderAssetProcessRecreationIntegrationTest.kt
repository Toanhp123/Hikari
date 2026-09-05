package app.openstory.reader.assets

import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderAssetProcessRecreationIntegrationTest {
    @Test
    fun `recreated Reader runtime reacquires semantics but reuses persistent image bytes`() = runBlocking {
        ReaderAssetPersistentTestFixture().use { fixture ->
            val firstRuntime = fixture.newRuntime(ReaderNetworkState.UNMETERED)
            val firstManifest = fixture.reacquireManifest(ReaderSessionId(101), "chapter-recreated", pageCount = 1)
            val firstRevision = firstRuntime.coordinator.registerCommitted(
                firstManifest.sessionId,
                1L,
                firstManifest,
            )
            val firstRequest = ReaderPageAssetRequest(
                firstManifest.sessionId,
                firstRevision,
                firstManifest.descriptors.single(),
            )
            assertIs<ReaderAssetLoadOutcome.Remote>(firstRuntime.coordinator.requestPage(firstRequest))
            fixture.awaitPersisted(firstRequest.descriptor.key)
            firstRuntime.close()

            fixture.reopenRoomAdapter()
            val recreatedRuntime = fixture.newRuntime(ReaderNetworkState.UNMETERED)
            val recreatedManifest = fixture.reacquireManifest(
                ReaderSessionId(202),
                "chapter-recreated",
                pageCount = 1,
            )
            val recreatedRevision = recreatedRuntime.coordinator.registerCommitted(
                recreatedManifest.sessionId,
                1L,
                recreatedManifest,
            )
            val local = assertIs<ReaderAssetLoadOutcome.Local>(
                recreatedRuntime.coordinator.requestPage(
                    ReaderPageAssetRequest(
                        recreatedManifest.sessionId,
                        recreatedRevision,
                        recreatedManifest.descriptors.single(),
                    ),
                ),
            )

            assertContentEquals(fixture.payloadBytes, local.readAndClose())
            assertEquals(2, fixture.semanticDocumentCalls)
            assertEquals(1, fixture.imageDeliveryCalls)
            assertEquals(
                1,
                recreatedRuntime.diagnostics.events.count { it == ReaderAssetDiagnosticEvent.DiskHit },
            )
        }
    }
}
