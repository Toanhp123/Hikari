package app.openstory.library.domain

import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType

data class LibraryEntry(
    val ref: StorySourceRef,
    val originMediaContext: CatalogMediaType,
    val savedAtEpochMs: Long,
    val snapshot: LibraryPresentationSnapshot,
) {
    init {
        require(savedAtEpochMs >= 0L)
        requireAlignedCover(
            ref = ref,
            locator = snapshot.artwork?.coverLocator,
            key = snapshot.artwork?.coverAssetKey,
        )
    }
}
