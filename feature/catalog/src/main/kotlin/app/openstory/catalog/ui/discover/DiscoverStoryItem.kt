package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.common.id.StoryId

data class DiscoverMediaTypeOption(
    val contentType: ContentType,
    val enabled: Boolean,
)

data class DiscoverStoryItem(
    val storyId: StoryId,
    val title: String,
    val coverUrl: String?,
    val contentType: ContentType,
    val score: Score?,
    val genres: List<String>,
    val publicationStatus: PublicationStatus?,
    val latestUpdate: CatalogLatestUpdate?,
)

internal val defaultDiscoverMediaTypeOptions = listOf(
    DiscoverMediaTypeOption(ContentType.MANGA, enabled = true),
    DiscoverMediaTypeOption(ContentType.LIGHT_NOVEL, enabled = false),
)
