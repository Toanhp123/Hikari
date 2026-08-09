package app.openstory.di

import android.content.Context
import app.openstory.common.AppResult
import app.openstory.model.PluginId
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.PluginUrlPolicy
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHostLoadException
import app.openstory.plugin.host.install.AndroidBundledPluginAssets
import app.openstory.plugin.host.install.JavaScriptCatalogBundledPlugin
import app.openstory.plugin.host.install.PackageVerifier
import app.openstory.plugin.host.install.ZipPackageArchiveInspector
import app.openstory.plugin.host.js.JavaScriptCatalogPlugin
import app.openstory.plugin.host.js.JavaScriptPluginRuntime
import app.openstory.plugin.host.js.JsIsolateExecutor
import app.openstory.plugin.host.js.JsCapabilityDispatcher
import app.openstory.plugin.host.js.JsWireDtoDecoder
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class BundledJavaScriptCatalogLoader(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val executor: JsIsolateExecutor,
) {
    private val applicationContext = context.applicationContext
    private val assets = AndroidBundledPluginAssets(
        context = applicationContext,
        descriptors = listOf(JavaScriptCatalogBundledPlugin.descriptor),
        ioDispatcher = ioDispatcher,
    )
    private val fixtureGateway = BundledCatalogFixtureGateway(
        context = applicationContext,
        ioDispatcher = ioDispatcher,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    private var loadedCatalog: HostedPlugin<CatalogPlugin>? = null

    suspend fun load(): HostedPlugin<CatalogPlugin> = loadedCatalog ?: loadMutex.withLock {
        loadedCatalog ?: createCatalogSafely().also { hosted -> loadedCatalog = hosted }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun createCatalogSafely(): HostedPlugin<CatalogPlugin> {
        val failure = try {
            return createCatalog()
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (failure: Exception) {
            failure
        }

        throw if (failure is PluginHostLoadException) {
            failure
        } else {
            PluginHostLoadException(
                pluginId = PluginId(JavaScriptCatalogBundledPlugin.PLUGIN_ID),
                errorCode = "plugin.bundled_runtime_load_failed",
            )
        }
    }

    private suspend fun createCatalog(): HostedPlugin<CatalogPlugin> {
        val bundledPackage = assets.packages().single()
        val verified = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(bundledPackage.installRequest)
        if (verified is AppResult.Failure) {
            throw PluginHostLoadException(
                pluginId = PluginId(JavaScriptCatalogBundledPlugin.PLUGIN_ID),
                errorCode = verified.error.code,
            )
        }

        val entries = readJavaScriptRuntimeEntries(bundledPackage.installRequest.packageBytes)
        val manifest = json.decodeFromString(PluginManifest.serializer(), entries.manifest)
        val scopedGateway = ManifestScopedFixtureGateway(
            allowedHosts = manifest.allowedHosts,
            delegate = fixtureGateway,
        )
        val runtime = JavaScriptPluginRuntime(
            executor = executor,
            dispatcher = JsCapabilityDispatcher(manifest, scopedGateway),
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
}

private fun readJavaScriptRuntimeEntries(packageBytes: ByteArray): JavaScriptRuntimeEntries {
    var manifest: String? = null
    var mainJs: String? = null

    ZipInputStream(ByteArrayInputStream(packageBytes)).use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            when (entry.name) {
                JAVASCRIPT_MANIFEST_ENTRY -> manifest = archive.readBytes().decodeToString()
                JAVASCRIPT_MAIN_ENTRY -> mainJs = archive.readBytes().decodeToString()
            }
            archive.closeEntry()
            entry = archive.nextEntry
        }
    }

    return JavaScriptRuntimeEntries(
        manifest = manifest ?: throw invalidJavaScriptPackage(),
        mainJs = mainJs ?: throw invalidJavaScriptPackage(),
    )
}

private fun invalidJavaScriptPackage(): PluginHostLoadException = PluginHostLoadException(
    pluginId = PluginId(JavaScriptCatalogBundledPlugin.PLUGIN_ID),
    errorCode = "plugin.bundled_package_invalid",
)

private data class JavaScriptRuntimeEntries(
    val manifest: String,
    val mainJs: String,
)

private const val JAVASCRIPT_MANIFEST_ENTRY = "manifest.json"
private const val JAVASCRIPT_MAIN_ENTRY = "main.js"


private class ManifestScopedFixtureGateway(
    allowedHosts: Set<String>,
    private val delegate: PluginHttpGateway,
) : PluginHttpGateway {
    private val urlPolicy = PluginUrlPolicy(allowedHosts)

    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> = when (val validated = urlPolicy.resolve(request.url)) {
        is AppResult.Failure -> validated
        is AppResult.Success -> delegate.execute(
            request = request.copy(url = validated.value.value),
            budget = budget,
        )
    }
}
