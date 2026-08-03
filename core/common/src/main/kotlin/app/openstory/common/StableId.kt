package app.openstory.common

object StableId {
    fun requireValid(value: String): String {
        require(value.isNotBlank()) {
            "Stable ID must not be blank"
        }
        require(value.none { it.isWhitespace() }) {
            "Stable ID must not contain whitespace"
        }

        return value
    }
}
