package app.openstory.catalog.domain.identity

@JvmInline
value class CatalogSourceKey(val value: String) {
    init {
        CatalogIdentifierRules.requireValidSourceKey(value)
    }
}
