package app.openstory.database.repository

import app.openstory.database.dao.CatalogDao
import app.openstory.database.entity.CatalogHomeItemEntity
import app.openstory.database.entity.CatalogHomeSectionEntity
import app.openstory.database.entity.CatalogHomeSnapshotEntity
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogSnapshot

internal class CatalogHomeWriter(
    private val catalogDao: CatalogDao,
) {
    suspend fun replace(
        snapshot: CatalogSnapshot,
        refreshedAtEpochMillis: Long,
        savedEntries: Map<String, CatalogEntryWithStory>,
    ) {
        catalogDao.upsertHomeSnapshot(
            CatalogHomeSnapshotEntity(
                catalogPluginId = snapshot.pluginId.value,
                pluginVersion = snapshot.pluginVersion,
                refreshedAtEpochMillis = refreshedAtEpochMillis,
            ),
        )
        catalogDao.deleteHomeSections(snapshot.pluginId.value)

        val sections = snapshot.toSectionEntities()
        if (sections.isNotEmpty()) {
            catalogDao.insertHomeSections(sections)
        }

        val items = snapshot.toItemEntities(savedEntries)
        if (items.isNotEmpty()) {
            catalogDao.insertHomeItems(items)
        }
    }

    private fun CatalogSnapshot.toSectionEntities():
        List<CatalogHomeSectionEntity> =
        sections.mapIndexed { position, section ->
            CatalogHomeSectionEntity(
                catalogPluginId = pluginId.value,
                sectionSourceId = section.sourceId,
                title = section.title,
                sectionPosition = position,
            )
        }

    private fun CatalogSnapshot.toItemEntities(
        savedEntries: Map<String, CatalogEntryWithStory>,
    ): List<CatalogHomeItemEntity> =
        sections.flatMap { section ->
            section.items.mapIndexed { position, item ->
                CatalogHomeItemEntity(
                    catalogPluginId = pluginId.value,
                    sectionSourceId = section.sourceId,
                    catalogEntryId =
                        requireNotNull(savedEntries[item.sourceId])
                            .entry.id.value,
                    itemPosition = position,
                )
            }
        }
}
