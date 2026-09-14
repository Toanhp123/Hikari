package app.openstory.catalog.feature.assets

import android.content.Context
import app.openstory.artwork.ArtworkAuthorityKey
import app.openstory.artwork.ArtworkCallbacksRegistry
import app.openstory.artwork.ArtworkDecodeEvidence
import app.openstory.artwork.ArtworkDecodedMemoryCache
import app.openstory.artwork.ArtworkEncodedCache
import app.openstory.artwork.ArtworkEncodedDiskCache
import app.openstory.artwork.ArtworkFetcher
import app.openstory.artwork.ArtworkImagePreflight
import app.openstory.artwork.ArtworkImagePreflightResult
import app.openstory.artwork.ArtworkLimits
import app.openstory.artwork.ArtworkLoader
import app.openstory.artwork.ArtworkPolicy
import app.openstory.artwork.ArtworkPolicyResolver
import app.openstory.artwork.ArtworkPreflight
import app.openstory.artwork.ArtworkRemotePayload
import app.openstory.artwork.ArtworkRemotePolicy
import app.openstory.artwork.ArtworkRequest
import app.openstory.artwork.ArtworkRuntime
import app.openstory.artwork.ArtworkRuntimeCallbacks
import app.openstory.artwork.ArtworkSession
import app.openstory.artwork.ArtworkTransport
import app.openstory.artwork.ArtworkTransportRequest
import app.openstory.artwork.ArtworkTransportResponse
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.common.execution.BoundedProcessWorkAdmission
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import coil3.request.Options
import java.io.File
import kotlinx.coroutines.runBlocking

internal typealias CatalogImageLimits = ArtworkLimits
internal typealias CoverImagePreflight = ArtworkImagePreflight
internal typealias CoverImagePreflightResult = ArtworkImagePreflightResult
internal typealias CoverEncodedCache = ArtworkEncodedCache
internal typealias CoverEncodedDiskCache = ArtworkEncodedDiskCache
internal typealias CatalogImageCallbacksRegistry = ArtworkCallbacksRegistry
internal typealias DecodedCoverMemoryCache = ArtworkDecodedMemoryCache
internal typealias CatalogCoverLoader = ArtworkLoader
internal typealias CatalogImageDecodeEvidence = ArtworkDecodeEvidence
internal typealias RemoteCoverTransport = ArtworkTransport
internal typealias RemoteCoverTransportRequest = ArtworkTransportRequest
internal typealias RemoteCoverTransportResponse = ArtworkTransportResponse
internal typealias RemoteCoverPayload = ArtworkRemotePayload

internal data class CoverRequest(
    val assetKey: CoverAssetKey,
    val locator: CoverLocator?,
) {
    fun toImageRequest(context: Context): ImageRequest = assetKey.toImageRequest(context, locator)

    fun toArtworkRequest(context: Context): ArtworkRequest = toImageRequest(context).data as ArtworkRequest
}

internal class CatalogImageLoaderCallbacks(
    val onSessionInitialized: () -> Unit = {},
    val onSessionClosed: () -> Unit = {},
    val onDemandStarted: () -> Unit = {},
    val onDemandStopped: () -> Unit = {},
    val onCoverReady: (Long, Long) -> Unit = { _, _ -> },
    val onActiveJobsChanged: (Int) -> Unit = {},
    val onSuccessfulDecode: (CatalogImageDecodeEvidence) -> Unit = {},
)

