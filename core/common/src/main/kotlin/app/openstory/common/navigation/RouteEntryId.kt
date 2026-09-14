package app.openstory.common.navigation

@JvmInline
value class RouteEntryId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "Route entry id must not be blank" }
        require(value.length <= MAX_LENGTH) {
            "Route entry id must not exceed $MAX_LENGTH characters: ${value.length}"
        }
        require(SAFE_PATTERN.matches(value)) {
            "Route entry id contains invalid characters: $value"
        }
    }

    companion object {
        const val MAX_LENGTH = 128
        private val SAFE_PATTERN = Regex("^[A-Za-z0-9_-]+$")

        fun from(wireValue: String): RouteEntryId = RouteEntryId(wireValue)
    }
}
