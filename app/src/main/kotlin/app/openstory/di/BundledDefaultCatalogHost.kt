package app.openstory.di

import android.content.Context
import app.openstory.common.AppResult
import app.openstory.model.PluginId
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.api.selector.SelectorDefinition
import app.openstory.plugin.api.selector.SelectorDefinitionDecoder
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHost
import app.openstory.plugin.host.PluginHostLoadException
import app.openstory.plugin.host.install.AndroidBundledPluginAssets
import app.openstory.plugin.host.install.DefaultCatalogBundledPlugin
import app.openstory.plugin.host.install.JavaScriptCatalogBundledPlugin
import app.openstory.plugin.host.install.PackageVerifier
import app.openstory.plugin.host.install.ZipPackageArchiveInspector
import app.openstory.plugin.host.js.JsIsolateExecutor
import app.openstory.plugin.host.selector.runtime.SelectorPluginFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class BundledDefaultCatalogHost(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    javaScriptExecutor: JsIsolateExecutor,
) : PluginHost {
    private val applicationContext = context.applicationContext
    private val assets = AndroidBundledPluginAssets(
        context = applicationContext,
        descriptors = listOf(DefaultCatalogBundledPlugin.descriptor),
        ioDispatcher = ioDispatcher,
    )
    private val fixtureGateway = BundledCatalogFixtureGateway(
        context = applicationContext,
        ioDispatcher = ioDispatcher,
    )
    private val javaScriptCatalog = BundledJavaScriptCatalogLoader(
        context = applicationContext,
        ioDispatcher = ioDispatcher,
        executor = javaScriptExecutor,
    )
    private val myAnimeListCatalog = BundledMyAnimeListCatalogLoader(
        context = applicationContext,
        ioDispatcher = ioDispatcher,
        executor = javaScriptExecutor,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    private var loadedCatalog: HostedPlugin<CatalogPlugin>? = null

    override suspend fun catalog(id: PluginId): HostedPlugin<CatalogPlugin> = when (id.value) {
        DefaultCatalogBundledPlugin.PLUGIN_ID -> loadCatalog()
        JavaScriptCatalogBundledPlugin.PLUGIN_ID -> javaScriptCatalog.load()
        MyAnimeListCatalogBundledPlugin.PLUGIN_ID -> myAnimeListCatalog.load()
        else -> throw notInstalled(id)
    }

    override suspend fun content(id: PluginId): HostedPlugin<ContentPlugin> =
        throw notInstalled(id)

    override suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>> = listOfNotNull(
        loadCatalogSafely { myAnimeListCatalog.load() },
    )

    override suspend fun enabledContentSources(): List<HostedPlugin<ContentPlugin>> = emptyList()

    private suspend fun loadCatalog(): HostedPlugin<CatalogPlugin> =
        loadedCatalog ?: loadMutex.withLock {
            loadedCatalog ?: createCatalogSafely().also { hosted ->
                loadedCatalog = hosted
            }
        }

    private suspend fun createCatalogSafely(): HostedPlugin<CatalogPlugin> =
        try {
            createCatalog()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: PluginHostLoadException) {
            throw failure
        } catch (_: Exception) {
            throw PluginHostLoadException(
                pluginId = PluginId(DefaultCatalogBundledPlugin.PLUGIN_ID),
                errorCode = BUNDLED_RUNTIME_LOAD_FAILED,
            )
        }

    private suspend fun createCatalog(): HostedPlugin<CatalogPlugin> {
        val bundledPackage = verifiedBundledPackage()
        val packageEntries = readRuntimeEntries(bundledPackage.installRequest.packageBytes)
        val manifest = json.decodeFromString(
            PluginManifest.serializer(),
            packageEntries.manifest,
        )
        val definition = decodeSelectorDefinition(
            manifest = manifest,
            selectorSource = packageEntries.selector,
        )
        val catalog = createCatalogInstance(
            manifest = manifest,
            definition = definition,
        )

        return HostedPlugin(
            id = PluginId(manifest.id),
            version = manifest.version,
            instance = catalog,
        )
    }

    private suspend fun verifiedBundledPackage() = assets.packages().single().also { bundledPackage ->
        val verified = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(bundledPackage.installRequest)
        if (verified is AppResult.Failure) {
            throw PluginHostLoadException(
                pluginId = PluginId(DefaultCatalogBundledPlugin.PLUGIN_ID),
                errorCode = verified.error.code,
            )
        }
    }

    private fun decodeSelectorDefinition(
        manifest: PluginManifest,
        selectorSource: String,
    ) = SelectorDefinitionDecoder()
        .decode(selectorSource)
        .getOrElse {
            throw PluginHostLoadException(
                pluginId = PluginId(manifest.id),
                errorCode = SELECTOR_DEFINITION_INVALID,
            )
        }

    private fun createCatalogInstance(
        manifest: PluginManifest,
        definition: SelectorDefinition,
    ): CatalogPlugin {
        val plugins = when (
            val created = SelectorPluginFactory().create(
                manifest = manifest,
                definition = definition,
                http = fixtureGateway,
            )
        ) {
            is AppResult.Success -> created.value
            is AppResult.Failure -> throw PluginHostLoadException(
                pluginId = PluginId(manifest.id),
                errorCode = created.error.code,
            )
        }

        return plugins.catalog ?: throw PluginHostLoadException(
            pluginId = PluginId(manifest.id),
            errorCode = CATALOG_RUNTIME_MISSING,
        )
    }

    private companion object {
        const val BUNDLED_RUNTIME_LOAD_FAILED = "plugin.bundled_runtime_load_failed"
        const val CATALOG_RUNTIME_MISSING = "plugin.catalog_runtime_missing"
        const val SELECTOR_DEFINITION_INVALID = "plugin.selector_definition_invalid"
    }
}

private suspend fun loadCatalogSafely(
    load: suspend () -> HostedPlugin<CatalogPlugin>,
): HostedPlugin<CatalogPlugin>? = try {
    load()
} catch (failure: CancellationException) {
    throw failure
} catch (_: Exception) {
    null
}

private fun readRuntimeEntries(packageBytes: ByteArray): BundledRuntimeEntries {
    var manifest: String? = null
    var selector: String? = null

    ZipInputStream(ByteArrayInputStream(packageBytes)).use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            when (entry.name) {
                MANIFEST_ENTRY -> manifest = archive.readBytes().decodeToString()
                SELECTOR_ENTRY -> selector = archive.readBytes().decodeToString()
            }
            archive.closeEntry()
            entry = archive.nextEntry
        }
    }

    return BundledRuntimeEntries(
        manifest = manifest ?: throw invalidBundledPackage(),
        selector = selector ?: throw invalidBundledPackage(),
    )
}

private fun invalidBundledPackage(): PluginHostLoadException = PluginHostLoadException(
    pluginId = PluginId(DefaultCatalogBundledPlugin.PLUGIN_ID),
    errorCode = BUNDLED_PACKAGE_INVALID,
)

private fun notInstalled(id: PluginId): PluginHostLoadException = PluginHostLoadException(
    pluginId = id,
    errorCode = PLUGIN_NOT_INSTALLED,
)

private data class BundledRuntimeEntries(
    val manifest: String,
    val selector: String,
)

private const val MANIFEST_ENTRY = "manifest.json"
private const val SELECTOR_ENTRY = "selector.json"
private const val PLUGIN_NOT_INSTALLED = "plugin.not_installed"
private const val BUNDLED_PACKAGE_INVALID = "plugin.bundled_package_invalid"
