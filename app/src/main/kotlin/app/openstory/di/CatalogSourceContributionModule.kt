package app.openstory.di

import app.openstory.catalog.source.CatalogSource
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExclusiveCatalogSources

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogSourceContributionModule {
    @Multibinds
    @ExclusiveCatalogSources
    abstract fun catalogSources(): Set<CatalogSource>
}
