package app.openstory.catalog.ui.chapters

import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.components.catalogDisplayName
import app.openstory.catalog.ui.download.DownloadActions
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadState
import java.math.BigDecimal
import java.util.Locale

enum class ChapterListFilter {
    ALL,
    MULTI_RELEASE,
    ;

    internal fun accepts(group: CanonicalChapterGroup): Boolean = when (this) {
        ALL -> true
        MULTI_RELEASE -> group.releases.size > 1
    }
}

enum class ChapterCapabilityState {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

data class ChapterListContent(
    val chapters: List<ChapterItemUiModel>,
    val readableTargets: List<ReaderTarget>,
    val downloadableTargets: List<ReaderTarget>,
    val releaseTargets: List<ReaderTarget>,
    val chapterCount: Int,
    // Authority belongs to the Reader capability observation, not to the currently filtered rows.
    val readerAvailabilityResolved: Boolean,
)

data class ChapterListUiState(
    val storyId: StoryId,
    val content: ContentState<ChapterListContent> = ContentState.Pending,
    val refresh: RefreshState = RefreshState(),
    val selectedFilter: ChapterListFilter = ChapterListFilter.ALL,
    val showTombstones: Boolean = false,
    val observationIssue: CatalogUiFailure? = null,
    val correctionFailure: CatalogUiFailure? = null,
)

data class ChapterItemUiModel(
    val id: CanonicalChapterId,
    val label: String,
    val tombstoned: Boolean,
    val releases: List<ChapterReleaseUiModel>,
    val title: String? = null,
    val volumeLabel: String? = null,
)

data class ChapterReleaseUiModel(
    val id: ChapterReleaseId,
    val pluginId: PluginId,
    val sourceName: String,
    val languageLabel: String,
    val publishedAtEpochMillis: Long?,
    val readerCapability: ChapterCapabilityState,
    val downloadCapability: ChapterCapabilityState = readerCapability,
)

data class ChapterListActions(
    val onRefresh: () -> Unit = {},
    val onRetryContent: () -> Unit = {},
    val onRetryObservation: () -> Unit = {},
    val onFilterSelected: (ChapterListFilter) -> Unit = {},
    val onTombstonesVisible: (Boolean) -> Unit = {},
    val onKeepGrouped: (ChapterReleaseId, CanonicalChapterId) -> Unit = { _, _ -> },
    val onSeparate: (ChapterReleaseId) -> Unit = {},
    val onRead: (ReaderTarget) -> Unit = {},
    val onDownloadFiltered: (List<ChapterReleaseId>) -> Unit = {},
    val downloadState: (ChapterReleaseId) -> DownloadState? = { null },
    val pendingRemoval: ChapterReleaseId? = null,
    val downloadActions: DownloadActions = DownloadActions(),
)

internal data class ReaderAvailability(
    val readablePluginIds: Set<PluginId>,
    val offlineDownloadPluginIds: Set<PluginId>,
)

internal val chapterNewestFirstComparator = compareByDescending<CanonicalChapterGroup> {
    it.chapter.parsedLabel.volume
}.thenByDescending {
    it.chapter.parsedLabel.chapter
}.thenByDescending {
    it.chapter.parsedLabel.part
}.thenBy {
    it.chapter.parsedLabel.kind.ordinal
}.thenByDescending {
    it.chapter.displayLabel
}

internal fun CanonicalChapterGroup.toUiModel(
    availability: ReaderAvailability?,
): ChapterItemUiModel {
    val primaryLabel = chapter.parsedLabel.primaryLabel(chapter.displayLabel)
    return ChapterItemUiModel(
        id = chapter.id,
        label = primaryLabel,
        tombstoned = chapter.tombstoned,
        releases = releases.map { release ->
            ChapterReleaseUiModel(
                id = release.id,
                pluginId = release.pluginId,
                sourceName = release.pluginId.catalogDisplayName(),
                languageLabel = release.languageTag.languageDisplayName(),
                publishedAtEpochMillis = release.publishedAtEpochMillis,
                readerCapability = availability.capabilityFor(release.pluginId, ReaderAvailability::readablePluginIds),
                downloadCapability = availability.capabilityFor(
                    release.pluginId,
                    ReaderAvailability::offlineDownloadPluginIds,
                ),
            )
        },
        title = releases.asSequence()
            .mapNotNull { release -> release.displayLabel.secondaryTitle(primaryLabel) }
            .firstOrNull()
            ?: chapter.displayLabel.secondaryTitle(primaryLabel),
        volumeLabel = chapter.parsedLabel.volume?.let { volume -> "Volume ${volume.stableValue()}" },
    )
}

private fun ReaderAvailability?.capabilityFor(
    pluginId: PluginId,
    supportedIds: (ReaderAvailability) -> Set<PluginId>,
): ChapterCapabilityState = when {
    this == null -> ChapterCapabilityState.UNKNOWN
    pluginId in supportedIds(this) -> ChapterCapabilityState.SUPPORTED
    else -> ChapterCapabilityState.UNSUPPORTED
}

private fun ParsedChapterLabel.primaryLabel(fallback: String): String {
    val chapterNumber = chapter
    return when {
        chapterNumber != null -> buildString {
            append("Chapter ")
            append(chapterNumber.stableValue())
            part?.let { value -> append(" · Part ").append(value) }
        }
        kind == ChapterKind.PROLOGUE -> "Prologue"
        kind == ChapterKind.EPILOGUE -> "Epilogue"
        kind == ChapterKind.SIDE_STORY -> "Side story"
        kind == ChapterKind.EXTRA -> "Extra"
        kind == ChapterKind.SPECIAL -> "Special"
        else -> fallback.substringBefore(CHAPTER_TITLE_SEPARATOR).trim()
    }
}

private fun String.secondaryTitle(primaryLabel: String): String? {
    val display = trim()
    if (display.equals(primaryLabel, ignoreCase = true)) return null
    val primaryPrefix = "$primaryLabel$CHAPTER_TITLE_SEPARATOR"
    val candidate = when {
        display.startsWith(primaryPrefix, ignoreCase = true) -> display.substring(primaryPrefix.length).trim()
        CHAPTER_TITLE_SEPARATOR in display -> display.substringAfter(CHAPTER_TITLE_SEPARATOR).trim()
        else -> display
    }
    return candidate.takeIf(String::isNotBlank)?.takeUnless { it.equals(primaryLabel, ignoreCase = true) }
}

private fun BigDecimal.stableValue(): String = stripTrailingZeros().toPlainString()

private fun String.languageDisplayName(): String = Locale.forLanguageTag(this)
    .getDisplayLanguage(Locale.ENGLISH)
    .takeIf(String::isNotBlank)
    ?: this

private const val CHAPTER_TITLE_SEPARATOR = " · "
