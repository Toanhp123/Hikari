package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.AppResult
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.di.MyAnimeListCatalogBundledPlugin
import app.openstory.di.OpenStoryAppGraph
import app.openstory.home.model.HomeRefreshReport
import app.openstory.home.ui.HomeViewModel
import app.openstory.model.PluginId
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHost
import app.openstory.plugin.host.PluginHostLoadException
import app.openstory.plugin.host.install.DefaultCatalogBundledPlugin
import app.openstory.plugin.host.install.JavaScriptCatalogBundledPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledCatalogIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val pluginHost: PluginHost
        get() = (context as OpenStoryApplication).pluginHost

    @Test
    fun productionEnabledCatalogsExposeOnlyMyAnimeList() = runBlocking {
        assertEquals(
            listOf(MyAnimeListCatalogBundledPlugin.PLUGIN_ID),
            pluginHost.enabledCatalogs().map { hosted -> hosted.id.value },
        )
    }

    @Test
    fun bundledHostLoadsCatalogAndExecutesHome() = runBlocking {
        val hosted = try {
            pluginHost.catalog(PluginId(DefaultCatalogBundledPlugin.PLUGIN_ID))
        } catch (failure: PluginHostLoadException) {
            fail("Bundled host load failed: ${failure.errorCode}")
            return@runBlocking
        }

        assertEquals(DefaultCatalogBundledPlugin.PLUGIN_ID, hosted.id.value)
        assertEquals(DefaultCatalogBundledPlugin.VERSION, hosted.version)

        when (val result = hosted.instance.home(CatalogHomeRequest())) {
            is AppResult.Success -> {
                assertEquals(listOf("featured", "popular"), result.value.map { it.sourceId })
                assertTrue(
                    "Expected Hikari Chronicles from bundled Home fixture",
                    result.value.flatMap { it.items }.any { it.title == "Hikari Chronicles" },
                )
            }
            is AppResult.Failure -> fail("Bundled catalog home failed: ${result.error.code}")
        }
    }

    @Test
    fun bundledJavaScriptCatalogExecutesHomeSearchAndDetails() = runBlocking {
        assumeTrue(
            "JavaScript sandbox is unavailable on this device WebView provider",
            JavaScriptSandbox.isSupported(),
        )

        val hosted = try {
            pluginHost.catalog(PluginId(JavaScriptCatalogBundledPlugin.PLUGIN_ID))
        } catch (failure: PluginHostLoadException) {
            fail("Bundled JavaScript host load failed: ${failure.errorCode}")
            return@runBlocking
        }

        assertEquals(JavaScriptCatalogBundledPlugin.PLUGIN_ID, hosted.id.value)
        assertEquals(JavaScriptCatalogBundledPlugin.VERSION, hosted.version)

        when (val result = hosted.instance.home(CatalogHomeRequest())) {
            is AppResult.Success -> assertTrue(
                "Expected JavaScript Lantern from JavaScript Home fixture",
                result.value.flatMap { it.items }.any { it.title == "JavaScript Lantern" },
            )
            is AppResult.Failure -> fail("Bundled JavaScript home failed: ${result.error.code}")
        }

        when (val result = hosted.instance.search(CatalogSearchRequest(query = "lantern"))) {
            is AppResult.Success -> assertEquals(
                listOf("javascript-lantern"),
                result.value.items.map { it.sourceId },
            )
            is AppResult.Failure -> fail("Bundled JavaScript search failed: ${result.error.code}")
        }

        when (val result = hosted.instance.details("javascript-lantern")) {
            is AppResult.Success -> assertEquals(
                "Deterministic metadata returned through the JavaScript plugin sandbox and host bridge.",
                result.value.description,
            )
            is AppResult.Failure -> fail("Bundled JavaScript details failed: ${result.error.code}")
        }
    }

    @Test
    fun bundledCatalogRefreshPersistsIntoHomeViewModel() = runBlocking {
        context.deleteDatabase("openstory.db")
        val viewModel = createHomeViewModel(FixtureEnabledCatalogHost(pluginHost))
        val javaScriptSupported = JavaScriptSandbox.isSupported()

        viewModel.refresh()

        val report = awaitRefreshReport(viewModel)
        assertCatalogRefreshCapabilityOutcome(report, javaScriptSupported)

        val projectedTitles = awaitProjectedTitles(viewModel, javaScriptSupported)
        assertProjectedCatalogs(projectedTitles, javaScriptSupported)
    }

    private fun createHomeViewModel(pluginHost: PluginHost): HomeViewModel {
        val dispatchers = FixedAppDispatchers(
            io = Dispatchers.IO,
            default = Dispatchers.Default,
            main = Dispatchers.Main.immediate,
        )
        return OpenStoryAppGraph(
            context = context,
            dispatchers = dispatchers,
            pluginHost = pluginHost,
        ).createHomeViewModel()
    }

    private suspend fun awaitRefreshReport(viewModel: HomeViewModel): HomeRefreshReport {
        val refreshState = withTimeout(10_000) {
            viewModel.state.first { current ->
                !current.refreshing && current.refreshReport != null
            }
        }
        return requireNotNull(refreshState.refreshReport)
    }

    private fun assertCatalogRefreshCapabilityOutcome(
        report: HomeRefreshReport,
        javaScriptSupported: Boolean,
    ) {
        assertTrue(
            "Bundled refresh had no selector success. failed=${report.failed.mapValues { it.value.code }}",
            report.succeeded.any { it.value == DefaultCatalogBundledPlugin.PLUGIN_ID },
        )
        val javaScriptPluginId = PluginId(JavaScriptCatalogBundledPlugin.PLUGIN_ID)
        if (javaScriptSupported) {
            assertTrue(
                "Bundled refresh had no JavaScript success. " +
                    "failed=${report.failed.mapValues { it.value.code }}",
                javaScriptPluginId in report.succeeded,
            )
        } else {
            assertEquals(
                JAVASCRIPT_SANDBOX_UNSUPPORTED,
                report.failed[javaScriptPluginId]?.code,
            )
        }
    }

    private suspend fun awaitProjectedTitles(
        viewModel: HomeViewModel,
        javaScriptSupported: Boolean,
    ): Set<String> {
        val projectedState = withTimeout(10_000) {
            viewModel.state.first { current ->
                val titles = current.home.combined.flatMap { combined ->
                    combined.sources.map { source -> source.title }
                }.toSet()
                "Hikari Chronicles" in titles &&
                    (!javaScriptSupported || "JavaScript Lantern" in titles)
            }
        }
        return projectedState.home.combined.flatMap { combined ->
            combined.sources.map { source -> source.title }
        }.toSet()
    }

    private fun assertProjectedCatalogs(
        projectedTitles: Set<String>,
        javaScriptSupported: Boolean,
    ) {
        assertTrue(
            "Selector catalog did not reach Home projection",
            "Hikari Chronicles" in projectedTitles,
        )
        if (javaScriptSupported) {
            assertTrue(
                "JavaScript catalog did not reach Home projection",
                "JavaScript Lantern" in projectedTitles,
            )
        } else {
            assertTrue(
                "Unsupported JavaScript catalog unexpectedly reached Home projection",
                "JavaScript Lantern" !in projectedTitles,
            )
        }
    }

    private class FixtureEnabledCatalogHost(
        private val delegate: PluginHost,
    ) : PluginHost by delegate {
        override suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>> = listOf(
            delegate.catalog(PluginId(DefaultCatalogBundledPlugin.PLUGIN_ID)),
            delegate.catalog(PluginId(JavaScriptCatalogBundledPlugin.PLUGIN_ID)),
        )
    }

    private companion object {
        const val JAVASCRIPT_SANDBOX_UNSUPPORTED = "plugin.javascript_sandbox_unsupported"
    }
}
