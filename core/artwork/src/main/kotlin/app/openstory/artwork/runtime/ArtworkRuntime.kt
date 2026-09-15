package app.openstory.artwork.runtime

import android.content.Context
import android.os.Looper
import app.openstory.artwork.ArtworkLimits
import app.openstory.artwork.cache.AndroidArtworkCallbacksRegistry
import app.openstory.artwork.cache.ArtworkCallbacksRegistry
import app.openstory.artwork.cache.ArtworkDecodedMemoryCache
import app.openstory.artwork.cache.ArtworkEncodedDiskCache
import app.openstory.artwork.cache.ArtworkMemoryPressureController
import app.openstory.artwork.cache.CoilArtworkDecodedMemoryCache
import app.openstory.artwork.pipeline.ArtworkPipelineCoordinator
import app.openstory.artwork.pipeline.ArtworkWorkKey
import app.openstory.artwork.policy.ArtworkPolicyResolver
import app.openstory.artwork.preflight.ArtworkImagePreflight
import app.openstory.artwork.preflight.ArtworkPreflight
import app.openstory.artwork.remote.ArtworkTransport
import app.openstory.artwork.request.ArtworkLocalResolver
import app.openstory.artwork.request.ArtworkRequest
import app.openstory.common.execution.ProcessWorkAdmission
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.size.Dimension
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

private val DefaultArtworkPreflight = ArtworkPreflight(ArtworkImagePreflight()::inspect)
