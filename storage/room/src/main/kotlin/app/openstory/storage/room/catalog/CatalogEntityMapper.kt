package app.openstory.storage.room.catalog

import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

internal fun Story.toEntity() = StoryEntity(id.value, contentType.name)

internal fun CatalogEntry.toEntity(pluginVersion: String, fetchedAtEpochMillis: Long) = CatalogEntryEntity(
    pluginId = pluginId.value,
    sourceId = sourceId,
    storyId = storyId.value,
    title = title,
    aliases = aliases,
    authors = authors,
    description = description,
    genres = genres,
    contentType = contentType.name,
    languageTags = languageTags,
    coverUrl = coverUrl,
    sourceUrl = sourceUrl,
    scoreValue = score?.value,
    scoreScale = score?.scale,
    popularityRank = popularityRank,
    pluginVersion = pluginVersion,
    fetchedAtEpochMillis = fetchedAtEpochMillis,
)

internal fun CatalogHomeSnapshotEntity.toModel(
    sections: List<CatalogHomeSectionEntity>,
    items: List<CatalogHomeItemEntity>,
    entries: Map<Pair<String, String>, CatalogEntry>,
): CatalogHomeSnapshot {
    val sectionsById = items.groupBy { it.sectionId }
    return CatalogHomeSnapshot(
        pluginId = PluginId(pluginId),
        pluginVersion = pluginVersion,
        refreshedAtEpochMillis = refreshedAtEpochMillis,
        sections = sections.sortedBy { it.position }.map { section ->
            CatalogHomeSection(
                sourceId = section.sectionId,
                title = section.title,
                items = sectionsById[section.sectionId].orEmpty().sortedBy { it.position }.mapNotNull { item ->
                    entries[pluginId to item.sourceId]
                },
            )
        },
    )
}

internal fun StoryEntity.toModel() = Story(StoryId(storyId), ContentType.valueOf(contentType))

internal fun CatalogEntryEntity.toModel() = CatalogEntry(
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceId = sourceId,
    title = title,
    aliases = aliases,
    authors = authors,
    description = description,
    genres = genres,
    contentType = ContentType.valueOf(contentType),
    languageTags = languageTags,
    coverUrl = coverUrl,
    sourceUrl = sourceUrl,
    score = if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
    popularityRank = popularityRank,
)

internal fun CatalogEntryEntity.toCandidate() = CatalogMatchCandidate(
    story = Story(StoryId(storyId), ContentType.valueOf(contentType)),
    titles = setOf(title) + aliases,
    authors = authors,
    sourceKeys = setOf(SourceKey(PluginId(pluginId), sourceId)),
)
