package app.openstory.catalog.feature

import java.util.concurrent.atomic.AtomicInteger

object CatalogDebugDiagnostics {
    private val activationStarts = AtomicInteger()
    private val storageReadyEvents = AtomicInteger()
    private val acquisitionStarts = AtomicInteger()
    private val activeDiscoverCollectors = AtomicInteger()
    private val activeCoverDemands = AtomicInteger()
    private val imageSessionInitializations = AtomicInteger()
    private val imageSessionCloses = AtomicInteger()
    private val runtimeSessionCloses = AtomicInteger()
    private val closeOrder = AtomicInteger()
    private val imageClosedAt = AtomicInteger()
    private val runtimeClosedAt = AtomicInteger()

    @JvmStatic
    fun reset() {
        activationStarts.set(0)
        storageReadyEvents.set(0)
        acquisitionStarts.set(0)
        activeDiscoverCollectors.set(0)
        activeCoverDemands.set(0)
        imageSessionInitializations.set(0)
        imageSessionCloses.set(0)
        runtimeSessionCloses.set(0)
        closeOrder.set(0)
        imageClosedAt.set(0)
        runtimeClosedAt.set(0)
    }

    @JvmStatic
    fun activationStartCount(): Int = activationStarts.get()

    @JvmStatic
    fun storageReadyCount(): Int = storageReadyEvents.get()

    @JvmStatic
    fun acquisitionStartCount(): Int = acquisitionStarts.get()

    @JvmStatic
    fun activeDiscoverCollectorCount(): Int = activeDiscoverCollectors.get()

    @JvmStatic
    fun activeCoverDemandCount(): Int = activeCoverDemands.get()

    @JvmStatic
    fun imageLoaderInitializationCount(): Int = imageSessionInitializations.get()

    @JvmStatic
    fun imageSessionInitializationCount(): Int = imageSessionInitializations.get()

    @JvmStatic
    fun imageSessionCloseCount(): Int = imageSessionCloses.get()

    @JvmStatic
    fun runtimeSessionCloseCount(): Int = runtimeSessionCloses.get()

    @JvmStatic
    fun imageSessionClosedOrder(): Int = imageClosedAt.get()

    @JvmStatic
    fun runtimeSessionClosedOrder(): Int = runtimeClosedAt.get()

    internal fun recordActivationStarted() {
        activationStarts.incrementAndGet()
    }

    internal fun recordStorageReady() {
        storageReadyEvents.incrementAndGet()
    }

    internal fun recordAcquisitionStarted() {
        acquisitionStarts.incrementAndGet()
    }

    internal fun recordDiscoverCollectorStarted() {
        activeDiscoverCollectors.incrementAndGet()
    }

    internal fun recordDiscoverCollectorStopped() {
        activeDiscoverCollectors.decrementAndGet()
    }

    internal fun recordCoverDemandStarted() {
        activeCoverDemands.incrementAndGet()
    }

    internal fun recordCoverDemandStopped() {
        activeCoverDemands.decrementAndGet()
    }

    internal fun recordImageSessionInitialized() {
        imageSessionInitializations.incrementAndGet()
    }

    internal fun recordImageSessionClosed() {
        imageSessionCloses.incrementAndGet()
        imageClosedAt.compareAndSet(0, closeOrder.incrementAndGet())
    }

    internal fun recordRuntimeSessionClosed() {
        runtimeSessionCloses.incrementAndGet()
        runtimeClosedAt.compareAndSet(0, closeOrder.incrementAndGet())
    }
}
