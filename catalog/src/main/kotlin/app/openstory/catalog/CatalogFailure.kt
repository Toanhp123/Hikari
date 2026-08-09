package app.openstory.catalog

sealed interface CatalogFailure {
    val code: String
    val retryable: Boolean
}

data class CatalogStoreFailure(
    override val code: String,
    override val retryable: Boolean,
) : CatalogFailure
