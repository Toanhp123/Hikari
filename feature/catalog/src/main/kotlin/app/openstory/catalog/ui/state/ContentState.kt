package app.openstory.catalog.ui.state

sealed interface ContentState<out T> {
    data object Pending : ContentState<Nothing>

    data class Ready<T>(
        val value: T,
    ) : ContentState<T>

    data class Failed(
        val failure: CatalogUiFailure,
    ) : ContentState<Nothing>
}
