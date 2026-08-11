package app.openstory.di

import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.reader.content.NoOpReaderDocumentStore
import app.openstory.reader.content.PluginReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentRepository
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.document.ReaderDocumentSanitizer
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.selection.ReleaseSelector
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.reader.RoomReadingProgressRepository
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
    fun provideReaderDocumentStore(): ReaderDocumentStore = NoOpReaderDocumentStore

    @Provides
    @Singleton
    fun provideReaderDocumentSourceRegistry(
        runtime: PluginRuntime,
        json: Json,
    ): ReaderDocumentSourceRegistry = PluginReaderDocumentSourceRegistry(
        runtime,
        json,
        ReaderDocumentSanitizer(),
    )

    @Provides
    @Singleton
    fun provideReaderDocumentRepository(
        store: ReaderDocumentStore,
        sources: ReaderDocumentSourceRegistry,
    ): ReaderDocumentRepository = ReaderDocumentRepository(store, sources, ReleaseSelector())

    @Provides
    @Singleton
    fun provideReadingProgressRepository(database: OpenStoryDatabase): ReadingProgressRepository =
        RoomReadingProgressRepository(database)
}
