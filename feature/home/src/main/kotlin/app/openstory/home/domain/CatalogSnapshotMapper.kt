package app.openstory.home.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceSection
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CatalogSnapshotSection
import app.openstory.model.ContentType

class CatalogSnapshotMapper {
    internal fun map(
        source: CatalogSource,
        sections: List<SourceSection>,
    ): CatalogSnapshot = CatalogSnapshot(
        pluginId = source.pluginId,
        pluginVersion = source.version,
        sections = sections.map { section ->
            CatalogSnapshotSection(
                sourceId = section.sourceId,
                title = section.title,
                items = section.items.map { card ->
                    CatalogSnapshotItem(
                        sourceId = card.sourceId,
                        title = card.title,
                        contentType = card.contentType.toModel(),
                        authors = card.authors.toList(),
                        coverReference = card.coverUrl,
                        score = card.scoreValue,
                        scoreScale = card.scoreScale,
                    )
                },
            )
        },
    )
}

internal fun SourceContentType.toModel(): ContentType = when (this) {
    SourceContentType.LIGHT_NOVEL -> ContentType.LIGHT_NOVEL
    SourceContentType.WEB_NOVEL -> ContentType.WEB_NOVEL
    SourceContentType.MANGA -> ContentType.MANGA
    SourceContentType.ANIME -> ContentType.ANIME
}
