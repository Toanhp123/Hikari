package app.openstory.artwork

import android.content.Context
import android.os.Looper
import app.openstory.common.execution.ProcessWorkAdmission
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.size.Dimension
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.buffer
import okio.source

fun interface ArtworkLoader {
    fun imageLoader(): ImageLoader

    fun onDemandStarted() = Unit

    fun onDemandStopped() = Unit

    fun onArtworkReady() = Unit
}

class ArtworkRuntime(
    context: Context,
    private val localResolver: ArtworkLocalResolver,
    private val policyResolver: ArtworkPolicyResolver = ArtworkPolicyResolver { null },
    private val admission: ProcessWorkAdmission = app.openstory.common.execution.BoundedProcessWorkAdmission(),
    private val remoteTransport: ArtworkTransport? = null,
    private val preflight: ArtworkPreflight = DefaultArtworkPreflight,
    private val callbacksRegistry: ArtworkCallbacksRegistry =
        AndroidArtworkCallbacksRegistry(context.applicationContext),
    private val decodedMemoryCacheFactory: () -> ArtworkDecodedMemoryCache = {
        CoilArtworkDecodedMemoryCache(
            MemoryCache.Builder()
                .maxSizeBytes(ArtworkLimits.DECODED_MEMORY_BYTES)
                .weakReferencesEnabled(false)
                .build(),
        )
    },
    private val callbacks: ArtworkRuntimeCallbacks = ArtworkRuntimeCallbacks(),
) : ArtworkLoader, AutoCloseable {
    private val applicationContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private var initializedSession: ArtworkSession? = null

    @Synchronized
    fun session(): ArtworkSession {
        check(!closed.get())
        initializedSession?.let { return it }
        return createSession().also { initializedSession = it }
    }

    override fun imageLoader(): ImageLoader = session().imageLoader

    override fun onDemandStarted() = callbacks.onDemandStarted()

    override fun onDemandStopped() = callbacks.onDemandStopped()

    override fun onArtworkReady() {
        val active = session()
        callbacks.onArtworkReady(active.decodedMemoryBytes(), active.encodedDiskBytes())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(this) {
            initializedSession?.let { session ->
                session.close()
                callbacks.onSessionClosed()
            }
            initializedSession = null
        }
    }

    private fun createSession(): ArtworkSession {
        val decodedMemoryCache = decodedMemoryCacheFactory()
        require(decodedMemoryCache.maxSizeBytes <= ArtworkLimits.DECODED_MEMORY_BYTES)
        val diskCache = DiskCache.Builder()
            .directory(applicationContext.cacheDir.resolve(CACHE_DIRECTORY))
            .maxSizeBytes(ArtworkLimits.ENCODED_DISK_BYTES)
            .build()
        val encodedCache = ArtworkEncodedDiskCache(diskCache)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = ArtworkPipelineCoordinator(
            scope = scope,
            admission = admission,
            onActiveDecodeJobsChanged = callbacks.onActiveDecodeJobsChanged,
        )
        val imageDispatcher = Dispatchers.IO.limitedParallelism(DECODE_DISPATCHER_PARALLELISM)
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache(decodedMemoryCache.coilMemoryCache)
            .diskCache(diskCache)
            .fetcherCoroutineContext(imageDispatcher)
            .decoderCoroutineContext(imageDispatcher)
            .eventListenerFactory { ArtworkImageEventListener(callbacks.onSuccessfulDecode) }
            .components {
                add(ArtworkPipelineInterceptor(policyResolver, coordinator))
                add(
                    ArtworkFetcher.Factory(
                        localResolver = localResolver,
                        encodedCache = encodedCache,
                        policyResolver = policyResolver,
                        admission = admission,
                        remoteTransport = remoteTransport,
                        preflight = preflight,
                    ),
                )
            }
            .build()
        val pressureController = ArtworkMemoryPressureController(decodedMemoryCache, callbacksRegistry)
        return ArtworkSession(
            imageLoader = imageLoader,
            encodedDiskCache = encodedCache,
            diskCache = diskCache,
            decodedMemoryCache = decodedMemoryCache,
            memoryPressureController = pressureController,
            coordinator = coordinator,
            scope = scope,
        ).also { callbacks.onSessionInitialized() }
    }

    private companion object {
        const val CACHE_DIRECTORY = "artwork-cache"
        const val DECODE_DISPATCHER_PARALLELISM = 4
    }
}

class ArtworkSession internal constructor(
    val imageLoader: ImageLoader,
    val encodedDiskCache: ArtworkEncodedDiskCache,
    private val diskCache: DiskCache,
    private val decodedMemoryCache: ArtworkDecodedMemoryCache,
    private val memoryPressureController: ArtworkMemoryPressureController,
    private val coordinator: ArtworkPipelineCoordinator,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun decodedMemoryBytes(): Long = decodedMemoryCache.coilMemoryCache.size

    fun encodedDiskBytes(): Long = diskCache.size

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        memoryPressureController.close()
        coordinator.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        imageLoader.shutdown()
        diskCache.shutdown()
    }
}

data class ArtworkRuntimeCallbacks(
    val onSessionInitialized: () -> Unit = {},
    val onSessionClosed: () -> Unit = {},
    val onDemandStarted: () -> Unit = {},
    val onDemandStopped: () -> Unit = {},
    val onActiveDecodeJobsChanged: (Int) -> Unit = {},
    val onArtworkReady: (Long, Long) -> Unit = { _, _ -> },
    val onSuccessfulDecode: (ArtworkDecodeEvidence) -> Unit = {},
)

data class ArtworkDecodeEvidence(
    val targetWidth: Int?,
    val targetHeight: Int?,
    val originalSize: Boolean,
    val mainThread: Boolean,
)

private class ArtworkImageEventListener(
    private val onSuccessfulDecode: (ArtworkDecodeEvidence) -> Unit,
) : EventListener() {
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        if (result == null) return
        onSuccessfulDecode(
            ArtworkDecodeEvidence(
                targetWidth = (options.size.width as? Dimension.Pixels)?.px,
                targetHeight = (options.size.height as? Dimension.Pixels)?.px,
                originalSize = options.size == coil3.size.Size.ORIGINAL,
                mainThread = Looper.myLooper() == Looper.getMainLooper(),
            ),
        )
    }
}

private class ArtworkPipelineInterceptor(
    private val policyResolver: ArtworkPolicyResolver,
    private val coordinator: ArtworkPipelineCoordinator,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val artworkRequest = chain.request.data as? ArtworkRequest ?: return chain.proceed()
        val policy = policyResolver.policyFor(artworkRequest.identity.authority)
        return coordinator.run(
            ArtworkWorkKey(
                request = artworkRequest.identity,
                policy = policy,
                decodeSizeKey = chain.size.toString(),
            ),
            chain::proceed,
        )
    }
}

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
