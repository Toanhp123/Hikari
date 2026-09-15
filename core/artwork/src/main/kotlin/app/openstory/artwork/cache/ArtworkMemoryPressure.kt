package app.openstory.artwork.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import java.util.concurrent.atomic.AtomicBoolean

interface ArtworkCallbacksRegistry {
    fun register(callbacks: ComponentCallbacks2)

    fun unregister(callbacks: ComponentCallbacks2)
}

internal class AndroidArtworkCallbacksRegistry(
    private val context: Context,
) : ArtworkCallbacksRegistry {
    override fun register(callbacks: ComponentCallbacks2) = context.registerComponentCallbacks(callbacks)

    override fun unregister(callbacks: ComponentCallbacks2) = context.unregisterComponentCallbacks(callbacks)
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class ArtworkMemoryPressureController(
    private val decodedMemoryCache: ArtworkDecodedMemoryCache,
    private val registry: ArtworkCallbacksRegistry,
) : ComponentCallbacks2, AutoCloseable {
    private val closed = AtomicBoolean(false)

    init {
        registry.register(this)
    }

    override fun onLowMemory() = decodedMemoryCache.clear()

    override fun onTrimMemory(level: Int) {
        val shouldClear = level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN &&
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (shouldClear) decodedMemoryCache.clear()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun close() {
        if (closed.compareAndSet(false, true)) registry.unregister(this)
    }
}
