package app.openstory.catalog.projection

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId

data class CatalogStoryProjection(
    val storyId: StoryId,
    val title: String,
    val contentType: ContentType,
    val coverUrl: String?,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
)

fun projectCatalogStory(
    story: Story,
    entries: List<CatalogEntry>,
): CatalogStoryProjection {
    val ordered = entries.sortedWith(
        compareBy<CatalogEntry> { it.pluginId.value }
            .thenBy { it.sourceId },
    )
    val preferred = ordered.firstOrNull()
    return CatalogStoryProjection(
        storyId = story.id,
        title = preferred?.title ?: story.id.value,
        contentType = story.contentType,
        coverUrl = preferred?.coverUrl,
        aliases = ordered.flatMap(CatalogEntry::aliases).toSet(),
        authors = ordered.flatMap(CatalogEntry::authors).toSet(),
    )
}
