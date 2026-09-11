package app.openstory.catalog.feature.presentation

import app.openstory.catalog.domain.model.CatalogMediaType

internal val CatalogMediaType.productLabel: String
    get() = when (this) {
        CatalogMediaType.MANGA -> "Manga"
        CatalogMediaType.LIGHT_NOVEL -> "Light Novel"
    }

internal val CatalogMediaType.productEyebrowLabel: String
    get() = when (this) {
        CatalogMediaType.MANGA -> "MANGA"
        CatalogMediaType.LIGHT_NOVEL -> "LIGHT NOVEL"
    }
