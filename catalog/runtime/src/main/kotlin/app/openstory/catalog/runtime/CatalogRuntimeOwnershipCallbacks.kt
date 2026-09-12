package app.openstory.catalog.runtime

import java.util.concurrent.CancellationException

class CatalogRuntimeOwnershipCallbacks(
    onActiveWorkChanged: (Int) -> Unit = {},
    onActiveStoryPinsChanged: (Int) -> Unit = {},
    onDiscoverMutationTouched: (Int) -> Unit = {},
    onStoryReleaseMutationTouched: (Int) -> Unit = {},
) {
    val onActiveWorkChanged = onActiveWorkChanged.failureIsolated()
    val onActiveStoryPinsChanged = onActiveStoryPinsChanged.failureIsolated()
    val onDiscoverMutationTouched = onDiscoverMutationTouched.failureIsolated()
    val onStoryReleaseMutationTouched = onStoryReleaseMutationTouched.failureIsolated()
}

private fun ((Int) -> Unit).failureIsolated(): (Int) -> Unit = { value ->
    runCatching { invoke(value) }.exceptionOrNull()?.rethrowFatalCallbackFailure()
}

private fun Throwable.rethrowFatalCallbackFailure() {
    if (this is CancellationException || this is Error) throw this
}
