package app.openstory.catalog.feature.seed

import android.os.Looper
import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.CatalogAcquisitionSource
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.DiscoverAcquisitionSection
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class BenchmarkCatalogSource(
    private val catalogSourceKey: CatalogSourceKey,
    private val scenario: BenchmarkCatalogScenario = BenchmarkCatalogScenario.NORMAL,
    private val onAcquisitionStarted: () -> Unit = {},
    private val assertWorkerThread: () -> Unit = ::assertNotMainThread,
) : CatalogAcquisitionSource {
    private val generations = ConcurrentHashMap<CatalogMediaType, AtomicInteger>()
    private val detailStories = ConcurrentHashMap<String, BenchmarkStory>().apply {
        benchmarkStories.values.flatten().forEach { story -> put(story.sourceStoryId, story) }
    }

    override suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition {
        assertWorkerThread()
        onAcquisitionStarted()
        return when (scenario) {
            BenchmarkCatalogScenario.PERSISTED_EMPTY -> DiscoverAcquisition(emptyList())
            BenchmarkCatalogScenario.ROTATING_GENERATIONS -> rotatingStories(mediaType).toDiscoverAcquisition()
            BenchmarkCatalogScenario.NORMAL,
            BenchmarkCatalogScenario.OVERSIZED_DETAIL,
            -> benchmarkStories.getValue(mediaType).toDiscoverAcquisition()
        }
    }

    override suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition {
        assertWorkerThread()
        onAcquisitionStarted()
        require(ref.catalogSourceKey == catalogSourceKey)
        return requireNotNull(detailStories[ref.sourceStoryId])
            .toStoryDetail(oversized = scenario == BenchmarkCatalogScenario.OVERSIZED_DETAIL)
    }

    private fun rotatingStories(mediaType: CatalogMediaType): List<BenchmarkStory> {
        val generation = generations.computeIfAbsent(mediaType) { AtomicInteger() }.incrementAndGet()
        return benchmarkStories.getValue(mediaType).map { story ->
            story.copy(sourceStoryId = "${story.sourceStoryId}-generation-$generation")
                .also { generated -> detailStories[generated.sourceStoryId] = generated }
        }
    }
}

internal fun assertNotMainThread() {
    val mainLooper = runCatching(Looper::getMainLooper).getOrNull() ?: return
    check(Looper.myLooper() != mainLooper) {
        "Benchmark Catalog source executed on the main thread."
    }
}

public enum class BenchmarkCatalogScenario {
    NORMAL,
    PERSISTED_EMPTY,
    OVERSIZED_DETAIL,
    ROTATING_GENERATIONS,
}

private data class BenchmarkStory(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val logicalAssetId: String,
    val rating: Double,
    val latestUpdateEpochMs: Long,
    val remoteCover: Boolean,
)

private fun List<BenchmarkStory>.toDiscoverAcquisition() = DiscoverAcquisition(
    sections = listOf(
        DiscoverAcquisitionSection(
            CatalogSectionKind.POPULAR,
            take(CatalogSectionCaps.cap(CatalogSectionKind.POPULAR)).map(BenchmarkStory::toItem),
        ),
        DiscoverAcquisitionSection(CatalogSectionKind.LATEST_UPDATES, map(BenchmarkStory::toItem)),
        DiscoverAcquisitionSection(
            CatalogSectionKind.TOP_RATED,
            sortedByDescending(BenchmarkStory::rating)
                .take(CatalogSectionCaps.cap(CatalogSectionKind.TOP_RATED))
                .map(BenchmarkStory::toItem),
        ),
    ),
)

private fun BenchmarkStory.toItem() = DiscoverAcquisitionItem(
    sourceStoryId = sourceStoryId,
    title = title,
    contentType = contentType,
    cover = coverInput(),
    rating = CatalogRating(rating, RATING_SCALE),
    publicationStatusSummary = "Ongoing",
    latestUpdateEpochMs = latestUpdateEpochMs,
)

private fun BenchmarkStory.toStoryDetail(oversized: Boolean) = StoryDetailAcquisition(
    sourceStoryId = sourceStoryId,
    title = title,
    contentType = contentType,
    cover = coverInput(),
    rating = CatalogRating(rating, RATING_SCALE),
    publicationStatusSummary = "Ongoing",
    latestUpdateEpochMs = latestUpdateEpochMs,
    description = if (oversized) {
        "x".repeat(OVERSIZED_DESCRIPTION_CHARS)
    } else {
        "A deterministic benchmark story imported through the production Catalog pipeline."
    },
    authors = listOf("OpenStory Bench Lab"),
    artists = listOf("Hikari Studio"),
    genres = listOf("Adventure", "Mystery"),
    publicationStatus = "Ongoing",
    language = if (contentType == CatalogMediaType.MANGA) "Japanese" else "English",
)

private fun BenchmarkStory.coverInput(): AcquisitionCoverInput = if (remoteCover) {
    AcquisitionCoverInput.RemoteHttps(
        rawUri = "https://covers.hikari.invalid/$logicalAssetId.webp",
        reviewedStableArtworkToken = "benchmark-$logicalAssetId-v1",
    )
} else {
    AcquisitionCoverInput.TrustedLocal(logicalAssetId, ASSET_VERSION)
}

private val benchmarkStories = mapOf(
    CatalogMediaType.MANGA to benchmarkStories(
        mediaType = CatalogMediaType.MANGA,
        idPrefix = "benchmark-manga",
        coverPrefix = "benchmark:manga",
        titles = listOf(
            "Aster Gate",
            "Blue Hour Ronin",
            "City of Small Suns",
            "Dream Cartographer",
            "Echoes in Cedar",
            "Foxfire Dispatch",
            "Garden of Comets",
            "Harbor of Ink",
            "Iron Cicada",
        ),
    ),
    CatalogMediaType.LIGHT_NOVEL to benchmarkStories(
        mediaType = CatalogMediaType.LIGHT_NOVEL,
        idPrefix = "benchmark-light-novel",
        coverPrefix = "benchmark:light-novel",
        titles = listOf(
            "The Library Beyond Rain",
            "Tomorrow's Familiar",
            "A Minor God of Trains",
            "Winter Protocol",
            "The Orchard Between Worlds",
            "Letters from Orbit",
            "The Alchemist's Second Name",
            "One Thousand Quiet Doors",
            "The Violetless Crown",
        ),
    ),
)

private fun benchmarkStories(
    mediaType: CatalogMediaType,
    idPrefix: String,
    coverPrefix: String,
    titles: List<String>,
): List<BenchmarkStory> = titles.mapIndexed { index, title ->
    BenchmarkStory(
        sourceStoryId = if (index == 0) "$idPrefix-星-01" else "$idPrefix-${index + 1}",
        title = title,
        contentType = mediaType,
        logicalAssetId = "$coverPrefix:cover-${if (index % 2 == 0) "a" else "b"}",
        rating = 7.2 + (index * 0.3),
        latestUpdateEpochMs = 1_790_000_000_000L + (index * 86_400_000L),
        remoteCover = index == 0,
    )
}

private const val ASSET_VERSION = "1"
private const val RATING_SCALE = 10.0
private const val OVERSIZED_DESCRIPTION_CHARS = 65_537
