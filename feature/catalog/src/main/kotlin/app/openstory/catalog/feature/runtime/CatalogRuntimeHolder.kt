package app.openstory.catalog.feature.runtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.CatalogCompositionDiagnostics
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeHost
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CatalogRuntimeHolder(
    val runtime: CatalogRuntimeHost,
    private val diagnostics: CatalogCompositionDiagnostics,
) : ViewModel() {
    private val activationMutex = Mutex()
    private val activatedSources = mutableSetOf<CatalogSourceKey>()
    private var storageReadyReported = false

    suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation =
        activationMutex.withLock {
            if (sourceKey !in activatedSources) diagnostics.activationStarted()
            runtime.activate(sourceKey).also { activation ->
                if (activation is CatalogCapabilityActivation.Available && !storageReadyReported) {
                    storageReadyReported = true
                    diagnostics.storageReady()
                }
                activatedSources += sourceKey
            }
        }

    fun discoverRuntime(mediaType: CatalogMediaType): DiscoverRuntime {
        return createFrozenDiscoverRuntime(
            mediaType = mediaType,
            authorityResolver = runtime.authorityResolver(),
            activateCatalog = ::activate,
            diagnostics = diagnostics,
        )
    }

    override fun onCleared() {
        runtime.close()
        diagnostics.runtimeSessionClosed()
    }

    companion object {
        fun factory(
            diagnostics: CatalogCompositionDiagnostics,
            createRuntime: () -> CatalogRuntimeHost,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CatalogRuntimeHolder::class.java))
                    return CatalogRuntimeHolder(createRuntime(), diagnostics) as T
                }
            }
    }
}
