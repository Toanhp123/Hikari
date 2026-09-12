package app.openstory.catalog.feature.fixture

public data class BenchmarkCatalogDiagnosticSnapshot(
    val lifecycle: BenchmarkLifecycleDiagnosticSnapshot = BenchmarkLifecycleDiagnosticSnapshot(),
    val work: BenchmarkWorkDiagnosticSnapshot = BenchmarkWorkDiagnosticSnapshot(),
    val queries: BenchmarkQueryDiagnosticSnapshot = BenchmarkQueryDiagnosticSnapshot(),
    val cache: BenchmarkCacheDiagnosticSnapshot = BenchmarkCacheDiagnosticSnapshot(),
    val decode: BenchmarkDecodeDiagnosticSnapshot = BenchmarkDecodeDiagnosticSnapshot(),
) {
    val activationStarts get() = lifecycle.activationStarts
    val storageReadyEvents get() = lifecycle.storageReadyEvents
    val acquisitionStarts get() = lifecycle.acquisitionStarts
    val activeDiscoverCollectors get() = lifecycle.activeDiscoverCollectors
    val activeStoryCollectors get() = lifecycle.activeStoryCollectors
    val activeImageSessions get() = lifecycle.activeImageSessions
    val runtimeSessionCloses get() = lifecycle.runtimeSessionCloses
    val activeCoverDemands get() = work.activeCoverDemands
    val activeCoverJobs get() = work.activeCoverJobs
    val peakActiveCoverJobs get() = work.peakActiveCoverJobs
    val activeRuntimeWork get() = work.activeRuntimeWork
    val activeStoryPins get() = work.activeStoryPins
    val maxDiscoverTouchedStoryIds get() = work.maxDiscoverTouchedStoryIds
    val maxReleaseTouchedStoryIds get() = work.maxReleaseTouchedStoryIds
    val transportRequests get() = queries.transportRequests
    val discoverObservationQueries get() = queries.discoverObservationQueries
    val storyObservationQueries get() = queries.storyObservationQueries
    val decodedMemoryBytes get() = cache.decodedMemoryBytes
    val peakDecodedMemoryBytes get() = cache.peakDecodedMemoryBytes
    val encodedDiskBytes get() = cache.encodedDiskBytes
    val peakEncodedDiskBytes get() = cache.peakEncodedDiskBytes
    val successfulDecodes get() = decode.successfulDecodes
    val maxDecodeTargetWidth get() = decode.maxDecodeTargetWidth
    val maxDecodeTargetHeight get() = decode.maxDecodeTargetHeight
    val originalSizeDecodes get() = decode.originalSizeDecodes
    val mainThreadDecodes get() = decode.mainThreadDecodes
}

public data class BenchmarkLifecycleDiagnosticSnapshot(
    val activationStarts: Int = 0,
    val storageReadyEvents: Int = 0,
    val acquisitionStarts: Int = 0,
    val activeDiscoverCollectors: Int = 0,
    val activeStoryCollectors: Int = 0,
    val activeImageSessions: Int = 0,
    val runtimeSessionCloses: Int = 0,
)

public data class BenchmarkWorkDiagnosticSnapshot(
    val activeCoverDemands: Int = 0,
    val activeCoverJobs: Int = 0,
    val peakActiveCoverJobs: Int = 0,
    val activeRuntimeWork: Int = 0,
    val activeStoryPins: Int = 0,
    val maxDiscoverTouchedStoryIds: Int = 0,
    val maxReleaseTouchedStoryIds: Int = 0,
)

public data class BenchmarkQueryDiagnosticSnapshot(
    val transportRequests: Int = 0,
    val discoverObservationQueries: Int = 0,
    val storyObservationQueries: Int = 0,
)

public data class BenchmarkCacheDiagnosticSnapshot(
    val decodedMemoryBytes: Long = 0,
    val peakDecodedMemoryBytes: Long = 0,
    val encodedDiskBytes: Long = 0,
    val peakEncodedDiskBytes: Long = 0,
)

public data class BenchmarkDecodeDiagnosticSnapshot(
    val successfulDecodes: Int = 0,
    val maxDecodeTargetWidth: Int = 0,
    val maxDecodeTargetHeight: Int = 0,
    val originalSizeDecodes: Int = 0,
    val mainThreadDecodes: Int = 0,
)

public data class BenchmarkCatalogPreparationEvidence(
    val agedUnrelatedRows: Int = 0,
    val maxDiscoverTouchedStoryIds: Int = 0,
    val maxReleaseTouchedStoryIds: Int = 0,
    val orphanRetentionRows: Int = 0,
    val pinPruneRetentionRows: Int = 0,
    val pathologicalImageRejections: Int = 0,
)

public object BenchmarkCatalogDiagnostics {
    @JvmStatic
    public fun reset() {
        BenchmarkLifecycleCounters.reset()
        BenchmarkWorkCounters.reset()
        BenchmarkQueryCounters.reset()
        BenchmarkImageCounters.reset()
        BenchmarkCoverFixture.resetTransportRequests()
    }

    @JvmStatic
    public fun snapshot(): BenchmarkCatalogDiagnosticSnapshot = BenchmarkCatalogDiagnosticSnapshot(
        lifecycle = BenchmarkLifecycleCounters.snapshot(),
        work = BenchmarkWorkCounters.snapshot(),
        queries = BenchmarkQueryCounters.snapshot(BenchmarkCoverFixture.transportRequestCount()),
        cache = BenchmarkImageCounters.cacheSnapshot(),
        decode = BenchmarkImageCounters.decodeSnapshot(),
    )
}
