package app.openstory.catalog.feature.assets

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal object CatalogImageLimits {
    const val DECODED_MEMORY_BYTES = 32L * 1024 * 1024
    const val ENCODED_DISK_BYTES = 128L * 1024 * 1024
    const val MAX_ENCODED_BYTES = 8L * 1024 * 1024
    const val ACTIVE_COVER_JOBS = 8
    const val MANUAL_OFFSCREEN_PREFETCH = 0
}

internal interface DecodedCoverMemoryCache {
    val coilMemoryCache: MemoryCache
    val maxSizeBytes: Long

    fun clear()
}

private class CoilDecodedCoverMemoryCache(
    override val coilMemoryCache: MemoryCache,
) : DecodedCoverMemoryCache {
    override val maxSizeBytes: Long
        get() = coilMemoryCache.maxSize

    override fun clear() {
        coilMemoryCache.clear()
    }
}

internal class CoverJobLimiter(maxActiveJobs: Int) {
    private val semaphore = Semaphore(maxActiveJobs)

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
}

private class CoverJobLimiterInterceptor(
    private val limiter: CoverJobLimiter,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain) = limiter.withPermit(chain::proceed)
}

internal class CatalogImageSession(
    val imageLoader: ImageLoader,
    val encodedDiskCache: CoverEncodedDiskCache,
    private val diskCache: DiskCache,
    private val memoryPressureController: CatalogImageMemoryPressureController,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        memoryPressureController.close()
        imageLoader.shutdown()
        diskCache.shutdown()
    }
}

internal class CatalogImageLoader(
    private val context: Context,
    private val localResolver: LocalCoverAssetResolver,
    private val remoteTransport: RemoteCoverTransport? = null,
    private val policyProvider: suspend () -> SourceAssetPolicyProvider? = { null },
    private val callbacksRegistry: CatalogImageCallbacksRegistry =
        AndroidCatalogImageCallbacksRegistry(context.applicationContext),
    private val decodedMemoryCacheFactory: () -> DecodedCoverMemoryCache = {
        CoilDecodedCoverMemoryCache(
            MemoryCache.Builder()
                .maxSizeBytes(CatalogImageLimits.DECODED_MEMORY_BYTES)
                .weakReferencesEnabled(false)
                .build(),
        )
    },
    private val onSessionInitialized: () -> Unit = {},
    private val onSessionClosed: () -> Unit = {},
    private val onDemandStartedCallback: () -> Unit = {},
    private val onDemandStoppedCallback: () -> Unit = {},
) : CatalogCoverLoader, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var initializedSession: CatalogImageSession? = null

    @Synchronized
    fun session(): CatalogImageSession {
        check(!closed.get())
        initializedSession?.let { return it }
        return createSession().also { initializedSession = it }
    }

    override fun imageLoader(): ImageLoader = session().imageLoader

    override fun onDemandStarted() = onDemandStartedCallback()

    override fun onDemandStopped() = onDemandStoppedCallback()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(this) {
            initializedSession?.let { session ->
                session.close()
                onSessionClosed()
            }
            initializedSession = null
        }
    }

    private fun createSession(): CatalogImageSession {
        val decodedMemoryCache = decodedMemoryCacheFactory()
        require(decodedMemoryCache.maxSizeBytes <= CatalogImageLimits.DECODED_MEMORY_BYTES)
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve(CACHE_DIRECTORY))
            .maxSizeBytes(CatalogImageLimits.ENCODED_DISK_BYTES)
            .build()
        val encodedDiskCache = CoverEncodedDiskCache(diskCache)
        val limiter = CoverJobLimiter(CatalogImageLimits.ACTIVE_COVER_JOBS)
        val imageDispatcher = Dispatchers.IO.limitedParallelism(CatalogImageLimits.ACTIVE_COVER_JOBS)
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache(decodedMemoryCache.coilMemoryCache)
            .diskCache(diskCache)
            .fetcherCoroutineContext(imageDispatcher)
            .decoderCoroutineContext(imageDispatcher)
            .components {
                add(CoverJobLimiterInterceptor(limiter))
                add(
                    CoverFetcher.Factory(
                        localResolver = localResolver,
                        encodedCache = encodedDiskCache,
                        remoteTransport = remoteTransport,
                        policyProvider = policyProvider,
                    ),
                )
            }
            .build()
        val memoryPressureController = CatalogImageMemoryPressureController(
            decodedMemoryCache = decodedMemoryCache,
            registry = callbacksRegistry,
        )
        return CatalogImageSession(
            imageLoader = imageLoader,
            encodedDiskCache = encodedDiskCache,
            diskCache = diskCache,
            memoryPressureController = memoryPressureController,
        ).also { onSessionInitialized() }
    }

    private companion object {
        const val CACHE_DIRECTORY = "catalog-cover-cache"
    }
}

internal fun interface CatalogCoverLoader {
    fun imageLoader(): ImageLoader

    fun onDemandStarted() = Unit

    fun onDemandStopped() = Unit
}

internal val LocalCatalogImageLoader = staticCompositionLocalOf<CatalogCoverLoader?> { null }
