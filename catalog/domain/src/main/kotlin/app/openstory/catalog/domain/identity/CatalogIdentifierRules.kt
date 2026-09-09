package app.openstory.catalog.domain.identity

import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.limits.requireUtf8Bound

internal object CatalogIdentifierRules {
    fun requireValidSourceKey(value: String): ByteArray = requireUtf8Bound(
        value = value,
        maximumBytes = CatalogInputLimits.SOURCE_KEY_UTF8_BYTES,
        requireNonBlank = true,
    )

    fun requireValidSourceStoryId(value: String): ByteArray = requireUtf8Bound(
        value = value,
        maximumBytes = CatalogInputLimits.SOURCE_STORY_ID_UTF8_BYTES,
        requireNonBlank = true,
    )
}
