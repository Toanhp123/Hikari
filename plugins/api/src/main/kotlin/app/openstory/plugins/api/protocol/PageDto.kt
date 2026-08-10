package app.openstory.plugins.api.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val nextToken: String? = null,
) {
    init {
        require(items.size <= MAX_PAGE_ITEMS) { "Page exceeds $MAX_PAGE_ITEMS items" }
        require(nextToken == null || nextToken.isNotBlank()) { "Continuation token must be null or non-blank" }
        require(nextToken == null || nextToken.length <= MAX_TOKEN_LENGTH) { "Continuation token is too long" }
    }

    companion object {
        private const val MAX_PAGE_ITEMS = 200
        private const val MAX_TOKEN_LENGTH = 4096
    }
}
