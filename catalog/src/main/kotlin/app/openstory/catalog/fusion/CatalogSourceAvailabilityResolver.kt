package app.openstory.catalog.fusion

import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.source.CatalogSourceRegistry
import javax.inject.Inject

enum class CatalogSourceOperationState {
    AVAILABLE,
    RETRYABLE_FAILURE,
    UNAVAILABLE,
    RETIRED,
}

class CatalogSourceAvailabilityResolver @Inject constructor(
    private val registry: CatalogSourceRegistry,
    private val metadataPolicy: CatalogMetadataPolicy,
) {
    suspend fun resolve(
        record: CatalogSourceRecord,
        operationState: CatalogSourceOperationState = CatalogSourceOperationState.AVAILABLE,
    ): FusionSource {
        val source = registry.source(record.key.pluginId)
        val freshness = when {
            source == null -> CatalogSourceFreshness.UNKNOWN
            record.full == null -> CatalogSourceFreshness.UNKNOWN
            metadataPolicy.isFresh(
                CatalogMetadataLevel.Full,
                record.full,
                source.version,
            ) -> CatalogSourceFreshness.FRESH
            else -> CatalogSourceFreshness.STALE
        }
        val usability = when (operationState) {
            CatalogSourceOperationState.RETIRED -> CatalogSourceUsability.RETIRED
            CatalogSourceOperationState.UNAVAILABLE -> CatalogSourceUsability.UNAVAILABLE
            CatalogSourceOperationState.RETRYABLE_FAILURE -> CatalogSourceUsability.TEMPORARILY_UNAVAILABLE
            CatalogSourceOperationState.AVAILABLE -> when {
                source == null -> CatalogSourceUsability.UNAVAILABLE
                freshness == CatalogSourceFreshness.STALE -> CatalogSourceUsability.STALE
                else -> CatalogSourceUsability.ACTIVE
            }
        }
        return FusionSource(record, usability, freshness)
    }

    suspend fun resolve(records: List<CatalogSourceRecord>): List<FusionSource> = records.map { resolve(it) }
}
