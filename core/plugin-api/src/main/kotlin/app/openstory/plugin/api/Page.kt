package app.openstory.plugin.api

import kotlinx.serialization.Serializable

interface PageItem {
    val stableKey: String
}

@Serializable
data class Page<T : PageItem>(
    val items: List<T>,
    val nextToken: String?,
) {
    init {
        require(items.size <= MAX_ITEMS) {
            "Page must not contain more than $MAX_ITEMS items."
        }

        require(items.all { it.stableKey.isNotBlank() }) {
            "Page items must have non-blank stable source IDs."
        }

        require(items.map { it.stableKey }.distinct().size == items.size) {
            "Page items must have unique stable source IDs."
        }

        require(nextToken == null || nextToken.isNotBlank()) {
            "Continuation token must be null or non-blank."
        }
    }

    private companion object {
        const val MAX_ITEMS = 100
    }
}
