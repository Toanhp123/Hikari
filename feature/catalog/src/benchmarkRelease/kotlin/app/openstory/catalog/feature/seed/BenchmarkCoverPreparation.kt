package app.openstory.catalog.feature.seed

import android.content.Context
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.feature.VariantLocalCoverAssets
import app.openstory.catalog.feature.assets.CatalogImageLoader
import app.openstory.catalog.feature.assets.CoverRequest
import app.openstory.catalog.feature.fixture.BenchmarkCoverFixture
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult

internal object BenchmarkCoverPreparation {
    suspend fun primeDiskCover(
        context: Context,
        activation: CatalogCapabilityActivation.Available,
    ) {
        val card = BenchmarkCatalogStatePreparation.firstMangaCard(activation)
        val imageLoader = CatalogImageLoader(
            context = context.applicationContext,
            localResolver = VariantLocalCoverAssets,
            remoteTransport = BenchmarkCoverFixture.transport(context.applicationContext),
            policyProvider = { activation.assetPolicyProvider },
        )
        BenchmarkCoverFixture.resetTransportRequests()
        try {
            val coverRequest = CoverRequest(
                assetKey = requireNotNull(card.coverAssetKey),
                locator = requireNotNull(card.coverLocator),
            )
            val request = ImageRequest.Builder(context.applicationContext)
                .data(coverRequest)
                .size(COVER_WIDTH_PX, COVER_HEIGHT_PX)
                .memoryCacheKey(coverRequest.assetKey.stableCacheKey)
                .diskCacheKey(coverRequest.assetKey.stableCacheKey)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            check(imageLoader.imageLoader().execute(request) is SuccessResult) {
                "Benchmark disk cache prime failed."
            }
            check(BenchmarkCoverFixture.transportRequestCount() == 1) {
                "Benchmark disk cache prime did not use exactly one transport request."
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
            val imageLoader = CatalogImageLoader(
                context = context.applicationContext,
                localResolver = VariantLocalCoverAssets,
                remoteTransport = transport,
                policyProvider = { activation.assetPolicyProvider },
            )
            try {
                val coverRequest = CoverRequest(
                    assetKey = CoverAssetKey(card.ref.storyId, revision),
                    locator = CoverLocator.RemoteHttps(card.ref.catalogSourceKey, uri, revision),
                )
                val request = ImageRequest.Builder(context.applicationContext)
                    .data(coverRequest)
                    .size(COVER_WIDTH_PX, COVER_HEIGHT_PX)
                    .memoryCacheKey(coverRequest.assetKey.stableCacheKey)
                    .diskCacheKey(coverRequest.assetKey.stableCacheKey)
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
