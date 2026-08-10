package app.openstory.di

import android.content.Context
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.content.ContentSourceRegistry
import app.openstory.library.content.PluginContentSourceRegistry
import app.openstory.library.mapping.ContentMappingSearchService
import app.openstory.library.matching.ContentStoryMatcher
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.library.RoomLibraryRepository
import app.openstory.work.WorkManagerLibraryMappingScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideLibraryRepository(database: OpenStoryDatabase): LibraryRepository =
        RoomLibraryRepository(database)

    @Provides
    @Singleton
    fun provideContentSourceRegistry(
        runtime: PluginRuntime,
        json: Json,
    ): ContentSourceRegistry = PluginContentSourceRegistry(runtime, json)

    @Provides
    @Singleton
    fun provideContentMappingSearchService(
        projections: CatalogStoryProjectionRepository,
        sources: ContentSourceRegistry,
    ): ContentMappingSearchService = ContentMappingSearchService(
        projections = projections,
        sources = sources,
        matcher = ContentStoryMatcher(),
    )

    @Provides
    @Singleton
    fun provideLibraryMappingScheduler(
        @ApplicationContext context: Context,
    ): LibraryMappingScheduler = WorkManagerLibraryMappingScheduler(context)
}
