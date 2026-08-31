package app.openstory.catalog.ui.state

data class CatalogUiFailure(
    val code: String,
    val retryable: Boolean,
)
