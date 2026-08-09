package app.openstory.home.model

import app.openstory.common.AppError
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class HomeUiModel(
    val combined: List<HomeCombinedCard>,
    val catalogs: List<HomeCatalog>,
)

data class HomeCatalog(
    val pluginId: PluginId,
    val pluginVersion: String,
    val refreshedAtEpochMillis: Long,
    val sections: List<HomeCatalogSection>,
)

data class HomeCatalogSection(
    val sourceId: String,
    val title: String,
    val items: List<HomeCatalogCard>,
)

data class HomeCatalogCard(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: Set<String>,
    val coverReference: String?,
    val score: Double?,
    val scoreScale: Double?,
)

data class HomeCombinedCard(
    val storyId: StoryId,
    val orderingScore: Double,
    val sources: List<HomeCombinedSource>,
)

data class HomeCombinedSource(
    val pluginId: PluginId,
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: Set<String>,
    val coverReference: String?,
    val score: Double?,
    val scoreScale: Double?,
    val normalizedScore: Double?,
    val priorityWeight: Double,
    val sections: List<HomeSectionMembership>,
)

data class HomeSectionMembership(
    val sourceId: String,
    val title: String,
    val sectionPosition: Int,
    val itemPosition: Int,
)

data class HomeCatalogFreshness(
    val refreshedAtEpochMillis: Long?,
    val stale: Boolean,
)

data class HomeRefreshReport(
    val succeeded: List<PluginId> = emptyList(),
    val failed: Map<PluginId, AppError> = emptyMap(),
    val freshness: Map<PluginId, HomeCatalogFreshness> = emptyMap(),
) {
    internal fun recordSuccess(
        pluginId: PluginId,
        refreshedAtEpochMillis: Long?,
    ): HomeRefreshReport = copy(
        succeeded = succeeded + pluginId,
        freshness = freshness + (
            pluginId to HomeCatalogFreshness(
                refreshedAtEpochMillis = refreshedAtEpochMillis,
                stale = false,
            )
        ),
    )

    internal fun recordFailure(
        pluginId: PluginId,
        error: AppError,
        refreshedAtEpochMillis: Long?,
    ): HomeRefreshReport = copy(
        failed = failed + (pluginId to error),
        freshness = freshness + (
            pluginId to HomeCatalogFreshness(
                refreshedAtEpochMillis = refreshedAtEpochMillis,
                stale = true,
            )
        ),
    )
}
