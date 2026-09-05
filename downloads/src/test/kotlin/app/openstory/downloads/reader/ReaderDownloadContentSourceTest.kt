package app.openstory.downloads.reader

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterReleaseLookup
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadFetchResult
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.assets.ContentFetchArbiter
import app.openstory.reader.assets.ContentFetchPriority
import app.openstory.reader.routing.ContentSourceExecutionLane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderDownloadContentSourceTest {
    @Test
    fun onlineOnlyReaderSourceIsRejectedBeforeFetch() = runTest {
        val source = FakeReaderSource(textDocument())
        val downloads = ReaderDownloadContentSource(
            ChapterReleaseLookup { release() },
            registry(source),
            availability(readable = setOf(PLUGIN_ID), offline = emptySet()),
            ContentSourceExecutionLane(),
            ContentFetchArbiter(),
        )

        val failure = assertIs<DownloadFetchResult.Failure>(downloads.fetch(RELEASE_ID))

        assertEquals("download.content_online_only", failure.code)
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun remoteImageDocumentCannotEnterExplicitDownloadBlob() = runTest {
        val source = FakeReaderSource(
            ReaderDocument(
                null,
                listOf(ReaderBlock.ImagePage("page", "hash/page.png", "https://node.example/page.png")),
                "image-fingerprint",
            ),
        )
        val downloads = ReaderDownloadContentSource(
            ChapterReleaseLookup { release() },
            registry(source),
            availability(readable = setOf(PLUGIN_ID), offline = setOf(PLUGIN_ID)),
            ContentSourceExecutionLane(),
            ContentFetchArbiter(),
        )

        val failure = assertIs<DownloadFetchResult.Failure>(downloads.fetch(RELEASE_ID))

        assertEquals("download.content_online_only", failure.code)
        assertEquals(1, source.fetchCount)
    }

    @Test
    fun explicitDownloadWaitsForSharedUserWorkAdmission() = runTest {
        val source = FakeReaderSource(textDocument())
        val arbiter = ContentFetchArbiter(maxTotal = 1, reservedCriticalInteractive = 0)
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        val holder = launch {
            arbiter.withAdmission(ContentFetchPriority.CRITICAL) {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }
        holderEntered.await()
        val downloads = ReaderDownloadContentSource(
            ChapterReleaseLookup { release() },
            registry(source),
            availability(readable = setOf(PLUGIN_ID), offline = setOf(PLUGIN_ID)),
            ContentSourceExecutionLane(),
            arbiter,
        )

        val fetch = launch { downloads.fetch(RELEASE_ID) }
        runCurrent()
        assertEquals(0, source.fetchCount)

        releaseHolder.complete(Unit)
        holder.join()
        fetch.join()
        assertEquals(1, source.fetchCount)
    }
}

private class FakeReaderSource(private val document: ReaderDocument) : ReaderDocumentSource {
    override val pluginId = PLUGIN_ID
    var fetchCount = 0

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetchCount += 1
        return ReaderSourceResult.Success(document)
    }
}

private fun registry(source: ReaderDocumentSource) = object : ReaderDocumentSourceRegistry {
    override suspend fun enabled(): List<ReaderDocumentSource> = listOf(source)
}

private fun availability(
    readable: Set<PluginId>,
    offline: Set<PluginId>,
) = object : ReaderSourceAvailability {
    override suspend fun enabledPluginIds(): Set<PluginId> = readable
    override suspend fun offlineDownloadPluginIds(): Set<PluginId> = offline
}

private fun release() = ChapterRelease(
    id = RELEASE_ID,
    storyId = StoryId("story"),
    pluginId = PLUGIN_ID,
    sourceStoryId = "source-story",
    sourceReleaseId = "source-release",
    displayLabel = "Chapter 1",
    parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
    languageTag = "en",
    publishedAtEpochMillis = 1L,
    canonicalChapterId = null,
)

private fun textDocument() = ReaderDocument(
    null,
    listOf(ReaderBlock.Paragraph("paragraph", "Text")),
    "text-fingerprint",
)

private val RELEASE_ID = ChapterReleaseId("release")
private val PLUGIN_ID = PluginId("plugin")
