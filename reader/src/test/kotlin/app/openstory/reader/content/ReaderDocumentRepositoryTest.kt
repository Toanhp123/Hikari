package app.openstory.reader.content

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderDocumentRepositoryTest {
    @Test
    fun returnsMatchingStoreEntryBeforeNetwork() = runTest {
        val document = document("fingerprint")
        val store = FakeStore(readResult = document)
        val source = FakeSource()
        val result = repository(store, source).load(
            request(candidate("release"), mapOf(ChapterReleaseId("release") to "fingerprint")),
        )

        assertEquals(true, assertIs<ReaderLoadResult.Success>(result).fromStore)
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun cacheHitSkipsSourceRegistryEnumeration() = runTest {
        val document = document("fingerprint")
        val store = FakeStore(readResult = document)
        var enabledCalls = 0
        val repository = ReaderDocumentRepository(
            store,
            object : ReaderDocumentSourceRegistry {
                override suspend fun enabled(): List<ReaderDocumentSource> {
                    enabledCalls += 1
                    return listOf(FakeSource())
                }
            },
            ReleaseSelector(),
        )

        val result = repository.load(
            request(candidate("release"), mapOf(ChapterReleaseId("release") to "fingerprint")),
        )

        assertEquals(true, assertIs<ReaderLoadResult.Success>(result).fromStore)
        assertEquals(0, enabledCalls)
    }

    @Test
    fun returnsCurrentStoreEntryWithoutProgressFingerprint() = runTest {
        val document = document("downloaded")
        val store = FakeStore(currentReadResult = document)
        val source = FakeSource()

        val result = repository(store, source).load(request(candidate("release")))

        assertEquals(true, assertIs<ReaderLoadResult.Success>(result).fromStore)
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun writesSanitizedNetworkResultAndFallsBackToAlternate() = runTest {
        val store = FakeStore()
        val source = FakeSource(
            results = mutableMapOf(
                "first" to ReaderSourceResult.Failure("failed", true),
                "second" to ReaderSourceResult.Success(document("network")),
            ),
        )
        val result = repository(store, source).load(
            request(candidate("first", 20), candidate("second", 10)),
        )

        assertEquals("second", assertIs<ReaderLoadResult.Success>(result).release.release.id.value)
        assertEquals(listOf("second" to "network"), store.writes)
    }

    @Test
    fun quarantinesMismatchedStoreEntryAndPreservesCancellation() = runTest {
        val store = FakeStore(readResult = document("wrong"))
        val source = FakeSource(cancel = true)
        val cancellation = try {
            repository(store, source).load(
                request(candidate("release"), mapOf(ChapterReleaseId("release") to "expected")),
            )
            null
        } catch (caught: CancellationException) {
            caught
        }

        assertIs<CancellationException>(cancellation)
        assertEquals(listOf("release" to "expected"), store.quarantines)
    }

    @Test
    fun reportsEveryAlternateFailureInDeterministicSelectionOrder() = runTest {
        val source = FakeSource(
            mutableMapOf(
                "newer" to ReaderSourceResult.Failure("reader.newer_failed", true),
                "older" to ReaderSourceResult.Failure("reader.older_failed", false),
            ),
        )

        val failure = assertIs<ReaderLoadResult.Failure>(
            repository(FakeStore(), source).load(
                request(candidate("older", 1), candidate("newer", 2)),
            ),
        )

        assertEquals(listOf("newer", "older"), failure.attempts.map { it.releaseId.value })
        assertEquals(listOf(true, false), failure.attempts.map(ReaderLoadFailure::retryable))
    }

    private fun repository(store: FakeStore, source: FakeSource) = ReaderDocumentRepository(
        store,
        object : ReaderDocumentSourceRegistry {
            override suspend fun enabled(): List<ReaderDocumentSource> = listOf(source)
        },
        ReleaseSelector(),
    )

    private fun request(
        vararg candidates: ReleaseCandidate,
        fingerprints: Map<ChapterReleaseId, String> = emptyMap(),
    ) = ReaderLoadRequest(candidates.toList(), expectedFingerprints = fingerprints)

    private fun request(
        candidate: ReleaseCandidate,
        fingerprints: Map<ChapterReleaseId, String>,
    ) = ReaderLoadRequest(listOf(candidate), expectedFingerprints = fingerprints)

    private fun candidate(id: String, updatedAt: Long = 1) = ReleaseCandidate(
        ChapterRelease(
            ChapterReleaseId(id), StoryId("story"), PluginId("plugin"), "source-story", "source-$id", id,
            ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null), "en", updatedAt, null,
        ),
    )

    private fun document(fingerprint: String) = ReaderDocument(
        null,
        listOf(ReaderBlock.Paragraph("block", "text")),
        fingerprint,
    )
}

private class FakeStore(
    private val readResult: ReaderDocument? = null,
    private val currentReadResult: ReaderDocument? = null,
) : ReaderDocumentStore {
    val writes = mutableListOf<Pair<String, String>>()
    val quarantines = mutableListOf<Pair<String, String>>()

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String) = readResult
    override suspend fun readCurrent(releaseId: ChapterReleaseId) = currentReadResult

    override suspend fun write(
        releaseId: ChapterReleaseId,
        fingerprint: String,
        document: ReaderDocument,
    ) {
        writes += releaseId.value to fingerprint
    }

    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) {
        quarantines += releaseId.value to fingerprint
    }
}

private class FakeSource(
    private val results: MutableMap<String, ReaderSourceResult> = mutableMapOf(),
    private val cancel: Boolean = false,
) : ReaderDocumentSource {
    override val pluginId = PluginId("plugin")
    var fetchCount = 0

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetchCount += 1
        if (cancel) throw CancellationException()
        return results[release.id.value] ?: ReaderSourceResult.Failure("missing", false)
    }
}
