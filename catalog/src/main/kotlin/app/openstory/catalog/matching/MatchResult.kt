package app.openstory.catalog.matching

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class CatalogMatchEvidence(
    val titles: Set<String>,
    val authors: Set<String>,
    val contentType: ContentType,
)

data class CatalogMatchCandidate(
    val story: app.openstory.catalog.model.Story,
    val titles: Set<String>,
    val authors: Set<String>,
    val sourceKeys: Set<SourceKey>,
    val evidence: List<CatalogMatchEvidence> = listOf(
        CatalogMatchEvidence(titles, authors, story.contentType),
    ),
)

data class SourceKey(val pluginId: PluginId, val sourceId: String) {
    init { require(sourceId.isNotBlank()) }
}

sealed interface StoryResolution {
    data class Existing(val storyId: StoryId) : StoryResolution
    data class Create(val story: app.openstory.catalog.model.Story) : StoryResolution
}

enum class MergeDecision { AUTO_LINK, REVIEW, SEPARATE }

data class CatalogMatchResult(
    val storyId: StoryId,
    val score: Double,
    val decision: MergeDecision,
)
