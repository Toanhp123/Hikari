package app.openstory.benchmark

import android.content.Context
import android.content.Intent
import app.openstory.R
import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.PluginId
import app.openstory.di.ExclusiveReaderAssetDeliverySources
import app.openstory.di.ExclusiveReaderDocumentSources
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.assets.ReaderAssetDeliveryRequest
import app.openstory.reader.assets.ReaderAssetDeliveryResult
import app.openstory.reader.assets.ReaderAssetDeliverySource
import app.openstory.reader.assets.ReaderAssetPayload
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI

internal val BENCHMARK_READER_PLUGIN_ID = PluginId("benchmark-fixture")
internal const val BENCHMARK_READER_IMAGE_SOURCE_PREFIX = "benchmark-image-pages:"
internal const val BENCHMARK_READER_CACHE_MODE_EXTRA = "app.openstory.benchmark.READER_CACHE_MODE"
private const val BENCHMARK_READER_ASSET_HOST = "benchmark.local"
private const val BENCHMARK_READER_ASSET_PATH_PREFIX = "/reader-assets/v1/"

internal enum class BenchmarkReaderCacheMode {
    NONE,
    COLD,
    WARM;

    companion object {
        fun from(intent: Intent): BenchmarkReaderCacheMode = intent
            .getStringExtra(BENCHMARK_READER_CACHE_MODE_EXTRA)
            ?.let { value -> entries.firstOrNull { mode -> mode.name == value } }
            ?: NONE
    }
}

internal class BenchmarkReaderDocumentSource : ReaderDocumentSource {
    override val pluginId: PluginId = BENCHMARK_READER_PLUGIN_ID
    override val imageSourcePolicy = ReaderImageSourcePolicy(
        identityContract = ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
        locatorContract = ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN,
        persistenceContract = ReaderImagePersistenceContract.PUBLIC,
    )

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult =
        benchmarkReaderImageDocument(release)
            ?.let(ReaderSourceResult::Success)
            ?: ReaderSourceResult.Failure("reader.benchmark_fixture_missing", retryable = false)
}

internal class BenchmarkReaderAssetDelivery(
    context: Context,
) : ReaderAssetDeliverySource {
    override val id: String = "benchmark-reader-assets"
    private val payload by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.resources.openRawResource(R.drawable.benchmark_reader_page).use { input ->
            ReaderAssetPayload.verifiedBounded(
                bytes = input.readBytes(),
                mimeType = "image/png",
                sourceIntegrityEvidence = null,
            )
        }
    }

    override fun matches(request: ReaderAssetDeliveryRequest): Boolean {
        val uri = runCatching { URI(request.deliveryLocator) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(BENCHMARK_READER_ASSET_HOST, ignoreCase = true) &&
            uri.rawPath.startsWith(BENCHMARK_READER_ASSET_PATH_PREFIX)
    }

    override suspend fun fetch(request: ReaderAssetDeliveryRequest): ReaderAssetDeliveryResult =
        ReaderAssetDeliveryResult.Success(payload)
}

internal fun benchmarkReaderImageDocument(release: ChapterRelease): ReaderDocument? {
    if (release.pluginId != BENCHMARK_READER_PLUGIN_ID) return null
    val pageCount = release.sourceReleaseId
        .takeIf { it.startsWith(BENCHMARK_READER_IMAGE_SOURCE_PREFIX) }
        ?.removePrefix(BENCHMARK_READER_IMAGE_SOURCE_PREFIX)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: return null
    return ReaderDocument(
        title = "Benchmark Chapter 1 Images",
        blocks = List(pageCount) { ordinal ->
            ReaderBlock.ImagePage(
                id = "benchmark-reader-image-$ordinal",
                stableAssetId = "benchmark-reader-page-v1/$ordinal.png",
                imageUrl = "https://$BENCHMARK_READER_ASSET_HOST$BENCHMARK_READER_ASSET_PATH_PREFIX$ordinal.png",
            )
        },
        fingerprint = "benchmark-reader-image-document-v1:$pageCount",
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal object BenchmarkReaderSourceModule {
    @Provides
    @IntoSet
    @ExclusiveReaderDocumentSources
    fun provideBenchmarkReaderDocumentSource(): ReaderDocumentSource = BenchmarkReaderDocumentSource()

    @Provides
    @IntoSet
    @ExclusiveReaderAssetDeliverySources
    fun provideBenchmarkReaderAssetDelivery(
        @ApplicationContext context: Context,
    ): ReaderAssetDeliverySource = BenchmarkReaderAssetDelivery(context)
}
