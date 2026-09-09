package app.openstory.catalog.feature

import java.util.concurrent.atomic.AtomicInteger

object CatalogDebugDiagnostics {
    private val activationStarts = AtomicInteger()
    private val storageReadyEvents = AtomicInteger()
    private val acquisitionStarts = AtomicInteger()

    @JvmStatic
    fun reset() {
        activationStarts.set(0)
        storageReadyEvents.set(0)
        acquisitionStarts.set(0)
    }

    @JvmStatic
    fun activationStartCount(): Int = activationStarts.get()

    @JvmStatic
    fun storageReadyCount(): Int = storageReadyEvents.get()

    @JvmStatic
    fun acquisitionStartCount(): Int = acquisitionStarts.get()

    @JvmStatic
    @Suppress("FunctionOnlyReturningConstant")
    fun imageLoaderInitializationCount(): Int = 0

    internal fun recordActivationStarted() {
        activationStarts.incrementAndGet()
    }

    internal fun recordStorageReady() {
        storageReadyEvents.incrementAndGet()
    }

    internal fun recordAcquisitionStarted() {
        acquisitionStarts.incrementAndGet()
    }
}
