package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.failure.CatalogArtworkFailureReason
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.util.concurrent.CancellationException
import okio.buffer
import okio.source

internal class CoverFetcher(
    private val request: CoverRequest,
    private val options: Options,
    private val localResolver: LocalCoverAssetResolver,
    private val encodedCache: CoverEncodedCache,
) : Fetcher {
    override suspend fun fetch(): FetchResult = try {
        when (val locator = request.locator) {
            null -> artworkFailure(CatalogArtworkFailureReason.INVALID_LOCATOR)
            is CoverLocator.TrustedLocalResource -> fetchLocal(locator)
            is CoverLocator.RemoteHttps -> fetchRemoteCacheHit()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: CatalogFailureException) {
        throw failure
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw CatalogFailureException(CatalogFailure.Artwork(CatalogArtworkFailureReason.IO_FAILED), error)
    }

    private fun fetchLocal(locator: CoverLocator.TrustedLocalResource): SourceFetchResult {
        val asset = localResolver.resolve(locator.logicalAssetId, locator.assetVersion)
            ?: artworkFailure(CatalogArtworkFailureReason.INVALID_LOCATOR)
        val source = options.context.resources.openRawResource(asset.resourceId).source().buffer()
        return SourceFetchResult(
            source = ImageSource(source, options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    private fun fetchRemoteCacheHit(): SourceFetchResult {
        val source = encodedCache.read(request.assetKey.stableCacheKey)
            ?: artworkFailure(CatalogArtworkFailureReason.IO_FAILED)
        return SourceFetchResult(source = source, mimeType = null, dataSource = DataSource.DISK)
    }

    class Factory(
        private val localResolver: LocalCoverAssetResolver,
        private val encodedCache: CoverEncodedCache,
    ) : Fetcher.Factory<CoverRequest> {
        override fun create(data: CoverRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverFetcher(data, options, localResolver, encodedCache)
    }
}

private fun artworkFailure(reason: CatalogArtworkFailureReason): Nothing =
    throw CatalogFailureException(CatalogFailure.Artwork(reason))
