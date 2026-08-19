package app.openstory.catalog.source

enum class SourceContentType { LIGHT_NOVEL, WEB_NOVEL, MANGA, ANIME }

enum class SourceFeedKind { POPULAR, LATEST_UPDATES, TOP_RATED, OTHER }

enum class SourcePublicationStatus { ONGOING, COMPLETED, HIATUS, CANCELLED, UPCOMING }

data class SourceLatestUpdate(
    val atEpochMillis: Long,
    val releaseLabel: String?,
)

data class SourceHomeRequest(
    val languageTags: Set<String> = emptySet(),
    val contentTypes: Set<SourceContentType> = emptySet(),
)

data class SourceSearchRequest(
    val query: String,
    val filterValues: Map<String, List<String>> = emptyMap(),
    val nextToken: String? = null,
)

data class SourceSection(
    val sourceId: String,
    val title: String,
    val items: List<SourceItem>,
    val kind: SourceFeedKind = SourceFeedKind.OTHER,
)

data class SourceItem(
    val sourceId: String,
    val title: String,
    val contentType: SourceContentType,
    val authors: Set<String>,
    val coverUrl: String?,
    val scoreValue: Double?,
    val scoreScale: Double?,
    val genres: Set<String> = emptySet(),
    val popularityRank: Long? = null,
    val publicationStatus: SourcePublicationStatus? = null,
    val latestUpdate: SourceLatestUpdate? = null,
)

data class SourceSearchPage(
    val items: List<SourceItem>,
    val nextToken: String?,
)

data class SourceDetails(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    val contentType: SourceContentType,
    val languageTags: Set<String>,
    val coverUrl: String?,
    val scoreValue: Double?,
    val scoreScale: Double?,
    val popularityRank: Long?,
    val publicationStatus: SourcePublicationStatus? = null,
    val latestUpdate: SourceLatestUpdate? = null,
)

sealed interface SourceFilter {
    val id: String
    val label: String
}

data class SourceOptionFilter(
    override val id: String,
    override val label: String,
    val multiple: Boolean,
    val options: List<SourceFilterOption>,
) : SourceFilter

data class SourceRangeFilter(
    override val id: String,
    override val label: String,
    val min: Double?,
    val max: Double?,
    val step: Double?,
) : SourceFilter

data class SourceTextFilter(
    override val id: String,
    override val label: String,
) : SourceFilter

data class SourceFilterOption(
    val value: String,
    val label: String,
)
