package app.openstory.di

import android.content.Context
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.reader.ReaderDownloadContentSource
import app.openstory.downloads.reader.ReaderCacheMetadataSource
import app.openstory.downloads.reconcile.StorageReconciliationInventory
import app.openstory.downloads.reconcile.StorageReconciliationRepository
import app.openstory.downloads.reconcile.StorageReconciliationService
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.chapters.repository.ChapterReleaseLookup
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.storage.files.AtomicFileChapterBlobStore
import app.openstory.storage.files.FileBlobInventory
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.downloads.RoomDownloadRepository
import app.openstory.work.WorkManagerDownloadScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideChapterBlobStore(@ApplicationContext context: Context): ChapterBlobStore =
        AtomicFileChapterBlobStore(context)

    @Provides
    @Singleton
    fun provideFileBlobInventory(@ApplicationContext context: Context) = FileBlobInventory(context)

    @Provides
    fun provideStorageReconciliationInventory(
        inventory: FileBlobInventory,
    ): StorageReconciliationInventory = inventory

    @Provides
    fun provideStorageWriteAdmission(inventory: FileBlobInventory): StorageWriteAdmission = inventory

    @Provides @Singleton
    fun provideRoomDownloadRepository(database: OpenStoryDatabase) = RoomDownloadRepository(database)

    @Provides fun provideCacheRepository(repository: RoomDownloadRepository): CacheRepository = repository
    @Provides fun provideDownloadRepository(repository: RoomDownloadRepository): DownloadRepository = repository
    @Provides
    fun provideReaderCacheMetadataSource(repository: RoomDownloadRepository): ReaderCacheMetadataSource = repository
    @Provides
    fun provideStorageReconciliationRepository(
        repository: RoomDownloadRepository,
    ): StorageReconciliationRepository = repository

    @Provides
    fun provideDownloadContentSource(
        chapters: ChapterReleaseLookup,
        sources: ReaderDocumentSourceRegistry,
        availability: ReaderSourceAvailability,
    ): DownloadContentSource = ReaderDownloadContentSource(chapters, sources, availability)

    @Provides @Singleton
    fun provideDownloadService(
        repository: DownloadRepository,
        store: ChapterBlobStore,
        source: DownloadContentSource,
        writeAdmission: StorageWriteAdmission,
    ) = DownloadService(repository, store, source, writeAdmission)

    @Provides
    @Singleton
    fun provideStorageReconciliationService(
        repository: StorageReconciliationRepository,
        inventory: StorageReconciliationInventory,
    ) = StorageReconciliationService(
        repository = repository,
        inventory = inventory,
        activeWriteWindowMillis = ACTIVE_WRITE_WINDOW_MILLIS,
        now = System::currentTimeMillis,
    )

    @Provides
    fun provideDownloadScheduler(@ApplicationContext context: Context): DownloadScheduler =
        WorkManagerDownloadScheduler(context)

    private const val ACTIVE_WRITE_WINDOW_MILLIS = 15L * 60 * 1000
}
