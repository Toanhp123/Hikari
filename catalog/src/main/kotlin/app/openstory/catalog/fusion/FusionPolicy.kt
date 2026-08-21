package app.openstory.catalog.fusion

import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel

const val FUSION_POLICY_VERSION: Int = 1
const val PRIMARY_SELECTION_POLICY_VERSION: Int = 1

private const val ACTIVE_RANK = 5
private const val STALE_USABILITY_RANK = 4
private const val TEMPORARILY_UNAVAILABLE_RANK = 3
private const val UNAVAILABLE_RANK = 2
private const val RETIRED_RANK = 1
private const val FULL_METADATA_RANK = 2
private const val SUMMARY_METADATA_RANK = 1
private const val FRESH_RANK = 3
private const val STALE_FRESHNESS_RANK = 2
private const val UNKNOWN_FRESHNESS_RANK = 1

enum class CatalogSourceUsability {
    ACTIVE,
    STALE,
    TEMPORARILY_UNAVAILABLE,
    UNAVAILABLE,
    RETIRED,
}

enum class CatalogSourceFreshness {
    FRESH,
    STALE,
    UNKNOWN,
}

data class FusionSource(
    val record: CatalogSourceRecord,
    val usability: CatalogSourceUsability,
    val freshness: CatalogSourceFreshness,
) {
    val sourceKey: SourceKey get() = record.key
    val metadataLevel: CatalogMetadataLevel
        get() = if (record.full == null) CatalogMetadataLevel.Summary else CatalogMetadataLevel.Full

    fun primaryQuality(): PrimaryQuality = PrimaryQuality(
        usability = usability,
        metadataLevel = metadataLevel,
        freshness = freshness,
        primaryFieldCoverage = primaryFieldCoverage(record),
        stableSourceKey = sourceKey,
    )
}

data class PrimaryQuality(
    val usability: CatalogSourceUsability,
    val metadataLevel: CatalogMetadataLevel,
    val freshness: CatalogSourceFreshness,
    val primaryFieldCoverage: Int,
    val stableSourceKey: SourceKey,
)

fun CatalogSourceUsability.rank(): Int = when (this) {
    CatalogSourceUsability.ACTIVE -> ACTIVE_RANK
    CatalogSourceUsability.STALE -> STALE_USABILITY_RANK
    CatalogSourceUsability.TEMPORARILY_UNAVAILABLE -> TEMPORARILY_UNAVAILABLE_RANK
    CatalogSourceUsability.UNAVAILABLE -> UNAVAILABLE_RANK
    CatalogSourceUsability.RETIRED -> RETIRED_RANK
}

fun CatalogMetadataLevel.rank(): Int = when (this) {
    CatalogMetadataLevel.Full -> FULL_METADATA_RANK
    CatalogMetadataLevel.Summary -> SUMMARY_METADATA_RANK
}

fun CatalogSourceFreshness.rank(): Int = when (this) {
    CatalogSourceFreshness.FRESH -> FRESH_RANK
    CatalogSourceFreshness.STALE -> STALE_FRESHNESS_RANK
    CatalogSourceFreshness.UNKNOWN -> UNKNOWN_FRESHNESS_RANK
}

val primaryQualityComparator: Comparator<FusionSource> = Comparator { left, right ->
    compareValuesBy(
        left,
        right,
        { -it.usability.rank() },
        { -it.metadataLevel.rank() },
        { -it.freshness.rank() },
        { -it.primaryQuality().primaryFieldCoverage },
        { it.sourceKey.pluginId.value },
        { it.sourceKey.sourceId },
    )
}

fun FusionSource.isEffectivePrimaryEligible(): Boolean = usability == CatalogSourceUsability.ACTIVE ||
    usability == CatalogSourceUsability.STALE

fun FusionSource.isUsableEvidence(): Boolean = usability == CatalogSourceUsability.ACTIVE ||
    usability == CatalogSourceUsability.STALE ||
    usability == CatalogSourceUsability.TEMPORARILY_UNAVAILABLE

private fun primaryFieldCoverage(record: CatalogSourceRecord): Int = with(record.entry) {
    listOf(
        !description.isNullOrBlank(),
        !coverUrl.isNullOrBlank(),
        !sourceUrl.isNullOrBlank(),
        authors.isNotEmpty(),
        aliases.isNotEmpty(),
        genres.isNotEmpty(),
        publicationStatus != null,
        latestUpdate != null,
        score != null,
    ).count { it }
}
