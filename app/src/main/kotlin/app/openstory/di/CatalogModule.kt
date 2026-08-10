package app.openstory.di

import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.PluginCatalogSourceRegistry
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.plugins.runtime.PluginRuntime
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    fun provideClock(): Clock = SystemClock

    @Provides
    fun provideStoryMatcher(): StoryMatcher = StoryMatcher()

    @Provides
    fun provideAggregateRanking(): AggregateRanking = AggregateRanking()

    @Provides
    @Singleton
    fun provideCatalogSourceRegistry(
        runtime: PluginRuntime,
        json: Json,
    ): CatalogSourceRegistry = PluginCatalogSourceRegistry(runtime, json)
}
