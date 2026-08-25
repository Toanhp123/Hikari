package app.openstory.di

import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.reader.content.PluginReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentRepository
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.document.ReaderDocumentSanitizer
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.routing.ReaderRouteCoordinator
import app.openstory.reader.routing.ReaderSourceExecutionLimiter
import app.openstory.reader.routing.ReaderSourceHealthRegistry
import app.openstory.reader.routing.ReaderRouteSessionFactory
import app.openstory.reader.selection.ReleaseSelector
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.reader.RoomReadingProgressRepository
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.reader.DownloadAwareReaderDocumentStore
import app.openstory.downloads.reconcile.StorageWriteAdmission
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {
    @Provides
    @Singleton
    fun provideReaderDocumentStore(
        blobs: ChapterBlobStore,
        cache: CacheRepository,
        downloads: DownloadRepository,
        writeAdmission: StorageWriteAdmission,
    ): ReaderDocumentStore = DownloadAwareReaderDocumentStore(
        blobs,
        cache,
        downloads,
        System::currentTimeMillis,
        writeAdmission,
    )

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
    fun provideReaderDocumentRepository(
        store: ReaderDocumentStore,
        sources: ReaderDocumentSourceRegistry,
        executionLimiter: ReaderSourceExecutionLimiter,
    ): ReaderDocumentRepository = ReaderDocumentRepository(
        store,
        sources,
        ReleaseSelector(),
        executionLimiter,
    )

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
    ): ReaderRouteCoordinator = ReaderRouteCoordinator(
        store = store,
        sources = sources,
        progress = progress,
        sourceAvailability = sourceAvailability,
        healthRegistry = healthRegistry,
        executionLimiter = executionLimiter,
    )

    @Provides
    @Singleton
    fun provideReaderRouteSessionFactory(
        coordinator: ReaderRouteCoordinator,
    ): ReaderRouteSessionFactory = ReaderRouteSessionFactory(coordinator)
}
