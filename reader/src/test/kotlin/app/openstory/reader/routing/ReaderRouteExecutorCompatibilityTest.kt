package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.penalizesSourceHealth
import app.openstory.reader.selection.ReleaseCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderRouteExecutorCompatibilityTest {
    @Test
    fun selectedCachedCandidateWinsBeforeSourceEnumeration() = runTest {
        val store = RecordingStore(exact = document("expected"))
        val registry = RecordingRegistry(listOf(RecordingSource()))

        val observations = mutableListOf<SourceObservation>()
        val result = executor(store, registry).executeCompatibility(
            orderedCandidates = listOf(candidate("selected"), candidate("alternate")),
            expectedFingerprints = mapOf(ChapterReleaseId("selected") to "expected"),
            onSourceObservation = { _, observation -> observations += observation },
        )

        assertEquals("selected", assertIs<ReaderLoadResult.Success>(result).release.release.id.value)
        assertEquals(true, result.fromStore)
        assertEquals(listOf<SourceObservation>(SourceObservation.Success.Local), observations)
        assertEquals(0, registry.enabledCalls)
    }

    @Test
    fun currentExplicitDownloadIsUsedWhenNoRequestedFingerprintExists() = runTest {
        val store = RecordingStore(current = document("download"))
        val registry = RecordingRegistry(emptyList())

        val result = executor(store, registry).executeCompatibility(listOf(candidate("release")), emptyMap())

        assertEquals(true, assertIs<ReaderLoadResult.Success>(result).fromStore)
        assertEquals(listOf("release"), store.currentReads)
        assertEquals(emptyList(), store.exactReads)
    }

    @Test
    fun requestedFingerprintIsReadExactlyWhenSupplied() = runTest {
        val store = RecordingStore(exact = document("fingerprint"))

        executor(store, RecordingRegistry(emptyList())).executeCompatibility(
            listOf(candidate("release")),
            mapOf(ChapterReleaseId("release") to "fingerprint"),
        )

        assertEquals(listOf("release" to "fingerprint"), store.exactReads)
        assertEquals(emptyList(), store.currentReads)
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        val source = RecordingSource(cancel = true)
        val caught = try {
            executor(RecordingStore(), RecordingRegistry(listOf(source)))
                .executeCompatibility(listOf(candidate("release")), emptyMap())
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertIs<CancellationException>(caught)
    }

    @Test
    fun sourcesAreEnumeratedLazilyOnceAfterCacheMiss() = runTest {
        val source = RecordingSource(
            results = mutableMapOf(
                "first" to ReaderSourceResult.Failure("first.failed", true),
                "second" to ReaderSourceResult.Failure("second.failed", false),
            ),
        )
        val registry = RecordingRegistry(listOf(source))

        executor(RecordingStore(), registry).executeCompatibility(
            listOf(candidate("first"), candidate("second")),
            emptyMap(),
        )

        assertEquals(1, registry.enabledCalls)
        assertEquals(listOf("first", "second"), source.fetches)
    }

    @Test
    fun validPersistableRemoteDocumentIsWritten() = runTest {
        val store = RecordingStore()
        val source = RecordingSource(
            results = mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )

        val result = executor(store, RecordingRegistry(listOf(source)))
            .executeCompatibility(listOf(candidate("release")), emptyMap())

        assertEquals(false, assertIs<ReaderLoadResult.Success>(result).fromStore)
        assertEquals(listOf("release" to "remote"), store.writes)
    }

    @Test
    fun imagePageRemoteDocumentIsNotStored() = runTest {
        val store = RecordingStore()
        val image = ReaderDocument(
            title = null,
            blocks = listOf(ReaderBlock.ImagePage("image", "https://node.example/image.png")),
            fingerprint = "remote-image",
        )
        val source = RecordingSource(
            results = mutableMapOf("release" to ReaderSourceResult.Success(image)),
        )

        executor(store, RecordingRegistry(listOf(source)))
            .executeCompatibility(listOf(candidate("release")), emptyMap())

        assertEquals(emptyList(), store.writes)
    }

    @Test
    fun fallbackOrderAndLegacyFailureSurfaceRemainUnchanged() = runTest {
        val source = RecordingSource(
            results = mutableMapOf(
                "newer" to ReaderSourceResult.Failure("reader.newer_failed", true),
                "older" to ReaderSourceResult.Failure("reader.older_failed", false),
            ),
        )

        val failure = assertIs<ReaderLoadResult.Failure>(
            executor(RecordingStore(), RecordingRegistry(listOf(source))).executeCompatibility(
                listOf(candidate("newer"), candidate("older")),
                emptyMap(),
            ),
        )

        assertEquals(listOf("newer", "older"), failure.attempts.map { it.releaseId.value })
        assertEquals(listOf("reader.newer_failed", "reader.older_failed"), failure.attempts.map { it.code })
        assertEquals(listOf(true, false), failure.attempts.map { it.retryable })
    }


    @Test
    fun exactCorruptionNotifiesInvalidLocatorAndRemoteProbeObservationKeepsOrigin() = runTest {
        val store = RecordingStore(exact = document("wrong"))
        val source = RecordingSource(
            results = mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val invalidated = mutableListOf<Pair<String, String>>()
        val observations = mutableListOf<SourceObservation>()

        val result = executor(store, RecordingRegistry(listOf(source))).executeCompatibility(
            orderedCandidates = listOf(candidate("release")),
            expectedFingerprints = mapOf(ChapterReleaseId("release") to "expected"),
            remoteAttemptKinds = mapOf(ChapterReleaseId("release") to RemoteAttemptKind.HALF_OPEN_PROBE),
            onSourceObservation = { _, observation -> observations += observation },
            onLocalInvalidated = { releaseId, fingerprint -> invalidated += releaseId.value to fingerprint },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertEquals(listOf("release" to "expected"), store.quarantines)
        assertEquals(listOf("release" to "expected"), invalidated)
        assertIs<SourceObservation.LocalFailure.FingerprintOrDecodeMismatch>(observations[0])
        assertEquals(false, observations[0].penalizesSourceHealth)
        assertEquals(
            RemoteAttemptKind.HALF_OPEN_PROBE,
            assertIs<SourceObservation.Success.Remote>(observations[1]).kind,
        )
    }

    @Test
    fun missingExactLocalBlobEmitsTypedNonPenalizingMissBeforeRemoteRecovery() = runTest {
        val source = RecordingSource(
            results = mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val observations = mutableListOf<SourceObservation>()

        val result = executor(RecordingStore(), RecordingRegistry(listOf(source))).executeCompatibility(
            orderedCandidates = listOf(candidate("release")),
            expectedFingerprints = mapOf(ChapterReleaseId("release") to "expected"),
            onSourceObservation = { _, observation -> observations += observation },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertIs<SourceObservation.LocalFailure.MissingBlob>(observations[0])
        assertEquals(false, observations[0].penalizesSourceHealth)
        assertIs<SourceObservation.Success.Remote>(observations[1])
    }

    @Test
    fun localStorageIoFailureEmitsTypedClientObservationWithoutCorruptionClaim() = runTest {
        val store = RecordingStore(readFailure = IllegalStateException("disk unavailable"))
        val source = RecordingSource(
            results = mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val observations = mutableListOf<SourceObservation>()

        val result = executor(store, RecordingRegistry(listOf(source))).executeCompatibility(
            orderedCandidates = listOf(candidate("release")),
            expectedFingerprints = mapOf(ChapterReleaseId("release") to "expected"),
            onSourceObservation = { _, observation -> observations += observation },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertIs<SourceObservation.RuntimeFailure.Unexpected>(observations[0])
        assertEquals(false, observations[0].penalizesSourceHealth)
        assertEquals(emptyList(), store.quarantines)
        assertIs<SourceObservation.Success.Remote>(observations[1])
    }

    private fun executor(store: ReaderDocumentStore, registry: ReaderDocumentSourceRegistry) =
        ReaderRouteExecutor(store, registry)

    private fun candidate(id: String) = ReleaseCandidate(
        ChapterRelease(
            id = ChapterReleaseId(id),
            storyId = StoryId("story"),
            pluginId = PluginId("plugin"),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-$id",
            displayLabel = id,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = null,
        ),
    )

    private fun document(fingerprint: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", "text")),
        fingerprint = fingerprint,
    )
}

private class RecordingStore(
    private val exact: ReaderDocument? = null,
    private val current: ReaderDocument? = null,
    private val readFailure: Throwable? = null,
) : ReaderDocumentStore {
    val exactReads = mutableListOf<Pair<String, String>>()
    val currentReads = mutableListOf<String>()
    val writes = mutableListOf<Pair<String, String>>()
    val quarantines = mutableListOf<Pair<String, String>>()

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? {
        exactReads += releaseId.value to fingerprint
        readFailure?.let { throw it }
        return exact
    }

    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? {
        currentReads += releaseId.value
        readFailure?.let { throw it }
        return current
    }

    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) {
        writes += releaseId.value to fingerprint
    }

    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) {
        quarantines += releaseId.value to fingerprint
    }
}

private class RecordingRegistry(
    private val sources: List<ReaderDocumentSource>,
) : ReaderDocumentSourceRegistry {
    var enabledCalls: Int = 0

    override suspend fun enabled(): List<ReaderDocumentSource> {
        enabledCalls += 1
        return sources
    }
}

private class RecordingSource(
    private val results: MutableMap<String, ReaderSourceResult> = mutableMapOf(),
    private val cancel: Boolean = false,
) : ReaderDocumentSource {
    override val pluginId = PluginId("plugin")
    val fetches = mutableListOf<String>()

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetches += release.id.value
        if (cancel) throw CancellationException()
        return results[release.id.value] ?: ReaderSourceResult.Failure("reader.source_failed", true)
    }
}
