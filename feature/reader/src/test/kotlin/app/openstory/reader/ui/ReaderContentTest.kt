package app.openstory.reader.ui

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.assets.ReaderAssetGraphRevision
import app.openstory.reader.assets.ReaderAssetManifestFactory
import app.openstory.reader.assets.ReaderCommittedAssetManifestSnapshot
import app.openstory.reader.assets.ReaderViewportDirection
import app.openstory.reader.assets.ReaderViewportSnapshot
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderSessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReaderContentTest {
    private val blocks = listOf(
        ReaderBlock.Paragraph("first", "First"),
        ReaderBlock.Paragraph("second", "Second"),
    )

    @Test
    fun freshDocumentStartsAtTheTitle() {
        assertEquals(0, restoredReaderItemIndex(blocks, hasTitle = true, restoredBlockId = null))
    }

    @Test
    fun staleBlockIdentityFallsBackToTheTop() {
        assertEquals(0, restoredReaderItemIndex(blocks, hasTitle = true, restoredBlockId = "missing"))
    }

    @Test
    fun knownBlockIncludesTheTitleOffset() {
        assertEquals(2, restoredReaderItemIndex(blocks, hasTitle = true, restoredBlockId = "second"))
    }

    @Test
    fun imagePagesDoNotInventCharacterOffsets() {
        val page = ReaderBlock.ImagePage("page", "hash/page.png", "https://node.example/page.png")

        assertEquals(0, page.progressExtent())
        assertEquals(5, ReaderBlock.Paragraph("text", "Hello").progressExtent())
    }

    @Test
    fun imageRestoreUsesDocumentFractionWithinTheKnownPage() {
        assertEquals(0.5f, restoredImagePageFraction(blockIndex = 1, blockCount = 4, documentFraction = 0.375f))
        assertEquals(0f, restoredImagePageFraction(blockIndex = 2, blockCount = 4, documentFraction = 0.1f))
        assertEquals(1f, restoredImagePageFraction(blockIndex = 2, blockCount = 4, documentFraction = 0.9f))
    }

    @Test
    fun mixedLazyKeysMapOnlyImageBlocksToManifestOrdinals() {
        val document = ReaderDocument(
            title = "Chapter",
            blocks = listOf(
                ReaderBlock.Paragraph("paragraph", "Text"),
                ReaderBlock.ImagePage("page-a", "asset-a", "https://example.test/a.jpg"),
                ReaderBlock.Note("note", "Note"),
                ReaderBlock.ImagePage("page-b", "asset-b", "https://example.test/b.jpg"),
            ),
            fingerprint = "fingerprint",
        )
        val assets = assetState(document)

        val bounds = readerVisibleImageOrdinalBounds(
            visibleItemKeys = listOf("reader-title", "paragraph", "page-b", "note"),
            assets = assets,
        )

        assertEquals(1, bounds?.first)
        assertEquals(1, bounds?.last)
        assertEquals(0, assertNotNull(assets.requestForBlockId("page-a")).descriptor.imageOrdinal)
        assertEquals(1, assertNotNull(assets.requestForBlockId("page-b")).descriptor.imageOrdinal)
        assertNull(assets.requestForBlockId("paragraph"))
    }

    @Test
    fun observedViewportEqualityIgnoresProgressAndDirectionButTracksVisibleImageWindow() {
        val base = ReaderViewportSnapshot(
            sessionId = ReaderSessionId(1),
            manifestRevision = 4,
            leadingVisibleImageOrdinal = 2,
            trailingVisibleImageOrdinal = 4,
            direction = ReaderViewportDirection.IDLE,
            chapterProgressBasisPoints = 2_000,
        )

        val sameWindow = base.copy(
            direction = ReaderViewportDirection.FORWARD,
            chapterProgressBasisPoints = 8_000,
        )
        val differentWindow = base.copy(
            leadingVisibleImageOrdinal = 3,
            trailingVisibleImageOrdinal = 5,
        )

        assertEquals(true, base.hasSameVisibleAssetWindow(sameWindow))
        assertEquals(false, base.hasSameVisibleAssetWindow(differentWindow))
        val noViewport: ReaderViewportSnapshot? = null
        assertEquals(false, base.hasSameVisibleAssetWindow(noViewport))
        assertEquals(true, noViewport.hasSameVisibleAssetWindow(null))
    }

    @Test
    fun committedAssetSnapshotAcceptsOnlyCurrentSemanticIdentityAndNewerRevision() {
        val document = ReaderDocument(
            title = null,
            blocks = listOf(ReaderBlock.ImagePage("page", "asset", "https://example.test/page.jpg")),
            fingerprint = "fingerprint",
        )
        val current = assetState(document)
        val matching = ReaderCommittedAssetManifestSnapshot(
            sessionId = current.manifest.sessionId,
            manifestRevision = 2,
            manifest = current.manifest,
        )

        assertNotNull(
            matching.toReaderAssetUiStateIfCurrent(
                activeSessionId = current.manifest.sessionId,
                activeChapterId = current.manifest.canonicalChapterId,
                activeReleaseId = current.manifest.selectedReleaseId,
                currentManifestRevision = current.manifestRevision,
            ),
        )
        assertNull(
            matching.copy(sessionId = ReaderSessionId(2)).toReaderAssetUiStateIfCurrent(
                activeSessionId = current.manifest.sessionId,
                activeChapterId = current.manifest.canonicalChapterId,
                activeReleaseId = current.manifest.selectedReleaseId,
                currentManifestRevision = current.manifestRevision,
            ),
        )
        assertNull(
            matching.copy(
                manifest = current.manifest.copy(canonicalChapterId = CanonicalChapterId("stale-chapter")),
            ).toReaderAssetUiStateIfCurrent(
                activeSessionId = current.manifest.sessionId,
                activeChapterId = current.manifest.canonicalChapterId,
                activeReleaseId = current.manifest.selectedReleaseId,
                currentManifestRevision = current.manifestRevision,
            ),
        )

        val staleRelease = assetState(document, releaseId = "stale-release")
        assertNull(
            ReaderCommittedAssetManifestSnapshot(
                sessionId = current.manifest.sessionId,
                manifestRevision = 2,
                manifest = staleRelease.manifest.copy(sessionId = current.manifest.sessionId),
            ).toReaderAssetUiStateIfCurrent(
                activeSessionId = current.manifest.sessionId,
                activeChapterId = current.manifest.canonicalChapterId,
                activeReleaseId = current.manifest.selectedReleaseId,
                currentManifestRevision = current.manifestRevision,
            ),
        )
        assertNull(
            matching.copy(manifestRevision = current.manifestRevision).toReaderAssetUiStateIfCurrent(
                activeSessionId = current.manifest.sessionId,
                activeChapterId = current.manifest.canonicalChapterId,
                activeReleaseId = current.manifest.selectedReleaseId,
                currentManifestRevision = current.manifestRevision,
            ),
        )
    }

    @Test
    fun readerProgressSamplingIsBoundedToTenUpdatesPerSecond() {
        assertEquals(100L, READER_PROGRESS_SAMPLE_MILLIS)
    }

    @Test
    fun visibleProgressBucketsToWholePercent() {
        assertEquals(0, fractionToPercent(0f))
        assertEquals(42, fractionToPercent(0.421f))
        assertEquals(42, fractionToPercent(0.429f))
        assertEquals(100, fractionToPercent(1f))
    }

    private fun assetState(
        document: ReaderDocument,
        releaseId: String = "release",
    ): ReaderAssetUiState {
        val release = ChapterRelease(
            id = ChapterReleaseId(releaseId),
            storyId = StoryId("story"),
            pluginId = PluginId("plugin"),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-$releaseId",
            displayLabel = "chapter",
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "en",
            publishedAtEpochMillis = 1,
            canonicalChapterId = CanonicalChapterId("chapter"),
        )
        val manifest = assertNotNull(
            ReaderAssetManifestFactory().create(
                sessionId = ReaderSessionId(1),
                storyId = StoryId("story"),
                canonicalChapterId = CanonicalChapterId("chapter"),
                selectedRelease = release,
                graphRevision = ReaderAssetGraphRevision(1),
                document = document,
                imageSourcePolicy = ReaderImageSourcePolicy.FAIL_CLOSED,
                sourcePluginId = PluginId("plugin"),
            ),
        )
        return ReaderAssetUiState(manifest = manifest, manifestRevision = 1)
    }
}
