package app.openstory.catalog.identity

enum class ExternalIdentifierScope { WORK, PUBLICATION, EDITION, PROVIDER_RECORD }

data class ExternalIdentifier(
    val namespace: String,
    val value: String,
    val scope: ExternalIdentifierScope,
) {
    init {
        require(namespace.isNotBlank()) { "External identifier namespace must not be blank" }
        require(value.isNotBlank()) { "External identifier value must not be blank" }
        require(namespace.length <= MAX_NAMESPACE_LENGTH) { "External identifier namespace is too long" }
        require(value.length <= MAX_VALUE_LENGTH) { "External identifier value is too long" }
        require(namespace.none(Char::isISOControl)) {
            "External identifier namespace must not contain control characters"
        }
        require(value.none(Char::isISOControl)) {
            "External identifier value must not contain control characters"
        }
    }

    private companion object {
        const val MAX_NAMESPACE_LENGTH = 128
        const val MAX_VALUE_LENGTH = 256
    }
}
