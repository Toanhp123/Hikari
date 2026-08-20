package app.openstory.storage.room.catalog

import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.CatalogMatchEvidence
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

internal fun Story.toEntity() = StoryEntity(id.value, contentType.name)

internal fun CatalogEntry.toHomeEntity(
    summaryPluginVersion: String,
    summaryResolvedAtEpochMillis: Long,
) = CatalogEntryEntity(
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
    coverUrl = coverUrl?.takeIf(String::isNotBlank),
    sourceUrl = sourceUrl,
    scoreValue = score?.value,
    scoreScale = score?.scale,
    popularityRank = popularityRank,
    publicationStatus = publicationStatus?.name,
    latestUpdateAtEpochMillis = latestUpdate?.atEpochMillis,
    latestUpdateReleaseLabel = latestUpdate?.releaseLabel,
    pluginVersion = summaryPluginVersion,
    fetchedAtEpochMillis = summaryResolvedAtEpochMillis,
    fullPluginVersion = null,
    fullResolvedAtEpochMillis = null,
)

internal fun CatalogEntry.toDetailsEntity(
    pluginVersion: String,
    resolvedAtEpochMillis: Long,
) = CatalogEntryEntity(
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
    coverUrl = coverUrl?.takeIf(String::isNotBlank),
    sourceUrl = sourceUrl,
    scoreValue = score?.value,
    scoreScale = score?.scale,
    popularityRank = popularityRank,
    publicationStatus = publicationStatus?.name,
    latestUpdateAtEpochMillis = latestUpdate?.atEpochMillis,
    latestUpdateReleaseLabel = latestUpdate?.releaseLabel,
    pluginVersion = pluginVersion,
    fetchedAtEpochMillis = resolvedAtEpochMillis,
    fullPluginVersion = pluginVersion,
    fullResolvedAtEpochMillis = resolvedAtEpochMillis,
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
                kind = CatalogFeedKind.valueOf(section.feedKind),
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
    publicationStatus = publicationStatus?.let { PublicationStatus.valueOf(it) },
    latestUpdate = latestUpdateAtEpochMillis?.let { atEpochMillis ->
        CatalogLatestUpdate(atEpochMillis, latestUpdateReleaseLabel)
    },
)

// These aliases isolate schema-8 legacy field names at the Room entity boundary.
// The SQL columns remain `plugin_version` / `fetched_at_epoch_millis`; lifecycle code
// must treat them as Summary provenance, not generic Details freshness.
internal val CatalogEntryEntity.summaryPluginVersion: String
    get() = pluginVersion

internal val CatalogEntryEntity.summaryResolvedAtEpochMillis: Long
    get() = fetchedAtEpochMillis

internal fun CatalogEntryEntity.toMetadataSnapshot(): CatalogMetadataSnapshot = CatalogMetadataSnapshot(
    entry = toModel(),
    summary = CatalogMetadataStamp(summaryPluginVersion, summaryResolvedAtEpochMillis),
    full = metadataStamp(fullPluginVersion, fullResolvedAtEpochMillis),
)

private fun metadataStamp(pluginVersion: String?, resolvedAtEpochMillis: Long?): CatalogMetadataStamp? = when {
    pluginVersion == null && resolvedAtEpochMillis == null -> null
    else -> CatalogMetadataStamp(
        pluginVersion = requireNotNull(pluginVersion),
        resolvedAtEpochMillis = requireNotNull(resolvedAtEpochMillis),
    )
}

internal fun List<CatalogEntryEntity>.toCandidate(story: StoryEntity): CatalogMatchCandidate {
    require(isNotEmpty())
    val titles = linkedSetOf<String>()
    val authors = linkedSetOf<String>()
    val sourceKeys = linkedSetOf<SourceKey>()
    val evidence = map { entry ->
        titles += entry.title
        titles += entry.aliases
        authors += entry.authors
        sourceKeys += SourceKey(PluginId(entry.pluginId), entry.sourceId)
        CatalogMatchEvidence(
            setOf(entry.title) + entry.aliases,
            entry.authors,
            ContentType.valueOf(entry.contentType),
        )
    }
    return CatalogMatchCandidate(story.toModel(), titles, authors, sourceKeys, evidence)
}
