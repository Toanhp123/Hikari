package app.openstory.reader.assets

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ReaderAssetImageLoaderInstaller @Inject constructor(
    private val coordinatorProvider: Provider<ReaderAssetCoordinator>,
) {
    fun install() {
        SingletonImageLoader.setSafe { context -> createImageLoader(context) }
    }

    internal fun createImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(ReaderPageAssetKeyer())
            add(
                ReaderAssetCoilFetcher.Factory { request ->
                    coordinatorProvider.get().requestPage(request)
                },
            )
        }
        .build()
}
