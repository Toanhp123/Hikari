package app.openstory.di

import android.content.Context
import app.openstory.common.MonotonicClock
import app.openstory.common.SystemMonotonicClock
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.assets.DownloadReaderAssetStore
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.reader.DownloadAwareReaderDocumentStore
import app.openstory.downloads.reader.ReaderCacheMetadataSource
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.reader.AndroidReaderNetworkFactsPort
import app.openstory.reader.assets.ContentFetchArbiter
import app.openstory.reader.assets.OkHttpReaderAssetDelivery
import app.openstory.reader.assets.ReaderAssetAggregateDiagnostics
import app.openstory.reader.assets.ReaderAssetCoordinator
import app.openstory.reader.assets.ReaderAssetDiagnosticsSink
import app.openstory.reader.assets.ReaderAssetDeliveryPort
import app.openstory.reader.assets.ReaderAssetLoader
import app.openstory.reader.assets.ReaderAssetSessionPort
import app.openstory.reader.assets.ReaderAssetSingleFlight
import app.openstory.reader.assets.ReaderAssetStorePort
import app.openstory.reader.content.PluginReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.document.ReaderDocumentSanitizer
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.routing.ContentSourceExecutionLane
import app.openstory.reader.routing.DefaultReaderExecutionScheduler
import app.openstory.reader.routing.PrefetchCoordinator
import app.openstory.reader.routing.ReaderCacheFactsPort
import app.openstory.reader.routing.ReaderExecutionScheduler
import app.openstory.reader.routing.ReaderHalfOpenProbeRegistry
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderRouteCoordinator
import app.openstory.reader.routing.ReaderRouteSessionFactory
import app.openstory.reader.routing.ReaderSourceHealthRegistry
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.reader.RoomReadingProgressRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReaderAssetCoordinatorScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReaderAssetHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {
    @Provides
    @Singleton
    fun provideDownloadAwareReaderDocumentStore(
        blobs: ChapterBlobStore,
        cache: CacheRepository,
        downloads: DownloadRepository,
        writeAdmission: StorageWriteAdmission,
        metadataSource: ReaderCacheMetadataSource,
        automaticCacheBudgetCoordinator: AutomaticCacheBudgetCoordinator,
    ): DownloadAwareReaderDocumentStore = DownloadAwareReaderDocumentStore(
        blobs = blobs,
        cacheRepository = cache,
        downloads = downloads,
        now = System::currentTimeMillis,
        writeAdmission = writeAdmission,
        metadataSource = metadataSource,
        automaticCacheBudgetCoordinator = automaticCacheBudgetCoordinator,
    )

    @Provides
    fun provideReaderDocumentStore(store: DownloadAwareReaderDocumentStore): ReaderDocumentStore = store

    @Provides
    fun provideReaderCacheFactsPort(store: DownloadAwareReaderDocumentStore): ReaderCacheFactsPort = store

    @Provides
    fun provideReaderAssetStorePort(store: DownloadReaderAssetStore): ReaderAssetStorePort = store

    @Provides
    @Singleton
    @ReaderAssetCoordinatorScope
    fun provideReaderAssetCoordinatorScope(
        dispatchers: AppDispatchers,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    @Provides
    @Singleton
    @ReaderAssetHttpClient
    fun provideReaderAssetHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideReaderAssetDeliveryPort(
        @ReaderAssetHttpClient client: OkHttpClient,
    ): ReaderAssetDeliveryPort = OkHttpReaderAssetDelivery(client)

    @Provides
    @Singleton
    fun provideReaderAssetDiagnosticsSink(): ReaderAssetDiagnosticsSink = ReaderAssetAggregateDiagnostics()

    @Provides
    @Singleton
    fun provideReaderAssetSingleFlight(
        @ReaderAssetCoordinatorScope coordinatorScope: CoroutineScope,
        diagnostics: ReaderAssetDiagnosticsSink,
    ): ReaderAssetSingleFlight = ReaderAssetSingleFlight(coordinatorScope, diagnostics)

    @Provides
    @Singleton
    fun provideReaderAssetLoader(
        store: ReaderAssetStorePort,
        delivery: ReaderAssetDeliveryPort,
        singleFlight: ReaderAssetSingleFlight,
        fetchArbiter: ContentFetchArbiter,
        @ReaderAssetCoordinatorScope coordinatorScope: CoroutineScope,
        diagnostics: ReaderAssetDiagnosticsSink,
    ): ReaderAssetLoader = ReaderAssetLoader(
        store = store,
        delivery = delivery,
        singleFlight = singleFlight,
        fetchArbiter = fetchArbiter,
        persistenceScope = coordinatorScope,
        diagnostics = diagnostics,
    )

    @Provides
    @Singleton
    fun provideReaderAssetCoordinator(
        store: ReaderAssetStorePort,
        networkFacts: ReaderNetworkFactsPort,
        loader: ReaderAssetLoader,
        @ReaderAssetCoordinatorScope coordinatorScope: CoroutineScope,
        diagnostics: ReaderAssetDiagnosticsSink,
    ): ReaderAssetCoordinator = ReaderAssetCoordinator(
        store = store,
        networkFacts = networkFacts,
        coordinatorScope = coordinatorScope,
        loader = loader,
        diagnostics = diagnostics,
    )

    @Provides
    fun provideReaderAssetSessionPort(
        coordinator: ReaderAssetCoordinator,
    ): ReaderAssetSessionPort = coordinator

    @Provides
    @Singleton
    fun provideReaderNetworkFactsPort(
        @ApplicationContext context: Context,
    ): ReaderNetworkFactsPort = AndroidReaderNetworkFactsPort(context)

    @Provides
    @Singleton
    fun providePluginReaderDocumentSourceRegistry(
        runtime: PluginRuntime,
        json: Json,
    ): PluginReaderDocumentSourceRegistry = PluginReaderDocumentSourceRegistry(
        runtime,
        json,
        ReaderDocumentSanitizer(),
    )

    @Provides
    fun provideReaderDocumentSourceRegistry(
        registry: PluginReaderDocumentSourceRegistry,
    ): ReaderDocumentSourceRegistry = registry

    @Provides
    fun provideReaderSourceAvailability(
        registry: PluginReaderDocumentSourceRegistry,
    ): ReaderSourceAvailability = registry

    @Provides
    @Singleton
    fun provideReaderSourceHealthRegistry(): ReaderSourceHealthRegistry = ReaderSourceHealthRegistry()

    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = SystemMonotonicClock

    @Provides
    @Singleton
    fun provideContentFetchArbiter(monotonicClock: MonotonicClock): ContentFetchArbiter =
        ContentFetchArbiter(monotonicClock = monotonicClock)

    @Provides
    @Singleton
    fun provideContentSourceExecutionLane(): ContentSourceExecutionLane = ContentSourceExecutionLane()

    @Provides
    @Singleton
    fun provideReaderHalfOpenProbeRegistry(): ReaderHalfOpenProbeRegistry =
        ReaderHalfOpenProbeRegistry()

    @Provides
    @Singleton
    fun provideReaderExecutionScheduler(): ReaderExecutionScheduler = DefaultReaderExecutionScheduler()

    @Provides
    @Singleton
    fun provideReadingProgressRepository(database: OpenStoryDatabase): ReadingProgressRepository =
        RoomReadingProgressRepository(database)

    @Provides
    @Singleton
    fun provideReaderRouteCoordinator(
        store: ReaderDocumentStore,
        sources: ReaderDocumentSourceRegistry,
        progress: ReadingProgressRepository,
        sourceAvailability: ReaderSourceAvailability,
        healthRegistry: ReaderSourceHealthRegistry,
        sourceLane: ContentSourceExecutionLane,
        fetchArbiter: ContentFetchArbiter,
        halfOpenProbeRegistry: ReaderHalfOpenProbeRegistry,
        cacheFacts: ReaderCacheFactsPort,
        networkFacts: ReaderNetworkFactsPort,
        executionScheduler: ReaderExecutionScheduler,
    ): ReaderRouteCoordinator = ReaderRouteCoordinator(
        store = store,
        sources = sources,
        progress = progress,
        sourceAvailability = sourceAvailability,
        healthRegistry = healthRegistry,
        sourceLane = sourceLane,
        fetchArbiter = fetchArbiter,
        halfOpenProbeRegistry = halfOpenProbeRegistry,
        cacheFacts = cacheFacts,
        networkFacts = networkFacts,
        executionScheduler = executionScheduler,
    )

    @Provides
    @Singleton
    fun providePrefetchCoordinator(
        coordinator: ReaderRouteCoordinator,
    ): PrefetchCoordinator = PrefetchCoordinator(coordinator)

    @Provides
    @Singleton
    fun provideReaderRouteSessionFactory(
        coordinator: ReaderRouteCoordinator,
        prefetchCoordinator: PrefetchCoordinator,
        assetSessionPort: ReaderAssetSessionPort,
    ): ReaderRouteSessionFactory = ReaderRouteSessionFactory(
        coordinator,
        prefetchCoordinator,
        assetSessionPort,
    )
}
