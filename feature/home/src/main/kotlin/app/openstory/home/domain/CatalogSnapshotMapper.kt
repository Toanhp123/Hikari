package app.openstory.home.domain

import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CatalogSnapshotSection
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.host.HostedPlugin

class CatalogSnapshotMapper {
    fun map(
        hosted: HostedPlugin<CatalogPlugin>,
        sections: List<CatalogSection>,
    ): CatalogSnapshot = CatalogSnapshot(
        pluginId = hosted.id,
        pluginVersion = hosted.version,
        sections = sections.map { section ->
            CatalogSnapshotSection(
                sourceId = section.sourceId,
                title = section.title,
                items = section.items.map { card ->
                    CatalogSnapshotItem(
                        sourceId = card.sourceId,
                        title = card.title,
                        contentType = card.contentType,
                        authors = card.authors,
                        coverReference = card.image?.url,
                        score = card.score?.value,
                        scoreScale = card.score?.scale,
                    )
                },
            )
        },
    )
}
