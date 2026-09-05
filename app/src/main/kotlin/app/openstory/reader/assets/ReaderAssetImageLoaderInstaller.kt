package app.openstory.reader.assets

import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ReaderAssetImageLoaderInstaller @Inject constructor(
    private val coordinatorProvider: Provider<ReaderAssetCoordinator>,
    private val diagnostics: ReaderAssetDiagnosticsSink,
) {
    fun install() {
        SingletonImageLoader.setSafe { context -> createImageLoader(context) }
    }

    internal fun createImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .eventListener(ReaderAssetImageLoaderDiagnosticsListener(diagnostics))
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

internal class ReaderAssetImageLoaderDiagnosticsListener(
    private val diagnostics: ReaderAssetDiagnosticsSink,
) : EventListener() {
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        if (request.data is ReaderPageAssetRequest && result.dataSource == DataSource.MEMORY_CACHE) {
            diagnostics.recordSafely(ReaderAssetDiagnosticEvent.MemoryHit)
        }
    }
}
