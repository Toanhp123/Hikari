package app.openstory.reader.assets

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.FakeClock
import app.openstory.common.FakeMonotonicClock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.assets.DownloadReaderAssetStore
import app.openstory.downloads.assets.ReaderAssetBlobIdFactory
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import app.openstory.storage.files.AtomicFileReaderAssetBlobStore
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.readerassets.RoomReaderAssetMetadataRepository
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderAssetIntegrationTest {
    @Test
    fun `same chapter and offline revisit read retained pages from RICC disk`() = runBlocking {
        ReaderAssetPersistentTestFixture().use { fixture ->
            val runtime = fixture.newRuntime(ReaderNetworkState.UNMETERED)
            val manifest = fixture.reacquireManifest(ReaderSessionId(1), "chapter-a", pageCount = 10)
            val revision = runtime.coordinator.registerCommitted(manifest.sessionId, 1L, manifest)

            manifest.descriptors.forEach { descriptor ->
                val request = ReaderPageAssetRequest(manifest.sessionId, revision, descriptor)
                assertIs<ReaderAssetLoadOutcome.Remote>(runtime.coordinator.requestPage(request))
                fixture.awaitPersisted(descriptor.key)
            }

            runtime.networkState = ReaderNetworkState.OFFLINE
            val firstPage = ReaderPageAssetRequest(manifest.sessionId, revision, manifest.descriptors.first())
            assertTrue(runtime.coordinator.updateViewport(viewport(firstPage, ordinal = 0)))
            assertTrue(runtime.coordinator.assetPresented(firstPage))
            fixture.awaitConsumed(firstPage.descriptor.key)
            assertTrue(runtime.coordinator.updateViewport(viewport(firstPage, ordinal = 9)))
            assertTrue(runtime.coordinator.updateViewport(viewport(firstPage, ordinal = 0)))

            val imageCallsBeforeRevisit = fixture.imageDeliveryCalls
            val local = assertIs<ReaderAssetLoadOutcome.Local>(runtime.coordinator.requestPage(firstPage))

            assertContentEquals(fixture.payloadBytes, local.readAndClose())
            assertEquals(imageCallsBeforeRevisit, fixture.imageDeliveryCalls)
            assertTrue(runtime.diagnostics.events.any { it == ReaderAssetDiagnosticEvent.DiskHit })
        }
    }

    @Test
    fun `returning through warm chapter history reuses retained A bytes`() = runBlocking {
        ReaderAssetPersistentTestFixture().use { fixture ->
            val runtime = fixture.newRuntime(ReaderNetworkState.UNMETERED)
            val sessionId = ReaderSessionId(2)
            val chapterA = fixture.reacquireManifest(sessionId, "chapter-a", pageCount = 1)
            var revision = runtime.coordinator.registerCommitted(sessionId, 1L, chapterA)
            val firstA = ReaderPageAssetRequest(sessionId, revision, chapterA.descriptors.single())
            assertIs<ReaderAssetLoadOutcome.Remote>(runtime.coordinator.requestPage(firstA))
            fixture.awaitPersisted(firstA.descriptor.key)
            assertTrue(runtime.coordinator.updateViewport(viewport(firstA, ordinal = 0)))
            assertTrue(runtime.coordinator.assetPresented(firstA))
            fixture.awaitConsumed(firstA.descriptor.key)

            listOf("chapter-b", "chapter-c", "chapter-d").forEach { chapter ->
                revision = runtime.coordinator.registerCommitted(
                    sessionId,
                    revision + 1L,
                    fixture.reacquireManifest(sessionId, chapter, pageCount = 1),
                )
            }
            val returnedA = fixture.reacquireManifest(sessionId, "chapter-a", pageCount = 1)
            revision = runtime.coordinator.registerCommitted(sessionId, revision + 1L, returnedA)

            val imageCallsBeforeReturn = fixture.imageDeliveryCalls
            val local = assertIs<ReaderAssetLoadOutcome.Local>(
                runtime.coordinator.requestPage(
                    ReaderPageAssetRequest(sessionId, revision, returnedA.descriptors.single()),
                ),
            )

            assertContentEquals(fixture.payloadBytes, local.readAndClose())
            assertEquals(imageCallsBeforeReturn, fixture.imageDeliveryCalls)
        }
    }

    @Test
    fun `fail closed source remains transient across runtime replacement`() = runBlocking {
        ReaderAssetPersistentTestFixture().use { fixture ->
            val firstRuntime = fixture.newRuntime(ReaderNetworkState.UNMETERED)
            val firstManifest = fixture.reacquireManifest(
                ReaderSessionId(3),
                "private-chapter",
                pageCount = 1,
                policy = ReaderImageSourcePolicy.FAIL_CLOSED,
            )
            val firstRevision = firstRuntime.coordinator.registerCommitted(firstManifest.sessionId, 1L, firstManifest)
            assertIs<ReaderAssetLoadOutcome.Remote>(
                firstRuntime.coordinator.requestPage(
                    ReaderPageAssetRequest(
                        firstManifest.sessionId,
                        firstRevision,
                        firstManifest.descriptors.single(),
                    ),
                ),
            )
            firstRuntime.close()

            val secondRuntime = fixture.newRuntime(ReaderNetworkState.UNMETERED)
            val secondManifest = fixture.reacquireManifest(
                ReaderSessionId(4),
                "private-chapter",
                pageCount = 1,
                policy = ReaderImageSourcePolicy.FAIL_CLOSED,
            )
            val secondRevision = secondRuntime.coordinator.registerCommitted(
                secondManifest.sessionId,
                1L,
                secondManifest,
            )
            assertIs<ReaderAssetLoadOutcome.Remote>(
                secondRuntime.coordinator.requestPage(
                    ReaderPageAssetRequest(
                        secondManifest.sessionId,
                        secondRevision,
                        secondManifest.descriptors.single(),
                    ),
                ),
            )

            assertEquals(2, fixture.imageDeliveryCalls)
            assertTrue(fixture.metadataRepository.all().isEmpty())
        }
    }
}

