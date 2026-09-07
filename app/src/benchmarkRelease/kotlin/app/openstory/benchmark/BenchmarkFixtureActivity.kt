package app.openstory.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.chapters.aggregation.AggregationPlan
import app.openstory.chapters.aggregation.ChapterReleaseLink
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Outcome
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.assets.ReaderAssetMetadataRepository
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryStatus
import app.openstory.reader.assets.ReaderAssetClearScope
import app.openstory.reader.assets.ContentFetchPriority
import app.openstory.reader.assets.ReaderAssetCommitFacts
import app.openstory.reader.assets.ReaderAssetConsumerToken
import app.openstory.reader.assets.ReaderAssetGraphRevision
import app.openstory.reader.assets.ReaderAssetIdentity
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetLoadOutcome
import app.openstory.reader.assets.ReaderAssetLoader
import app.openstory.reader.assets.ReaderAssetLocalPresence
import app.openstory.reader.assets.ReaderAssetManifestFactory
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderCacheSecurityScope
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace
import app.openstory.reader.assets.ReaderAssetStorePort
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.routing.ReaderSessionId
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class BenchmarkFixtureActivity : ComponentActivity() {
    @Inject lateinit var catalog: CatalogRepository
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var chapters: ChapterRepository
    @Inject lateinit var documents: ReaderDocumentStore
    @Inject lateinit var progress: ReadingProgressRepository
    @Inject lateinit var downloads: DownloadRepository
    @Inject lateinit var cache: CacheRepository
    @Inject lateinit var automaticCacheBudget: AutomaticCacheBudgetCoordinator
    @Inject lateinit var readerAssetMetadata: ReaderAssetMetadataRepository
    @Inject lateinit var readerAssetStore: ReaderAssetStorePort
    @Inject lateinit var readerAssetLoader: ReaderAssetLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = BENCHMARK_SEEDING_TEXT }
        setContentView(status)
        lifecycleScope.launch {
            runCatching { seedFixture(BenchmarkFixtureProfile.from(intent)) }
                .onSuccess { status.text = BENCHMARK_READY_TEXT }
                .onFailure { error -> status.text = "$BENCHMARK_FAILED_PREFIX${error::class.java.simpleName}" }
        }
    }

    private suspend fun seedFixture(profile: BenchmarkFixtureProfile) {
        val readerCacheMode = BenchmarkReaderCacheMode.from(intent)
        readerAssetStore.clearAutomatic(ReaderAssetClearScope.AllAutomatic)
        seedReaderAssetMetadata(profile.readerAssetMetadataRows)
        seedAutomaticCacheMetadata(profile.automaticCacheRows)
        automaticCacheBudget.reconcile()

        val browseEntries = seedBrowseFixtures(profile)
        val storyId = StoryId(BENCHMARK_STORY_ID)
        val catalogResult = catalog.commitDetails(
            CatalogDetailsMutation(
                storyId = storyId,
                entry = CatalogEntry(
                    storyId = storyId,
                    pluginId = PluginId(BENCHMARK_PLUGIN_ID),
                    sourceId = BENCHMARK_SOURCE_ID,
                    title = BENCHMARK_STORY_TITLE,
                    authors = setOf("Hikari"),
                    description = benchmarkMetadata(
                        "Deterministic local story used only by benchmarkRelease.",
                        profile.metadataWidth,
                    ),
                    contentType = ContentType.MANGA,
                    languageTags = setOf("en"),
                ),
                pluginVersion = BENCHMARK_PLUGIN_VERSION,
                resolvedAtEpochMillis = BENCHMARK_EPOCH_MILLIS,
            ),
        )
        check(catalogResult is Outcome.Success)
        seedLibraryMembership(profile, storyId, browseEntries)

        val chapterFixtures = (1..profile.chapterCount).map { index -> chapterFixture(storyId, index, profile) }
        val commit = chapters.commit(
            ChapterMutation(
                storyId = storyId,
                releases = chapterFixtures.map(ChapterFixture::release),
                plan = AggregationPlan(
                    creates = chapterFixtures.map(ChapterFixture::chapter),
                    links = chapterFixtures.map { fixture ->
                        ChapterReleaseLink(fixture.release.id, fixture.chapter.id)
                    },
                    unlinks = emptySet(),
                    tombstones = emptySet(),
                    reviewCandidates = emptyList(),
                ),
            ),
        )
        check(commit == ChapterCommitResult.Success)

        var imageFixture: Pair<ChapterFixture, ReaderDocument>? = null
        chapterFixtures.forEachIndexed { zeroBasedIndex, fixture ->
            val imageDocument = benchmarkReaderImageDocument(fixture.release)
            val document = imageDocument ?: benchmarkDocument(fixture.index)
            if (imageDocument == null) {
                documents.write(fixture.release.id, document.fingerprint, document)
                check(documents.read(fixture.release.id, document.fingerprint) == document) {
                    "Benchmark Reader document was not persisted for chapter ${fixture.index}."
                }
            } else {
                imageFixture = fixture to imageDocument
            }
            if (zeroBasedIndex < profile.progressRows) seedProgress(storyId, fixture, document, profile)
            if (zeroBasedIndex < profile.explicitDownloadRecords) seedExplicitDownload(fixture, document)
        }
        imageFixture?.let { (fixture, document) ->
            prepareReaderImageCache(fixture, document, readerCacheMode)
        }
    }

    private suspend fun seedReaderAssetMetadata(rowCount: Int) {
        repeat(rowCount) { index ->
            val identity = benchmarkSha256("benchmark-reader-history-identity:$index")
            readerAssetMetadata.upsert(
                ReaderAssetMetadata(
                    logicalAssetKeyHash = ReaderAssetKeyHash(
                        benchmarkSha256("benchmark-reader-history-key:$index"),
                    ),
                    keySchemaVersion = ReaderAssetIdentity.KEY_SCHEMA_VERSION,
                    storyId = StoryId("benchmark-reader-history-story-$index"),
                    canonicalChapterId = CanonicalChapterId("benchmark-reader-history-chapter-$index"),
                    chapterReleaseId = ChapterReleaseId("benchmark-reader-history-release-$index"),
                    sourceNamespace = BENCHMARK_HISTORY_READER_SOURCE,
                    securityScopeHash = null,
                    contentVariant = ReaderContentVariant.ORIGINAL,
                    identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
                    persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
                    imageSetNamespaceHash = ReaderImageSetNamespace(
                        benchmarkSha256("benchmark-reader-history-set:$index"),
                    ),
                    pageIdentityHash = ReaderAssetIdentityHash(identity),
                    pageOrdinal = index,
                    blobId = benchmarkSha256("benchmark-reader-history-blob:$index"),
                    byteSize = 1L,
                    localBlobChecksum = BlobChecksum.sha256("reader-history:$index".encodeToByteArray()),
                    sourceIntegrityHash = null,
                    createdAtEpochMillis = BENCHMARK_EPOCH_MILLIS - index,
                    lastAccessedAtEpochMillis = BENCHMARK_EPOCH_MILLIS - index,
                    lastConsumedAtEpochMillis = null,
                ),
            )
        }
    }

    private suspend fun seedAutomaticCacheMetadata(rowCount: Int) {
        repeat(rowCount) { index ->
            cache.upsert(
                CacheEntry(
                    key = ChapterBlobKey(
                        namespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
                        releaseId = ChapterReleaseId("benchmark-cache-history-release-$index"),
                        contentFingerprint = "benchmark-cache-history-fingerprint-$index",
                    ),
                    checksum = BlobChecksum.sha256("cache-history:$index".encodeToByteArray()),
                    sizeBytes = 1L,
                    lastAccessedAtEpochMillis = BENCHMARK_EPOCH_MILLIS - index,
                ),
            )
        }
    }

    private suspend fun prepareReaderImageCache(
        fixture: ChapterFixture,
        document: ReaderDocument,
        cacheMode: BenchmarkReaderCacheMode,
    ) {
        val sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(fixture.release.pluginId)
        readerAssetStore.clearAutomatic(ReaderAssetClearScope.Source(sourceNamespace))
        if (cacheMode != BenchmarkReaderCacheMode.WARM) return
        val manifest = requireNotNull(
            ReaderAssetManifestFactory().create(
                sessionId = ReaderSessionId(BENCHMARK_PREWARM_SESSION_ID),
                storyId = fixture.release.storyId,
                canonicalChapterId = requireNotNull(fixture.release.canonicalChapterId),
                selectedRelease = fixture.release,
                graphRevision = ReaderAssetGraphRevision(1L),
                document = document,
                imageSourcePolicy = BenchmarkReaderDocumentSource().imageSourcePolicy,
                sourcePluginId = fixture.release.pluginId,
            ),
        )
        manifest.descriptors.forEachIndexed { index, descriptor ->
            val presence = readerAssetStore.inspect(setOf(descriptor.key))[descriptor.key]
                ?: ReaderAssetLocalPresence.UNKNOWN
            check(
                readerAssetLoader.load(
                    facts = ReaderAssetCommitFacts(
                        key = descriptor.key,
                        storyId = manifest.storyId,
                        canonicalChapterId = manifest.canonicalChapterId,
                        releaseId = manifest.selectedReleaseId,
                        sourceNamespace = manifest.sourceNamespace,
                        securityScope = manifest.securityScope,
                        contentVariant = manifest.contentVariant,
                        identityMode = manifest.identityMode,
                        persistenceMode = manifest.persistenceMode,
                        imageSetNamespace = manifest.imageSetNamespace,
                        imageOrdinal = descriptor.imageOrdinal,
                    ),
                    descriptor = descriptor,
                    localPresence = presence,
                    priority = ContentFetchPriority.CRITICAL,
                    consumer = ReaderAssetConsumerToken(index + 1L),
                ) !is ReaderAssetLoadOutcome.Failure,
            )
        }
        withTimeout(BENCHMARK_CACHE_PREWARM_TIMEOUT_MILLIS) {
            val hashes = manifest.descriptors.mapTo(linkedSetOf()) { descriptor -> descriptor.key.hash }
            while (readerAssetMetadata.find(hashes).size != hashes.size) delay(BENCHMARK_CACHE_PERSIST_POLL_MILLIS)
        }
    }

    private suspend fun seedBrowseFixtures(profile: BenchmarkFixtureProfile): List<CatalogEntry> {
        val pluginId = PluginId(BENCHMARK_PLUGIN_ID)
        val entries = List(profile.catalogStories) { index ->
            val storyId = StoryId("benchmark-browse-story-$index")
            CatalogEntry(
                storyId = storyId,
                pluginId = pluginId,
                sourceId = "benchmark-browse-source-$index",
                title = "Benchmark Browse Story ${index + 1}",
                authors = setOf("Historical Fixture Author ${index + 1}"),
                description = benchmarkMetadata(
                    "Deterministic browse fixture ${index + 1} for scroll macrobenchmarks.",
                    profile.metadataWidth,
                ),
                genres = setOf("Fantasy", "Adventure"),
                contentType = ContentType.MANGA,
                languageTags = setOf("en"),
                coverUrl = BENCHMARK_BROWSE_COVER_URL,
                score = benchmarkBrowseScore(index),
                popularityRank = (index + 1).toLong(),
                publicationStatus = if (index % 5 == 0) {
                    PublicationStatus.COMPLETED
                } else {
                    PublicationStatus.ONGOING
                },
                latestUpdate = CatalogLatestUpdate(
                    atEpochMillis = BENCHMARK_EPOCH_MILLIS - index,
                    releaseLabel = (profile.catalogStories - index).toString(),
                ),
            )
        }
        val sections = listOf(
            CatalogHomeSection(
                sourceId = "benchmark-popular",
                title = "Popular",
                items = entries.take(5),
                kind = CatalogFeedKind.POPULAR,
            ),
            CatalogHomeSection(
                sourceId = "benchmark-latest",
                title = "Latest Updates",
                items = entries.take(9),
                kind = CatalogFeedKind.LATEST_UPDATES,
            ),
            CatalogHomeSection(
                sourceId = "benchmark-top-rated",
                title = "Top Rated",
                items = entries.take(5),
                kind = CatalogFeedKind.TOP_RATED,
            ),
            CatalogHomeSection(
                sourceId = "benchmark-other",
                title = "Benchmark Remainder",
                items = entries.drop(9),
                kind = CatalogFeedKind.OTHER,
            ),
        )
        val homeResult = catalog.commitHomeRefresh(
            CatalogHomeMutation(
                pluginId = pluginId,
                pluginVersion = BENCHMARK_PLUGIN_VERSION,
                refreshedAtEpochMillis = BENCHMARK_EPOCH_MILLIS,
                stories = entries.map { entry -> Story(entry.storyId, entry.contentType) },
                entries = entries,
                sections = sections,
                orderedSourceItemIds = sections.associate { section ->
                    section.sourceId to section.items.map(CatalogEntry::sourceId)
                },
            ),
        )
        check(homeResult is Outcome.Success)
        return entries
    }

    private suspend fun seedLibraryMembership(
        profile: BenchmarkFixtureProfile,
        targetStoryId: StoryId,
        browseEntries: List<CatalogEntry>,
    ) {
        library.add(targetStoryId, LibraryStatus.READING, BENCHMARK_EPOCH_MILLIS)
        check(
            library.changeStatus(
                targetStoryId,
                LibraryStatus.READING,
                BENCHMARK_EPOCH_MILLIS + BENCHMARK_PRIMARY_ACTIVITY_OFFSET,
            ) != null,
        )
        browseEntries.take(profile.libraryEntries - 1).forEachIndexed { index, entry ->
            val activityAt = BENCHMARK_EPOCH_MILLIS - index - 1
            library.add(entry.storyId, LibraryStatus.WANT_TO_READ, activityAt)
            check(
                library.changeStatus(entry.storyId, LibraryStatus.WANT_TO_READ, activityAt) != null,
            )
        }
    }

    private suspend fun seedProgress(
        storyId: StoryId,
        fixture: ChapterFixture,
        document: ReaderDocument,
        profile: BenchmarkFixtureProfile,
    ) {
        progress.save(
            ReadingProgress(
                storyId = storyId,
                canonicalChapterId = fixture.chapter.id,
                releaseId = fixture.release.id,
                contentFingerprint = document.fingerprint,
                position = ReadingPosition(document.blocks.first().id, 0, 0f),
                completedAtEpochMillis = if (fixture.index == BENCHMARK_RESUME_CHAPTER_INDEX) {
                    null
                } else {
                    BENCHMARK_EPOCH_MILLIS
                },
                updatedAtEpochMillis = BENCHMARK_EPOCH_MILLIS + (profile.chapterCount - fixture.index),
            ),
        )
    }

    private suspend fun seedExplicitDownload(fixture: ChapterFixture, document: ReaderDocument) {
        downloads.save(
            DownloadRecord(
                key = ChapterBlobKey(
                    namespace = ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
                    releaseId = fixture.release.id,
                    contentFingerprint = document.fingerprint,
                ),
                state = DownloadState.QUEUED,
                updatedAtEpochMillis = BENCHMARK_EPOCH_MILLIS + fixture.index,
            ),
        )
    }

    private fun benchmarkMetadata(base: String, width: Int): String = when {
        width == BenchmarkFixtureProfile.DEFAULT.metadataWidth -> base
        base.length >= width -> base.take(width)
        else -> buildString(width) {
            append(base)
            while (length < width) append(" benchmark-metadata")
        }.take(width)
    }

    private fun chapterFixture(storyId: StoryId, index: Int, profile: BenchmarkFixtureProfile): ChapterFixture {
        val chapterId = CanonicalChapterId("$BENCHMARK_STORY_ID:chapter:$index")
        val releaseId = ChapterReleaseId("$BENCHMARK_STORY_ID:release:$index")
        val parsed = ParsedChapterLabel(
            kind = ChapterKind.NUMBERED,
            volume = null,
            chapter = BigDecimal.valueOf(index.toLong()),
            part = null,
            normalizedTitle = null,
        )
        return ChapterFixture(
            index = index,
            chapter = CanonicalChapter(
                id = chapterId,
                storyId = storyId,
                parsedLabel = parsed,
                displayLabel = "Chapter $index",
                tombstoned = false,
                releaseIds = setOf(releaseId),
            ),
            release = ChapterRelease(
                id = releaseId,
                storyId = storyId,
                pluginId = PluginId(BENCHMARK_PLUGIN_ID),
                sourceStoryId = BENCHMARK_SOURCE_ID,
                sourceReleaseId = if (index == BENCHMARK_RESUME_CHAPTER_INDEX && profile.readerImagePages > 0) {
                    "$BENCHMARK_READER_IMAGE_SOURCE_PREFIX${profile.readerImagePages}"
                } else {
                    "chapter-$index"
                },
                displayLabel = "Chapter $index",
                parsedLabel = parsed,
                languageTag = "en",
                publishedAtEpochMillis = BENCHMARK_EPOCH_MILLIS + index,
                canonicalChapterId = chapterId,
            ),
        )
    }

    private fun benchmarkDocument(index: Int): ReaderDocument {
        val blocks = List(BENCHMARK_PARAGRAPH_COUNT) { blockIndex ->
            ReaderBlock.Paragraph(
                id = "benchmark-$index-block-$blockIndex",
                text = "Benchmark chapter $index paragraph $blockIndex. " + BENCHMARK_PARAGRAPH_BODY,
            )
        }
        return ReaderDocument(
            title = "Benchmark Chapter $index",
            blocks = blocks,
            fingerprint = "benchmark-fixture-fingerprint-$index",
        )
    }

    private data class ChapterFixture(
        val index: Int,
        val chapter: CanonicalChapter,
        val release: ChapterRelease,
    )

    private companion object {
        const val BENCHMARK_STORY_ID = "benchmark-fixture-story"
        const val BENCHMARK_PLUGIN_ID = "benchmark-fixture"
        const val BENCHMARK_SOURCE_ID = "benchmark-fixture-source"
        const val BENCHMARK_PLUGIN_VERSION = "1.0.0"
        const val BENCHMARK_STORY_TITLE = "Hikari Benchmark Fixture"
        const val BENCHMARK_BROWSE_COVER_URL =
            "android.resource://app.openstory/drawable/benchmark_browse_cover"
        const val BENCHMARK_RESUME_CHAPTER_INDEX = 1
        const val BENCHMARK_PARAGRAPH_COUNT = 24
        const val BENCHMARK_EPOCH_MILLIS = 1_700_000_000_000L
        const val BENCHMARK_PRIMARY_ACTIVITY_OFFSET = 1_000L
        const val BENCHMARK_PREWARM_SESSION_ID = 9_001L
        const val BENCHMARK_CACHE_PREWARM_TIMEOUT_MILLIS = 60_000L
        const val BENCHMARK_CACHE_PERSIST_POLL_MILLIS = 10L
        const val BENCHMARK_SEEDING_TEXT = "HIKARI_BENCHMARK_SEEDING"
        const val BENCHMARK_READY_TEXT = "HIKARI_BENCHMARK_READY"
        const val BENCHMARK_FAILED_PREFIX = "HIKARI_BENCHMARK_FAILED:"
        const val BENCHMARK_PARAGRAPH_BODY =
            "This deterministic local content keeps Reader measurement independent from network and plugin state."
    }
}

private val BENCHMARK_HISTORY_READER_SOURCE = ReaderAssetSourceNamespace.fromPluginId(
    PluginId("benchmark-reader-history"),
)

private fun benchmarkSha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal fun benchmarkBrowseScore(index: Int): Score =
    Score(value = (10.0 - index * 0.1).coerceAtLeast(0.0), scale = 10.0)
