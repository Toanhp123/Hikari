package app.openstory.di

import android.content.Context
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomCatalogRepository
import app.openstory.storage.room.catalog.RoomCatalogStoryProjectionRepository
import app.openstory.storage.room.plugins.RoomPluginDiagnosticsSink
import app.openstory.storage.room.plugins.RoomPluginStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenStoryDatabase =
        OpenStoryDatabase.open(context)

    @Provides
    @Singleton
    fun provideCatalogRepository(database: OpenStoryDatabase): CatalogRepository =
        RoomCatalogRepository(database)

    @Provides
    @Singleton
    fun provideCatalogStoryProjectionRepository(
        database: OpenStoryDatabase,
    ): CatalogStoryProjectionRepository = RoomCatalogStoryProjectionRepository(database)

    @Provides
    @Singleton
    fun providePluginStateStore(database: OpenStoryDatabase): PluginStateStore =
        RoomPluginStateStore(database)

    @Provides
    @Singleton
    fun providePluginDiagnosticsSink(database: OpenStoryDatabase): PluginDiagnosticsSink =
        RoomPluginDiagnosticsSink(database)
}
