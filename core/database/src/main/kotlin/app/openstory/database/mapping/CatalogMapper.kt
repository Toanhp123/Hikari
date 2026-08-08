package app.openstory.database.mapping

import app.openstory.database.dao.CatalogEntryWithStoryRow
import app.openstory.database.dao.CatalogHomeRow
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSection
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.PluginId
import app.openstory.model.StoryId

internal fun CatalogEntryWithStoryRow.toDomain():
    CatalogEntryWithStory =
    CatalogEntryWithStory(
        storyId = StoryId(storyId),
        entry = entry.toDomain(),
    )

internal fun CatalogSnapshotItem.toCatalogEntry(
    pluginId: PluginId,
    pluginVersion: String,
    fetchedAtEpochMillis: Long,
    existing: CatalogEntry?,
): CatalogEntry =
    CatalogEntry(
        id =
            existing?.id ?: catalogEntryId(
                pluginId = pluginId,
                sourceId = sourceId,
            ),
        catalogPluginId = pluginId,
        externalStoryId = sourceId,
        sourceUrl = existing?.sourceUrl,
        title = title,
        aliases = existing?.aliases.orEmpty(),
        authors = authors.toSet(),
        description = existing?.description,
        genres = existing?.genres.orEmpty(),
        contentType = contentType,
        languageTags = existing?.languageTags.orEmpty(),
        coverReference = coverReference,
        publicationStatus = existing?.publicationStatus,
        score = score,
        scoreScale = scoreScale,
        popularityRank = existing?.popularityRank,
        pluginVersion = pluginVersion,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )

internal fun CatalogSourceMetadata.toCatalogEntry(
    pluginId: PluginId,
    pluginVersion: String,
    fetchedAtEpochMillis: Long,
    existing: CatalogEntry?,
): CatalogEntry =
    CatalogEntry(
        id =
            existing?.id ?: catalogEntryId(
                pluginId = pluginId,
                sourceId = sourceId,
            ),
        catalogPluginId = pluginId,
        externalStoryId = sourceId,
        sourceUrl = sourceUrl,
        title = title,
        aliases = aliases,
        authors = authors,
        description = description,
        genres = genres,
        contentType = contentType,
        languageTags = languageTags,
        coverReference = coverReference,
        publicationStatus = publicationStatus,
        score = score,
        scoreScale = scoreScale,
        popularityRank = popularityRank,
        pluginVersion = pluginVersion,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )

internal fun catalogEntryId(
    pluginId: PluginId,
    sourceId: String,
): CatalogEntryId =
    CatalogEntryId(
        "catalog:${pluginId.value}:$sourceId",
    )

internal fun List<CatalogHomeRow>.toHomeSnapshots():
    List<CatalogHomeSnapshot> =
    groupBy(CatalogHomeRow::pluginId)
        .toSortedMap()
        .map { (pluginId, pluginRows) ->
            val first = pluginRows.first()
            val sections =
                pluginRows
                    .filter { row -> row.sectionSourceId != null }
                    .groupBy { row ->
                        requireNotNull(row.sectionPosition)
                    }
                    .toSortedMap()
                    .map { (_, sectionRows) ->
                        val section = sectionRows.first()
                        CatalogHomeSection(
                            sourceId = requireNotNull(section.sectionSourceId),
                            title = requireNotNull(section.sectionTitle),
                            items =
                                sectionRows
                                    .filter { row -> row.entry != null }
                                    .sortedBy { row ->
                                        requireNotNull(row.itemPosition)
                                    }
                                    .map { row ->
                                        CatalogEntryWithStory(
                                            storyId =
                                                StoryId(
                                                    requireNotNull(row.storyId),
                                                ),
                                            entry =
                                                requireNotNull(row.entry).toDomain(),
                                        )
                                    },
                        )
                    }

            CatalogHomeSnapshot(
                pluginId = PluginId(pluginId),
                pluginVersion = first.pluginVersion,
                refreshedAtEpochMillis = first.refreshedAtEpochMillis,
                sections = sections,
            )
        }