internal class CatalogImageLoader(
    context: Context,
    localResolver: LocalCoverAssetResolver,
    remoteTransport: RemoteCoverTransport? = null,
    policyProvider: suspend () -> SourceAssetPolicyProvider? = { null },
    callbacksRegistry: CatalogImageCallbacksRegistry = object : CatalogImageCallbacksRegistry {
        override fun register(callbacks: android.content.ComponentCallbacks2) =
            context.applicationContext.registerComponentCallbacks(callbacks)

        override fun unregister(callbacks: android.content.ComponentCallbacks2) =
            context.applicationContext.unregisterComponentCallbacks(callbacks)
    },
    decodedMemoryCacheFactory: () -> DecodedCoverMemoryCache = {
        object : DecodedCoverMemoryCache {
            override val coilMemoryCache = coil3.memory.MemoryCache.Builder()
                .maxSizeBytes(ArtworkLimits.DECODED_MEMORY_BYTES)
                .weakReferencesEnabled(false)
                .build()
            override val maxSizeBytes: Long get() = coilMemoryCache.maxSize
            override fun clear() = coilMemoryCache.clear()
        }
    },
    callbacks: CatalogImageLoaderCallbacks = CatalogImageLoaderCallbacks(),
) : CatalogCoverLoader, AutoCloseable {
    private val delegate = ArtworkRuntime(
        context = context,
        localResolver = localResolver,
        remoteTransport = remoteTransport,
        policyResolver = ArtworkPolicyResolver { authority ->
            runBlocking { policyProvider() }?.policyFor(CatalogSourceKey(authority.value))?.let { policy ->
                ArtworkPolicy(authority, policy.allowedHttpsHosts)
            }
        },
        callbacksRegistry = callbacksRegistry,
        decodedMemoryCacheFactory = decodedMemoryCacheFactory,
        callbacks = ArtworkRuntimeCallbacks(
            onSessionInitialized = callbacks.onSessionInitialized,
            onSessionClosed = callbacks.onSessionClosed,
            onDemandStarted = callbacks.onDemandStarted,
            onDemandStopped = callbacks.onDemandStopped,
            onActiveDecodeJobsChanged = callbacks.onActiveJobsChanged,
            onArtworkReady = callbacks.onCoverReady,
            onSuccessfulDecode = callbacks.onSuccessfulDecode,
        ),
    )

    fun session(): ArtworkSession = delegate.session()
    override fun imageLoader(): ImageLoader = delegate.imageLoader()
    override fun onDemandStarted() = delegate.onDemandStarted()
    override fun onDemandStopped() = delegate.onDemandStopped()
    override fun onArtworkReady() = delegate.onArtworkReady()
    fun onCoverReady() = delegate.onArtworkReady()
    override fun close() = delegate.close()
}

internal val LocalCatalogImageLoader = LocalArtworkLoader

internal class CoverFetcher(
    request: CoverRequest,
    options: Options,
    localResolver: LocalCoverAssetResolver,
    encodedCache: CoverEncodedCache,
    remoteTransport: RemoteCoverTransport?,
    policyProvider: suspend () -> SourceAssetPolicyProvider?,
    preflight: CoverImagePreflight,
) : Fetcher by ArtworkFetcher(
    request = request.toArtworkRequest(options.context),
    options = options,
    localResolver = localResolver,
    encodedCache = encodedCache,
    policyResolver = policyResolver(policyProvider),
    admission = BoundedProcessWorkAdmission(),
    remoteTransport = remoteTransport,
    preflight = ArtworkPreflight(preflight::inspect),
) {
    class Factory(
        private val localResolver: LocalCoverAssetResolver,
        private val encodedCache: CoverEncodedCache,
        private val remoteTransport: RemoteCoverTransport? = null,
        private val policyProvider: suspend () -> SourceAssetPolicyProvider? = { null },
        private val preflight: CoverImagePreflight = CoverImagePreflight(),
    ) : Fetcher.Factory<CoverRequest> {
        override fun create(data: CoverRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            CoverFetcher(data, options, localResolver, encodedCache, remoteTransport, policyProvider, preflight)
    }
}

internal class RemoteCoverPolicy(
    private val policyProvider: suspend () -> SourceAssetPolicyProvider?,
    private val transport: RemoteCoverTransport?,
    private val temporaryDirectory: File,
) {
    suspend fun fetch(locator: CoverLocator.RemoteHttps): RemoteCoverPayload =
        fetch(locator.catalogSourceKey, locator.normalizedUri.value)

    suspend fun fetch(sourceKey: CatalogSourceKey, rawUri: String): RemoteCoverPayload {
        val resolver = policyResolver(policyProvider)
        return mapFailure {
            ArtworkRemotePolicy(resolver, transport, temporaryDirectory).fetch(
                app.openstory.artwork.ArtworkRequestIdentity(
                    authority = ArtworkAuthorityKey(sourceKey.value),
                    stableAssetKey = "android-test:$rawUri",
                    locator = rawUri,
                    transformKey = "android-test",
                    varyKey = "public",
                ),
            )
        }
    }
}

private fun policyResolver(
    provider: suspend () -> SourceAssetPolicyProvider?,
) = ArtworkPolicyResolver { authority ->
    runBlocking { provider() }?.policyFor(CatalogSourceKey(authority.value))?.let { policy ->
        ArtworkPolicy(authority, policy.allowedHttpsHosts)
    }
}

private inline fun <T> mapFailure(block: () -> T): T = try {
    block()
} catch (failure: app.openstory.artwork.ArtworkFailureException) {
    val reason = app.openstory.catalog.domain.failure.CatalogArtworkFailureReason.valueOf(
        if (failure.reason.name == "SATURATED") "IO_FAILED" else failure.reason.name,
    )
    throw CatalogFailureException(CatalogFailure.Artwork(reason), failure)
}
