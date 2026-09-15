package app.openstory.catalog.feature.seed

import android.content.Context
import app.openstory.artwork.request.ArtworkAuthorityKey
import app.openstory.artwork.policy.ArtworkPolicy
import app.openstory.artwork.policy.ArtworkPolicyResolver
import app.openstory.artwork.runtime.ArtworkRuntime
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.feature.VariantLocalCoverAssets
import app.openstory.catalog.feature.assets.toImageRequest
import app.openstory.catalog.feature.fixture.BenchmarkCoverFixture
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.common.execution.BoundedProcessWorkAdmission
import coil3.request.ErrorResult
import coil3.request.SuccessResult

internal object BenchmarkCoverPreparation {
    suspend fun primeDiskCover(
        context: Context,
        activation: CatalogCapabilityActivation.Available,
    ) {
        val card = BenchmarkCatalogStatePreparation.firstMangaCard(activation)
        val imageLoader = ArtworkRuntime(
            context = context.applicationContext,
            localResolver = VariantLocalCoverAssets,
            remoteTransport = BenchmarkCoverFixture.transport(context.applicationContext),
            policyResolver = activation.artworkPolicyResolver(),
            admission = BoundedProcessWorkAdmission(),
        )
        BenchmarkCoverFixture.resetTransportRequests()
        try {
            val assetKey = requireNotNull(card.coverAssetKey)
            val locator = requireNotNull(card.coverLocator)
            val request = assetKey.toImageRequest(context.applicationContext, locator).newBuilder()
                .size(COVER_WIDTH_PX, COVER_HEIGHT_PX)
                .build()
            check(imageLoader.imageLoader().execute(request) is SuccessResult) {
                "Benchmark disk cache prime failed."
            }
            check(BenchmarkCoverFixture.transportRequestCount() in 0..1) {
                "Benchmark disk cache prime used more than one transport request."
            }
        } finally {
            imageLoader.close()
            BenchmarkCoverFixture.resetTransportRequests()
        }
    }

    suspend fun assertPathologicalImageBoundsRejected(
        context: Context,
        activation: CatalogCapabilityActivation.Available,
    ): Int {
        val card = BenchmarkCatalogStatePreparation.firstMangaCard(activation)
        BenchmarkCoverFixture.resetTransportRequests()
        BenchmarkCoverFixture.pathologicalTransports().forEachIndexed { index, transport ->
            val uri = RemoteHttpsUriV1.parseAndNormalize(
                "https://covers.hikari.invalid/pathological-$index.png",
            )
            val revision = CoverRevisionV1.remoteUri(uri)
            val imageLoader = ArtworkRuntime(
                context = context.applicationContext,
                localResolver = VariantLocalCoverAssets,
                remoteTransport = transport,
                policyResolver = activation.artworkPolicyResolver(),
                admission = BoundedProcessWorkAdmission(),
            )
            try {
                val assetKey = CoverAssetKey(card.ref.storyId, revision)
                val locator = CoverLocator.RemoteHttps(card.ref.catalogSourceKey, uri, revision)
                val request = assetKey.toImageRequest(context.applicationContext, locator).newBuilder()
                    .size(COVER_WIDTH_PX, COVER_HEIGHT_PX)
                    .build()
                check(imageLoader.imageLoader().execute(request) is ErrorResult) {
                    "Pathological image fixture $index was unexpectedly decoded."
                }
            } finally {
                imageLoader.close()
            }
        }
        return BenchmarkCoverFixture.transportRequestCount().also { count ->
            check(count == PATHOLOGICAL_IMAGE_REJECTIONS) {
                "Pathological image preparation made $count requests instead of $PATHOLOGICAL_IMAGE_REJECTIONS."
            }
        }
    }

    private const val PATHOLOGICAL_IMAGE_REJECTIONS = 2
    private const val COVER_WIDTH_PX = 360
    private const val COVER_HEIGHT_PX = 540
}

private fun CatalogCapabilityActivation.Available.artworkPolicyResolver() = ArtworkPolicyResolver { authority ->
    runCatching { CatalogSourceKey(authority.value) }.getOrNull()?.let { sourceKey ->
        assetPolicyProvider.policyFor(sourceKey)?.let { policy ->
            ArtworkPolicy(ArtworkAuthorityKey(policy.catalogSourceKey.value), policy.allowedHttpsHosts)
        }
    }
}
