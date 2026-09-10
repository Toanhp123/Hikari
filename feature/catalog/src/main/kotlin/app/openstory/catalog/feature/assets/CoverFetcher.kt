package app.openstory.catalog.feature.assets

import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
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
    private val remoteTransport: RemoteCoverTransport?,
    private val policyProvider: suspend () -> SourceAssetPolicyProvider?,
    private val preflight: CoverImagePreflight,
) : Fetcher {
    override suspend fun fetch(): FetchResult = try {
        when (val locator = request.locator) {
            null -> artworkFailure(CatalogArtworkFailureReason.INVALID_LOCATOR)
            is CoverLocator.TrustedLocalResource -> fetchLocal(locator)
            is CoverLocator.RemoteHttps -> fetchRemote(locator)
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

    private suspend fun fetchRemote(locator: CoverLocator.RemoteHttps): SourceFetchResult {
        val policy = RemoteCoverPolicy(
            policyProvider = policyProvider,
            transport = remoteTransport,
            temporaryDirectory = options.context.cacheDir,
        )
        val validated = policy.validate(locator)
        encodedCache.read(request.assetKey.stableCacheKey)?.let { source ->
            return SourceFetchResult(source = source, mimeType = null, dataSource = DataSource.DISK)
        }
        val payload = policy.fetch(validated)
        val mediaType = payload.mediaType
        payload.use {
            preflight.inspect(payload.file, mediaType, options.size)
            val committed = payload.file.inputStream().source().buffer().use { source ->
                encodedCache.commit(request.assetKey.stableCacheKey, source, payload.length)
            }
            if (!committed) artworkFailure(CatalogArtworkFailureReason.IO_FAILED)
        }
        val source = encodedCache.read(request.assetKey.stableCacheKey)
            ?: artworkFailure(CatalogArtworkFailureReason.IO_FAILED)
        return SourceFetchResult(source = source, mimeType = mediaType, dataSource = DataSource.NETWORK)
    }

    class Factory(
        private val localResolver: LocalCoverAssetResolver,
        private val encodedCache: CoverEncodedCache,
        private val remoteTransport: RemoteCoverTransport? = null,
        private val policyProvider: suspend () -> SourceAssetPolicyProvider? = { null },
        private val preflight: CoverImagePreflight = CoverImagePreflight(),
    ) : Fetcher.Factory<CoverRequest> {
        override fun create(data: CoverRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverFetcher(
                request = data,
                options = options,
                localResolver = localResolver,
                encodedCache = encodedCache,
                remoteTransport = remoteTransport,
                policyProvider = policyProvider,
                preflight = preflight,
            )
    }
}
