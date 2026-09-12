package app.openstory.catalog.feature.fixture

import java.util.concurrent.atomic.AtomicInteger

internal object BenchmarkLifecycleCounters {
    private val activationStarts = AtomicInteger()
    private val storageReadyEvents = AtomicInteger()
    private val acquisitionStarts = AtomicInteger()
    private val activeDiscoverCollectors = AtomicInteger()
    private val activeStoryCollectors = AtomicInteger()
    private val activeImageSessions = AtomicInteger()
    private val runtimeSessionCloses = AtomicInteger()

    fun reset() {
        activationStarts.set(0)
        storageReadyEvents.set(0)
        acquisitionStarts.set(0)
        activeDiscoverCollectors.set(0)
        activeStoryCollectors.set(0)
        activeImageSessions.set(0)
        runtimeSessionCloses.set(0)
    }

    fun snapshot() = BenchmarkLifecycleDiagnosticSnapshot(
        activationStarts.get(),
        storageReadyEvents.get(),
        acquisitionStarts.get(),
        activeDiscoverCollectors.get(),
        activeStoryCollectors.get(),
        activeImageSessions.get(),
        runtimeSessionCloses.get(),
    )

    fun recordActivationStarted() { activationStarts.incrementAndGet() }
    fun recordStorageReady() { storageReadyEvents.incrementAndGet() }
    fun recordAcquisitionStarted() { acquisitionStarts.incrementAndGet() }
    fun recordDiscoverCollectorStarted() { activeDiscoverCollectors.incrementAndGet() }
    fun recordDiscoverCollectorStopped() { activeDiscoverCollectors.decrementAndGet() }
    fun recordStoryCollectorStarted() { activeStoryCollectors.incrementAndGet() }
    fun recordStoryCollectorStopped() { activeStoryCollectors.decrementAndGet() }
    fun recordImageSessionInitialized() { activeImageSessions.incrementAndGet() }
    fun recordImageSessionClosed() { activeImageSessions.decrementAndGet() }
    fun recordRuntimeSessionClosed() { runtimeSessionCloses.incrementAndGet() }
}
