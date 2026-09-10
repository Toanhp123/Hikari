package app.openstory.catalog.feature.assets

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import java.util.concurrent.atomic.AtomicBoolean

internal interface CatalogImageCallbacksRegistry {
    fun register(callbacks: ComponentCallbacks2)

    fun unregister(callbacks: ComponentCallbacks2)
}

internal class AndroidCatalogImageCallbacksRegistry(
    private val context: Context,
) : CatalogImageCallbacksRegistry {
    override fun register(callbacks: ComponentCallbacks2) {
        context.registerComponentCallbacks(callbacks)
    }

    override fun unregister(callbacks: ComponentCallbacks2) {
        context.unregisterComponentCallbacks(callbacks)
    }
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class CatalogImageMemoryPressureController(
    private val decodedMemoryCache: DecodedCoverMemoryCache,
    private val registry: CatalogImageCallbacksRegistry,
) : ComponentCallbacks2, AutoCloseable {
    private val closed = AtomicBoolean(false)

    init {
        registry.register(this)
    }

    override fun onLowMemory() {
        decodedMemoryCache.clear()
    }

    override fun onTrimMemory(level: Int) {
        if (
            level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN &&
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        ) {
            decodedMemoryCache.clear()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun close() {
        if (closed.compareAndSet(false, true)) registry.unregister(this)
    }
}
