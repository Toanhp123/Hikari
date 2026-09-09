package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.limits.requireUtf8Bound

data class AcquisitionProvenance(
    val catalogSourceKey: CatalogSourceKey,
    val sourceVersion: String,
    val acquiredAtEpochMs: Long,
) {
    init {
        requireUtf8Bound(sourceVersion, CatalogInputLimits.SOURCE_VERSION_UTF8_BYTES, true)
        require(acquiredAtEpochMs >= 0)
    }
}
