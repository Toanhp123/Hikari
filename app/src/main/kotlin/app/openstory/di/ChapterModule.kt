package app.openstory.di

import android.content.Context
import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.maintenance.ChapterReaggregationService
import app.openstory.chapters.maintenance.ChapterReaggregator
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterReleaseLookup
import app.openstory.chapters.source.ChapterSourceRegistry
import app.openstory.chapters.source.PluginChapterSourceRegistry
import app.openstory.chapters.sync.ChapterSyncService
import app.openstory.chapters.sync.InitialChapterSyncScheduler
import app.openstory.common.Clock
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.chapters.RoomChapterRepository
import app.openstory.work.WorkManagerInitialChapterSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object ChapterModule {
    @Provides
    @Singleton
    fun provideChapterRepository(database: OpenStoryDatabase): ChapterRepository =
        RoomChapterRepository(database)

    @Provides
    @Singleton
    fun provideChapterReleaseLookup(database: OpenStoryDatabase): ChapterReleaseLookup =
        RoomChapterRepository(database)

    @Provides
    @Singleton
    fun provideChapterReaggregator(chapters: ChapterRepository): ChapterReaggregator =
        ChapterReaggregationService(chapters)

    @Provides
    @Singleton
    fun provideChapterSourceRegistry(
        runtime: PluginRuntime,
        json: Json,
    ): ChapterSourceRegistry = PluginChapterSourceRegistry(runtime, json)

    @Provides
    @Singleton
    fun provideChapterSyncService(
        mappings: ContentMappingRepository,
        sources: ChapterSourceRegistry,
        chapters: ChapterRepository,
        clock: Clock,
    ): ChapterSyncService = ChapterSyncService(
        mappings = mappings,
        sources = sources,
        chapters = chapters,
        aggregation = ChapterAggregationEngine(),
        parser = ChapterLabelParser(),
        clock = clock,
    )

    @Provides
    @Singleton
    fun provideInitialChapterSyncScheduler(
        @ApplicationContext context: Context,
    ): InitialChapterSyncScheduler = WorkManagerInitialChapterSyncScheduler(context)
}
