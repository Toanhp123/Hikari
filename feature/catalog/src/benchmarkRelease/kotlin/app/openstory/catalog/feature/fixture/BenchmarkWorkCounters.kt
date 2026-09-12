package app.openstory.catalog.feature.fixture

import java.util.concurrent.atomic.AtomicInteger

internal object BenchmarkWorkCounters {
    private val activeCoverDemands = AtomicInteger()
    private val activeCoverJobs = AtomicInteger()
    private val peakActiveCoverJobs = AtomicInteger()
    private val activeRuntimeWork = AtomicInteger()
    private val activeStoryPins = AtomicInteger()
    private val maxDiscoverTouchedStoryIds = AtomicInteger()
    private val maxReleaseTouchedStoryIds = AtomicInteger()

    fun reset() {
        activeCoverDemands.set(0)
        activeCoverJobs.set(0)
        peakActiveCoverJobs.set(0)
        activeRuntimeWork.set(0)
        activeStoryPins.set(0)
        maxDiscoverTouchedStoryIds.set(0)
        maxReleaseTouchedStoryIds.set(0)
    }

    fun snapshot() = BenchmarkWorkDiagnosticSnapshot(
        activeCoverDemands.get(),
        activeCoverJobs.get(),
        peakActiveCoverJobs.get(),
        activeRuntimeWork.get(),
        activeStoryPins.get(),
        maxDiscoverTouchedStoryIds.get(),
        maxReleaseTouchedStoryIds.get(),
    )

    fun recordCoverDemandStarted() { activeCoverDemands.incrementAndGet() }
    fun recordCoverDemandStopped() { activeCoverDemands.decrementAndGet() }
    fun recordRuntimeWorkCount(activeWork: Int) { activeRuntimeWork.set(activeWork) }
    fun recordStoryPinCount(activePins: Int) { activeStoryPins.set(activePins) }
    fun recordCoverJobCount(activeJobs: Int) {
        activeCoverJobs.set(activeJobs)
        peakActiveCoverJobs.updateAndGet { previous -> maxOf(previous, activeJobs) }
    }
    fun recordDiscoverMutationTouched(touchedStories: Int) {
        maxDiscoverTouchedStoryIds.updateAndGet { previous -> maxOf(previous, touchedStories) }
    }
    fun recordStoryReleaseMutationTouched(touchedStories: Int) {
        maxReleaseTouchedStoryIds.updateAndGet { previous -> maxOf(previous, touchedStories) }
    }
}
