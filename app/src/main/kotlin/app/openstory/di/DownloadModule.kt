package app.openstory.di

import android.content.Context
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadFetchResult
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import app.openstory.downloads.cache.CacheRepository
import app.openstory.storage.files.AtomicFileChapterBlobStore
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

    @Provides @Singleton
    fun provideRoomDownloadRepository(database: OpenStoryDatabase) = RoomDownloadRepository(database)

    @Provides fun provideCacheRepository(repository: RoomDownloadRepository): CacheRepository = repository
    @Provides fun provideDownloadRepository(repository: RoomDownloadRepository): DownloadRepository = repository

    @Provides
    fun provideDownloadContentSource(): DownloadContentSource = DownloadContentSource {
        DownloadFetchResult.Failure("download.source_unavailable", retryable = false)
    }

    @Provides @Singleton
    fun provideDownloadService(repository: DownloadRepository, store: ChapterBlobStore, source: DownloadContentSource) =
        DownloadService(repository, store, source)

    @Provides
    fun provideDownloadScheduler(@ApplicationContext context: Context): DownloadScheduler =
        WorkManagerDownloadScheduler(context)
}
