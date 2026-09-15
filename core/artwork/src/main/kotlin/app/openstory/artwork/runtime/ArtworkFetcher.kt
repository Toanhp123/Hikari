package app.openstory.artwork.runtime

import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.cache.ArtworkEncodedCache
import app.openstory.artwork.policy.ArtworkPolicyResolver
import app.openstory.artwork.preflight.ArtworkImagePreflight
import app.openstory.artwork.preflight.ArtworkPreflight
import app.openstory.artwork.remote.ArtworkRemotePolicy
import app.openstory.artwork.remote.ArtworkTransport
import app.openstory.artwork.request.ArtworkLocalResolver
import app.openstory.artwork.request.ArtworkLocator
import app.openstory.artwork.request.ArtworkRequest
import app.openstory.common.execution.ProcessWorkAdmission
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

class ArtworkFetcher(
    private val request: ArtworkRequest,
    private val options: Options,
    private val localResolver: ArtworkLocalResolver,
    private val encodedCache: ArtworkEncodedCache,
    private val policyResolver: ArtworkPolicyResolver,
    private val admission: ProcessWorkAdmission,
    private val remoteTransport: ArtworkTransport?,
    private val preflight: ArtworkPreflight,
) : Fetcher {
    override suspend fun fetch(): FetchResult = try {
        when (val locator = request.locator) {
            null -> throw ArtworkFailureException(ArtworkFailureReason.INVALID_LOCATOR)
            is ArtworkLocator.TrustedLocalResource -> fetchLocal(locator)
            is ArtworkLocator.RemoteHttps -> fetchRemote()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: ArtworkFailureException) {
        throw failure
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        throw ArtworkFailureException(ArtworkFailureReason.IO_FAILED, error)
    }

    private fun fetchLocal(locator: ArtworkLocator.TrustedLocalResource): SourceFetchResult {
        val asset = localResolver.resolve(locator.logicalAssetId, locator.assetVersion)
            ?: throw ArtworkFailureException(ArtworkFailureReason.INVALID_LOCATOR)
        val source = options.context.resources.openRawResource(asset.resourceId).source().buffer()
        return SourceFetchResult(
            source = ImageSource(source, options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun fetchRemote(): SourceFetchResult {
        val policy = ArtworkRemotePolicy(policyResolver, remoteTransport, options.context.cacheDir, admission)
        val validated = policy.validate(request.identity)
        encodedCache.read(request.identity.encodedCacheKey)?.let { source ->
            return SourceFetchResult(source = source, mimeType = null, dataSource = DataSource.DISK)
        }
        val payload = policy.fetch(validated)
        val mediaType = payload.mediaType
        payload.use {
            preflight.inspect(payload.file, mediaType, options.size)
            val committed = payload.file.inputStream().source().buffer().use { source ->
                encodedCache.commit(request.identity.encodedCacheKey, source, payload.length)
            }
            if (!committed) throw ArtworkFailureException(ArtworkFailureReason.IO_FAILED)
        }
        val source = encodedCache.read(request.identity.encodedCacheKey)
            ?: throw ArtworkFailureException(ArtworkFailureReason.IO_FAILED)
        return SourceFetchResult(source = source, mimeType = mediaType, dataSource = DataSource.NETWORK)
    }

    class Factory(
        private val localResolver: ArtworkLocalResolver,
        private val encodedCache: ArtworkEncodedCache,
        private val policyResolver: ArtworkPolicyResolver = ArtworkPolicyResolver { null },
        private val admission: ProcessWorkAdmission = app.openstory.common.execution.BoundedProcessWorkAdmission(),
        private val remoteTransport: ArtworkTransport? = null,
        private val preflight: ArtworkPreflight = DefaultArtworkPreflight,
    ) : Fetcher.Factory<ArtworkRequest> {
        override fun create(data: ArtworkRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkFetcher(
                data,
                options,
                localResolver,
                encodedCache,
                policyResolver,
                admission,
                remoteTransport,
                preflight,
            )
    }
}

private val DefaultArtworkPreflight = ArtworkPreflight(ArtworkImagePreflight()::inspect)
