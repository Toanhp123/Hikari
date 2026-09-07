package app.openstory.di

import app.openstory.reader.assets.ReaderAssetDeliverySource
import app.openstory.reader.content.ReaderDocumentSource
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExclusiveReaderDocumentSources

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExclusiveReaderAssetDeliverySources

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderSourceContributionModule {
    @Multibinds
    @ExclusiveReaderDocumentSources
    abstract fun documentSources(): Set<ReaderDocumentSource>

    @Multibinds
    @ExclusiveReaderAssetDeliverySources
    abstract fun assetDeliverySources(): Set<ReaderAssetDeliverySource>
}
