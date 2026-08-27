package app.openstory.catalog.ui.state

data class RefreshState(
    val inProgress: Boolean = false,
    val failure: CatalogUiFailure? = null,
)

internal fun RefreshState.startAttempt(): RefreshState =
    copy(inProgress = true, failure = null)

internal fun RefreshState.completeSuccess(): RefreshState = RefreshState()

internal fun RefreshState.completeFailure(failure: CatalogUiFailure): RefreshState =
    RefreshState(inProgress = false, failure = failure)