internal class ReaderAssetPersistentTestFixture : AutoCloseable {
    private val applicationContext = RuntimeEnvironment.getApplication() as Context
    private val fixtureId = UUID.randomUUID().toString()
    private val fileRoot = File(applicationContext.cacheDir, "reader-asset-integration-$fixtureId")
    private val fileContext = object : ContextWrapper(applicationContext) {
        override fun getFilesDir(): File = fileRoot
    }
    private var database = openDatabase()
    internal var metadataRepository = RoomReaderAssetMetadataRepository(database)
        private set
    private var blobStore = AtomicFileReaderAssetBlobStore(fileContext)

    val payloadBytes = byteArrayOf(1, 3, 3, 7)
    var imageDeliveryCalls: Int = 0
        private set
    var semanticDocumentCalls: Int = 0
        private set

    fun newRuntime(networkState: ReaderNetworkState): RuntimeHandle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val diagnostics = RecordingReaderAssetDiagnostics()
        val budget = AutomaticCacheBudgetCoordinator(
            cacheRepository = EmptyCacheRepository(),
            documentBlobStore = EmptyChapterBlobStore,
            readerAssetMetadataRepository = metadataRepository,
            readerAssetBlobStore = blobStore,
            initialQuotaBytes = 64L * 1024 * 1024,
            reconciliationScope = scope,
            diagnostics = diagnostics,
        )
        val store = DownloadReaderAssetStore(
            metadataRepository = metadataRepository,
            blobStore = blobStore,
            blobIdFactory = ReaderAssetBlobIdFactory(),
            budget = budget,
            clock = FakeClock(1_000L),
            monotonicClock = FakeMonotonicClock(0L),
            diagnostics = diagnostics,
        )
        val runtime = RuntimeHandle(scope, networkState, diagnostics)
        val loader = ReaderAssetLoader(
            store = store,
            delivery = ReaderAssetDeliveryPort {
                imageDeliveryCalls += 1
                ReaderAssetDeliveryResult.Success(
                    ReaderAssetPayload.verifiedBounded(payloadBytes, "image/png", null),
                )
            },
            singleFlight = ReaderAssetSingleFlight(scope, diagnostics),
            fetchArbiter = ContentFetchArbiter(),
            persistenceScope = scope,
            diagnostics = diagnostics,
        )
        runtime.coordinator = ReaderAssetCoordinator(
            store = store,
            networkFacts = ReaderNetworkFactsPort { runtime.networkState },
            coordinatorScope = scope,
            loader = loader,
            diagnostics = diagnostics,
        )
        return runtime
    }

    fun reacquireManifest(
        sessionId: ReaderSessionId,
        chapter: String,
        pageCount: Int,
        policy: ReaderImageSourcePolicy = TRUSTED_PUBLIC_POLICY,
    ): ReaderAssetChapterManifest {
        semanticDocumentCalls += 1
        val storyId = StoryId("story")
        val chapterId = CanonicalChapterId(chapter)
        val pluginId = PluginId("org.example.reader")
        val release = ChapterRelease(
            id = ChapterReleaseId("release:$chapter"),
            storyId = storyId,
            pluginId = pluginId,
            sourceStoryId = "source-story",
            sourceReleaseId = "source-$chapter",
            displayLabel = chapter,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = chapterId,
        )
        val document = ReaderDocument(
            title = chapter,
            blocks = List(pageCount) { ordinal ->
                ReaderBlock.ImagePage(
                    id = "image-$ordinal",
                    stableAssetId = "$chapter/page-$ordinal.png",
                    imageUrl = "https://cdn.example.test/$chapter/page-$ordinal.png",
                )
            },
            fingerprint = "document:$chapter",
        )
        return requireNotNull(
            ReaderAssetManifestFactory().create(
                sessionId = sessionId,
                storyId = storyId,
                canonicalChapterId = chapterId,
                selectedRelease = release,
                graphRevision = ReaderAssetGraphRevision(1L),
                document = document,
                imageSourcePolicy = policy,
                sourcePluginId = pluginId,
            ),
        )
    }

    suspend fun awaitPersisted(key: ReaderPageAssetKey) {
        withTimeout(5_000L) {
            while (metadataRepository.find(setOf(key.hash))[key.hash] == null) delay(10L)
        }
    }

    suspend fun awaitConsumed(key: ReaderPageAssetKey) {
        withTimeout(5_000L) {
            while (metadataRepository.find(setOf(key.hash))[key.hash]?.lastConsumedAtEpochMillis == null) delay(10L)
        }
    }

    fun reopenRoomAdapter() {
        metadataRepository = RoomReaderAssetMetadataRepository(database)
        blobStore = AtomicFileReaderAssetBlobStore(fileContext)
    }

    override fun close() {
        database.close()
        fileRoot.deleteRecursively()
    }

    private fun openDatabase(): OpenStoryDatabase = Room.inMemoryDatabaseBuilder(
        applicationContext,
        OpenStoryDatabase::class.java,
    ).build()

    internal class RuntimeHandle(
        private val scope: CoroutineScope,
        var networkState: ReaderNetworkState,
        val diagnostics: RecordingReaderAssetDiagnostics,
    ) : AutoCloseable {
        lateinit var coordinator: ReaderAssetCoordinator

        override fun close() {
            scope.cancel()
        }
    }

    internal class RecordingReaderAssetDiagnostics : ReaderAssetDiagnosticsSink {
        val events = mutableListOf<ReaderAssetDiagnosticEvent>()

        override fun record(event: ReaderAssetDiagnosticEvent) {
            synchronized(events) { events += event }
        }
    }

    private companion object {
        val TRUSTED_PUBLIC_POLICY = ReaderImageSourcePolicy(
            identityContract = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
            persistenceContract = ReaderImagePersistenceContract.PUBLIC,
        )
    }
}

private class EmptyCacheRepository : CacheRepository {
    override suspend fun entries(): List<CacheEntry> = emptyList()
    override suspend fun upsert(entry: CacheEntry) = Unit
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> = emptyList()
}

private object EmptyChapterBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}

private fun viewport(request: ReaderPageAssetRequest, ordinal: Int) = ReaderViewportSnapshot(
    sessionId = request.sessionId,
    manifestRevision = request.manifestRevision,
    leadingVisibleImageOrdinal = ordinal,
    trailingVisibleImageOrdinal = ordinal,
    direction = ReaderViewportDirection.IDLE,
    chapterProgressBasisPoints = ordinal * 1_000,
)

internal fun ReaderAssetLoadOutcome.Local.readAndClose(): ByteArray = try {
    lease.openStream().readBytes()
} finally {
    lease.close()
}
