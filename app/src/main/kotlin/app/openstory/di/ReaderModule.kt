package app.openstory.di

import android.content.Context
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.reader.content.PluginReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.document.ReaderDocumentSanitizer
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.routing.PrefetchCoordinator
import app.openstory.reader.routing.DefaultReaderExecutionScheduler
import app.openstory.reader.routing.ReaderExecutionScheduler
import app.openstory.reader.routing.ReaderRouteCoordinator
import app.openstory.reader.routing.ReaderCacheFactsPort
import app.openstory.reader.AndroidReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderSourceExecutionLimiter
import app.openstory.reader.routing.ReaderSourceHealthRegistry
import app.openstory.reader.routing.ReaderRouteSessionFactory
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.reader.RoomReadingProgressRepository
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.reader.DownloadAwareReaderDocumentStore
import app.openstory.downloads.reader.ReaderCacheMetadataSource
import app.openstory.downloads.reconcile.StorageWriteAdmission
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import kotlinx.serialization.json.Json

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
    ): DownloadAwareReaderDocumentStore = DownloadAwareReaderDocumentStore(
        blobs = blobs,
        cacheRepository = cache,
        downloads = downloads,
        now = System::currentTimeMillis,
        writeAdmission = writeAdmission,
        metadataSource = metadataSource,
    )

    @Provides
    fun provideReaderDocumentStore(store: DownloadAwareReaderDocumentStore): ReaderDocumentStore = store

    @Provides
    fun provideReaderCacheFactsPort(store: DownloadAwareReaderDocumentStore): ReaderCacheFactsPort = store

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
    fun provideReaderSourceExecutionLimiter(): ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter()

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
        executionLimiter: ReaderSourceExecutionLimiter,
        cacheFacts: ReaderCacheFactsPort,
        networkFacts: ReaderNetworkFactsPort,
        executionScheduler: ReaderExecutionScheduler,
    ): ReaderRouteCoordinator = ReaderRouteCoordinator(
        store = store,
        sources = sources,
        progress = progress,
        sourceAvailability = sourceAvailability,
        healthRegistry = healthRegistry,
        executionLimiter = executionLimiter,
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
    ): ReaderRouteSessionFactory = ReaderRouteSessionFactory(coordinator, prefetchCoordinator)
}
