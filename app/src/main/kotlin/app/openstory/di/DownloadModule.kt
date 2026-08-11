package app.openstory.di

import android.content.Context
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.storage.files.AtomicFileChapterBlobStore
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
}
