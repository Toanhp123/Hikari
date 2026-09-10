package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.model.CatalogSectionKind

internal sealed interface DiscoverViewportRow {
    val stableKey: String

    data class Header(val kind: CatalogSectionKind) : DiscoverViewportRow {
        override val stableKey = "section-header:${kind.name}"
    }

    data class Carousel(
        val kind: CatalogSectionKind,
        val cards: List<DiscoverCardUi>,
    ) : DiscoverViewportRow {
        override val stableKey = "section-carousel:${kind.name}"
    }

    data class VerticalCard(
        val kind: CatalogSectionKind,
        val index: Int,
        val card: DiscoverCardUi,
    ) : DiscoverViewportRow {
        override val stableKey = "section-card:${kind.name}:${card.ref.storyId.value}"
    }
}
