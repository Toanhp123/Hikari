package app.openstory.di

import android.content.Context
import app.openstory.BuildConfig
import app.openstory.common.AppResult
import app.openstory.common.SystemClock
import app.openstory.model.PluginId
import app.openstory.network.AllowlistedHttpGateway
import app.openstory.network.PerPluginTokenBucket
import app.openstory.network.PluginSessionStore
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHostLoadException
import app.openstory.plugin.host.install.AndroidBundledPluginAssets
import app.openstory.plugin.host.install.PackageVerifier
import app.openstory.plugin.host.install.ZipPackageArchiveInspector
import app.openstory.plugin.host.js.JavaScriptCatalogPlugin
import app.openstory.plugin.host.js.JavaScriptPluginRuntime
import app.openstory.plugin.host.js.JsCapabilityDispatcher
import app.openstory.plugin.host.js.JsIsolateExecutor
import app.openstory.plugin.host.js.JsWireDtoDecoder
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.OkHttpClient

internal class BundledMyAnimeListCatalogLoader(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val executor: JsIsolateExecutor,
) {
    private val applicationContext = context.applicationContext
    private val assets = AndroidBundledPluginAssets(
        context = applicationContext,
        descriptors = listOf(MyAnimeListCatalogBundledPlugin.descriptor),
        ioDispatcher = ioDispatcher,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    private var loadedCatalog: HostedPlugin<CatalogPlugin>? = null

    suspend fun load(): HostedPlugin<CatalogPlugin> = loadedCatalog ?: loadMutex.withLock {
        loadedCatalog ?: createCatalogSafely().also { hosted -> loadedCatalog = hosted }
    }

    private suspend fun createCatalogSafely(): HostedPlugin<CatalogPlugin> = try {
        createCatalog()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: PluginHostLoadException) {
        throw failure
    } catch (_: Exception) {
        throw PluginHostLoadException(
            pluginId = PluginId(MyAnimeListCatalogBundledPlugin.PLUGIN_ID),
            errorCode = BUNDLED_RUNTIME_LOAD_FAILED,
        )
    }

    private suspend fun createCatalog(): HostedPlugin<CatalogPlugin> {
        val bundledPackage = assets.packages().single()
        val verified = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(bundledPackage.installRequest)
        if (verified is AppResult.Failure) {
            throw PluginHostLoadException(
                pluginId = PluginId(MyAnimeListCatalogBundledPlugin.PLUGIN_ID),
                errorCode = verified.error.code,
            )
        }

        val entries = readMyAnimeListRuntimeEntries(
            bundledPackage.installRequest.packageBytes,
        )
        val manifest = json.decodeFromString(
            PluginManifest.serializer(),
            entries.manifest,
        )
        val baseGateway = AllowlistedHttpGateway(
            client = OkHttpClient(),
            pluginId = manifest.id,
            allowedHosts = manifest.allowedHosts,
            sessionStore = EmptyPluginSessionStore,
            rateLimiter = PerPluginTokenBucket(
                capacity = MAL_BURST_REQUESTS,
                refillTokens = MAL_REFILL_TOKENS,
                refillPeriodMillis = MAL_REFILL_PERIOD_MILLIS,
                clock = SystemClock,
            ),
        )
        val gateway = MyAnimeListHttpGateway(
            clientId = BuildConfig.MYANIMELIST_CLIENT_ID,
            delegate = baseGateway,
        )
        val runtime = JavaScriptPluginRuntime(
            executor = executor,
            dispatcher = JsCapabilityDispatcher(manifest, gateway),
        )
        val decoder = JsWireDtoDecoder(
            PluginWireDtoValidator(PluginUrlPolicy(manifest.allowedHosts)),
        )

        return HostedPlugin(
            id = PluginId(manifest.id),
            version = manifest.version,
            instance = JavaScriptCatalogPlugin(
                source = entries.mainJs,
                runtime = runtime,
                decoder = decoder,
            ),
        )
    }

    private companion object {
        const val BUNDLED_RUNTIME_LOAD_FAILED = "plugin.bundled_runtime_load_failed"
        const val MAL_BURST_REQUESTS = 3
        const val MAL_REFILL_TOKENS = 1
        const val MAL_REFILL_PERIOD_MILLIS = 1_000L
    }
}

private fun readMyAnimeListRuntimeEntries(packageBytes: ByteArray): MyAnimeListRuntimeEntries {
    var manifest: String? = null
    var mainJs: String? = null

    ZipInputStream(ByteArrayInputStream(packageBytes)).use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            when (entry.name) {
                MAL_MANIFEST_ENTRY -> manifest = archive.readBytes().decodeToString()
                MAL_MAIN_ENTRY -> mainJs = archive.readBytes().decodeToString()
            }
            archive.closeEntry()
            entry = archive.nextEntry
        }
    }

    return MyAnimeListRuntimeEntries(
        manifest = manifest ?: throw invalidMyAnimeListPackage(),
        mainJs = mainJs ?: throw invalidMyAnimeListPackage(),
    )
}

private fun invalidMyAnimeListPackage(): PluginHostLoadException = PluginHostLoadException(
    pluginId = PluginId(MyAnimeListCatalogBundledPlugin.PLUGIN_ID),
    errorCode = "plugin.bundled_package_invalid",
)

private data class MyAnimeListRuntimeEntries(
    val manifest: String,
    val mainJs: String,
)

private object EmptyPluginSessionStore : PluginSessionStore {
    override fun load(pluginId: String, host: String): List<Cookie> = emptyList()

    override fun save(pluginId: String, host: String, cookies: List<Cookie>) = Unit
}

private const val MAL_MANIFEST_ENTRY = "manifest.json"
private const val MAL_MAIN_ENTRY = "main.js"
