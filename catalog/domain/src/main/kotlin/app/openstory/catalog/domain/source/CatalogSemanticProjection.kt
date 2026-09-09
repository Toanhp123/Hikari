package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind

object CatalogSemanticProjection {
    fun project(
        catalogSourceKey: CatalogSourceKey,
        kind: CatalogSectionKind,
        items: List<DiscoverAcquisitionItem>,
    ): List<DiscoverAcquisitionItem> {
        val candidates = items.mapIndexed { ordinal, item ->
            SemanticCandidate(
                item = item,
                sourceOrdinal = ordinal,
                storyId = SourceStoryIdV1.derive(SourceStoryKey(catalogSourceKey, item.sourceStoryId)).value,
            )
        }
        return when (kind) {
            CatalogSectionKind.POPULAR -> candidates
            CatalogSectionKind.LATEST_UPDATES -> candidates
                .filter { it.item.latestUpdateEpochMs != null }
                .sortedWith(
                    compareByDescending<SemanticCandidate> { it.item.latestUpdateEpochMs }
                        .thenBy { it.sourceOrdinal }
                        .thenBy { it.storyId },
                )
            CatalogSectionKind.TOP_RATED -> candidates
                .filter { it.item.rating.isEligible() }
                .sortedWith(
                    compareByDescending<SemanticCandidate> { it.item.rating!!.normalizedScore() }
                        .thenBy { it.sourceOrdinal }
                        .thenBy { it.storyId },
                )
        }.take(CatalogSectionCaps.cap(kind)).map(SemanticCandidate::item)
    }
}

private data class SemanticCandidate(
    val item: DiscoverAcquisitionItem,
    val sourceOrdinal: Int,
    val storyId: String,
)

private fun CatalogRating?.isEligible(): Boolean =
    this != null && value.isFinite() && scale.isFinite() && scale > 0.0 && value >= 0.0 && value <= scale

private fun CatalogRating.normalizedScore(): Double = value / scale
