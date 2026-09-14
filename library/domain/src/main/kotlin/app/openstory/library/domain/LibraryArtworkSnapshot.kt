package app.openstory.library.domain

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator

data class LibraryArtworkSnapshot(
    val coverAssetKey: CoverAssetKey,
    val coverLocator: CoverLocator,
)
