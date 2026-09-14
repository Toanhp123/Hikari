package app.openstory.library.feature

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.library.domain.LibraryEntry

data class LibraryStoryPosterUi(
    val ref: StorySourceRef,
    val originMediaContext: CatalogMediaType,
    val savedAtEpochMs: Long,
    val title: String,
    val coverAssetKey: CoverAssetKey?,
    val coverLocator: CoverLocator?,
    val supportingText: String?,
)

internal fun LibraryEntry.toPosterUi() = LibraryStoryPosterUi(
    ref = ref,
    originMediaContext = originMediaContext,
    savedAtEpochMs = savedAtEpochMs,
    title = snapshot.title,
    coverAssetKey = snapshot.artwork?.coverAssetKey,
    coverLocator = snapshot.artwork?.coverLocator,
    supportingText = snapshot.supportingText,
)
