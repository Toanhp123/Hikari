package app.openstory.catalog.projection

import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.common.id.StoryId

data class CatalogStoryProjection(
    val storyId: StoryId,
    val title: String,
    val contentType: ContentType,
    val coverUrl: String?,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val publicationStatus: PublicationStatus? = null,
    val latestUpdate: CatalogLatestUpdate? = null,
    val score: CanonicalScore? = null,
    val health: CanonicalHealth = CanonicalHealth.FRESH,
)

fun CanonicalStoryState.Ready.toProjection(): CatalogStoryProjection = CatalogStoryProjection(
    storyId = story.id,
    title = generation.metadata.title,
    contentType = story.contentType,
    coverUrl = generation.metadata.coverUrl,
    aliases = generation.metadata.aliases.toSet(),
    authors = generation.metadata.authors.toSet(),
    publicationStatus = generation.metadata.publicationStatus,
    latestUpdate = generation.metadata.latestUpdate,
    score = generation.metadata.score,
    health = health,
)
