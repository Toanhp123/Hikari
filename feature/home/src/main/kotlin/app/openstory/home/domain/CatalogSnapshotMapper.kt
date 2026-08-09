package app.openstory.home.domain

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeCatalogCard
import app.openstory.home.model.HomeCatalogSection

class CatalogSnapshotMapper {
    internal fun map(snapshot: CatalogHomeSnapshot): HomeCatalog = HomeCatalog(
        pluginId = snapshot.pluginId,
        pluginVersion = snapshot.pluginVersion,
        refreshedAtEpochMillis = snapshot.refreshedAtEpochMillis,
        sections = snapshot.sections.map { section ->
            HomeCatalogSection(
                sourceId = section.sourceId,
                title = section.title,
                items = section.items.map { entry ->
                    HomeCatalogCard(
                        storyId = entry.storyId,
                        pluginId = entry.pluginId,
                        pluginVersion = snapshot.pluginVersion,
                        sourceId = entry.sourceId,
                        title = entry.title,
                        contentType = entry.contentType,
                        authors = entry.authors,
                        coverReference = entry.coverUrl,
                        score = entry.score?.value,
                        scoreScale = entry.score?.scale,
                        fetchedAtEpochMillis = snapshot.refreshedAtEpochMillis,
                    )
                },
            )
        },
    )
}
