package app.openstory.di

import android.content.Context
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryUserStateFootprintReader
import app.openstory.catalog.orchestration.CanonicalEngineMaintenanceReader
import app.openstory.catalog.orchestration.CanonicalEngineWorkRepository
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.Clock
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.StoryMergeLineageReader
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import app.openstory.plugins.runtime.persistence.PluginStateStore
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomCatalogRepository
import app.openstory.storage.room.catalog.RoomReconciliationCaseRepository
import app.openstory.storage.room.catalog.RoomCatalogStoryProjectionRepository
import app.openstory.storage.room.catalog.RoomCanonicalCatalogRepository
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinator
import app.openstory.storage.room.merge.RoomStoryMergeLineageReader
import app.openstory.storage.room.merge.RoomStoryUserStateFootprintReader
import app.openstory.storage.room.catalog.RoomCanonicalEngineMaintenanceReader
import app.openstory.storage.room.catalog.RoomCanonicalEngineWorkRepository
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
    fun provideCanonicalCatalogRepository(database: OpenStoryDatabase): CanonicalCatalogRepository =
        RoomCanonicalCatalogRepository(database)

    @Provides
    @Singleton
    fun provideStoryIdentityRepository(database: OpenStoryDatabase): StoryIdentityRepository =
        RoomStoryIdentityResolver(database)

    @Provides
    @Singleton
    fun provideCanonicalEngineWorkRepository(
        database: OpenStoryDatabase,
        clock: Clock,
    ): CanonicalEngineWorkRepository = RoomCanonicalEngineWorkRepository(database, clock)

    @Provides
    @Singleton
    fun provideCanonicalEngineMaintenanceReader(database: OpenStoryDatabase): CanonicalEngineMaintenanceReader =
        RoomCanonicalEngineMaintenanceReader(database)

    @Provides
    @Singleton
    fun provideStoryMergeExecutor(
        database: OpenStoryDatabase,
        clock: Clock,
    ): StoryMergeExecutor = RoomStoryGraphMergeCoordinator(database, clock)

    @Provides
    @Singleton
    fun provideReconciliationCaseRepository(database: OpenStoryDatabase): ReconciliationCaseRepository =
        RoomReconciliationCaseRepository(database)

    @Provides
    @Singleton
    fun provideStoryMergeLineageReader(database: OpenStoryDatabase): StoryMergeLineageReader =
        RoomStoryMergeLineageReader(database)

    @Provides
    @Singleton
    fun provideStoryUserStateFootprintReader(database: OpenStoryDatabase): StoryUserStateFootprintReader =
        RoomStoryUserStateFootprintReader(database)

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
