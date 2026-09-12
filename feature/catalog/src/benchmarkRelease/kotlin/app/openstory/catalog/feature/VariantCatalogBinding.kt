package app.openstory.catalog.feature

import android.content.Context
import app.openstory.catalog.domain.asset.SourceAssetPolicy
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.feature.assets.CatalogImageDecodeEvidence
import app.openstory.catalog.feature.assets.CatalogImageLoaderCallbacks
import app.openstory.catalog.feature.assets.RemoteCoverTransport
import app.openstory.catalog.feature.fixture.BenchmarkCoverFixture
import app.openstory.catalog.feature.fixture.BenchmarkImageCounters
import app.openstory.catalog.feature.fixture.BenchmarkLifecycleCounters
import app.openstory.catalog.feature.fixture.BenchmarkQueryCounters
import app.openstory.catalog.feature.fixture.BenchmarkWorkCounters
import app.openstory.catalog.feature.seed.BenchmarkCatalogSource
import app.openstory.catalog.runtime.CatalogRuntimeOwnershipCallbacks
import app.openstory.catalog.runtime.source.CatalogSourceBinding

internal object VariantCatalogBinding : CatalogVariantBinding {
    private val sourceKey = CatalogSourceKey("hikari.benchmark.local")

    override val binding = CatalogSourceBinding(
        catalogSourceKey = sourceKey,
        sourceVersion = "benchmark-seed-v1",
        acquisitionSource = BenchmarkCatalogSource(
            sourceKey,
            onAcquisitionStarted = BenchmarkLifecycleCounters::recordAcquisitionStarted,
        ),
        assetPolicy = SourceAssetPolicy(sourceKey, setOf("covers.hikari.invalid")),
    )

    override fun remoteCoverTransport(context: Context): RemoteCoverTransport =
        BenchmarkCoverFixture.transport(context)

    override val queryListener: (String) -> Unit = BenchmarkQueryCounters::recordSqlQuery

    override val diagnostics = object : CatalogCompositionDiagnostics {
        override fun activationStarted() = BenchmarkLifecycleCounters.recordActivationStarted()
        override fun storageReady() = BenchmarkLifecycleCounters.recordStorageReady()
        override fun discoverCollectorStarted() = BenchmarkLifecycleCounters.recordDiscoverCollectorStarted()
        override fun discoverCollectorStopped() = BenchmarkLifecycleCounters.recordDiscoverCollectorStopped()
        override fun runtimeSessionClosed() = BenchmarkLifecycleCounters.recordRuntimeSessionClosed()
        override fun storyCollectorStarted() = BenchmarkLifecycleCounters.recordStoryCollectorStarted()
        override fun storyCollectorStopped() = BenchmarkLifecycleCounters.recordStoryCollectorStopped()

        override val runtimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(
            onActiveWorkChanged = BenchmarkWorkCounters::recordRuntimeWorkCount,
            onActiveStoryPinsChanged = BenchmarkWorkCounters::recordStoryPinCount,
            onDiscoverMutationTouched = BenchmarkWorkCounters::recordDiscoverMutationTouched,
            onStoryReleaseMutationTouched = BenchmarkWorkCounters::recordStoryReleaseMutationTouched,
        )

        override val imageLoaderCallbacks = CatalogImageLoaderCallbacks(
            onSessionInitialized = BenchmarkLifecycleCounters::recordImageSessionInitialized,
            onSessionClosed = BenchmarkLifecycleCounters::recordImageSessionClosed,
            onDemandStarted = BenchmarkWorkCounters::recordCoverDemandStarted,
            onDemandStopped = BenchmarkWorkCounters::recordCoverDemandStopped,
            onCoverReady = BenchmarkImageCounters::recordImageOwnership,
            onActiveJobsChanged = BenchmarkWorkCounters::recordCoverJobCount,
            onSuccessfulDecode = { evidence: CatalogImageDecodeEvidence ->
                BenchmarkImageCounters.recordSuccessfulDecode(
                    targetWidth = evidence.targetWidth,
                    targetHeight = evidence.targetHeight,
                    originalSize = evidence.originalSize,
                    mainThread = evidence.mainThread,
                )
            },
        )
    }
}
