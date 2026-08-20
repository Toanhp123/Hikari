package app.openstory.di

import app.openstory.catalog.metadata.CatalogMetadataScope
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.common.dispatchers.FixedAppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers =
        FixedAppDispatchers(
            io = Dispatchers.IO,
            default = Dispatchers.Default,
            main = Dispatchers.Main.immediate,
        )

    @Provides
    @Singleton
    @CatalogMetadataScope
    fun provideCatalogMetadataScope(
        dispatchers: AppDispatchers,
    ): CoroutineScope = CoroutineScope(
        SupervisorJob() + dispatchers.io,
    )
}
