package app.openstory.catalog.runtime

import org.junit.Test

class CatalogRuntimeOwnershipCallbacksTest {
    @Test
    fun diagnosticFailuresDoNotEscapeIntoRuntimeSemantics() {
        val callbacks = CatalogRuntimeOwnershipCallbacks(
            onActiveWorkChanged = { error("active work diagnostics failed") },
            onActiveStoryPinsChanged = { error("pin diagnostics failed") },
            onDiscoverMutationTouched = { error("discover diagnostics failed") },
            onStoryReleaseMutationTouched = { error("release diagnostics failed") },
        )

        callbacks.onActiveWorkChanged(1)
        callbacks.onActiveStoryPinsChanged(1)
        callbacks.onDiscoverMutationTouched(19)
        callbacks.onStoryReleaseMutationTouched(2)
    }
}
