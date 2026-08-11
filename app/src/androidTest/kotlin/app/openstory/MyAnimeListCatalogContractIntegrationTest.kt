package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.di.PluginRuntimeEntryPoint
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.CatalogFiltersOutputDto
import app.openstory.plugins.runtime.PluginCallResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyAnimeListCatalogContractIntegrationTest {
    @Test
    fun bundledPluginLoadsAndExecutesVnextCatalogOperation() = runBlocking {
        assumeTrue(JavaScriptSandbox.isSupported())
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val runtime = EntryPointAccessors.fromApplication(context, PluginRuntimeEntryPoint::class.java).runtime()

        val result = runtime.invoke(
            PluginId(MYANIMELIST_PLUGIN_ID),
            PluginOperation.CATALOG_FILTERS,
            buildJsonObject {},
        )

        assertTrue(
            "Bundled plugin invocation failed: ${(result as? PluginCallResult.Failure)?.code}",
            result is PluginCallResult.Success,
        )
        val payload = (result as PluginCallResult.Success).value
        assertTrue(Json.decodeFromJsonElement(CatalogFiltersOutputDto.serializer(), payload).filters.isEmpty())
        val installed = runtime.enabled(app.openstory.plugins.api.manifest.PluginService.CATALOG).single()
        assertEquals(MYANIMELIST_PLUGIN_VERSION, installed.version)
    }
}

private const val MYANIMELIST_PLUGIN_ID = "org.openstory.catalog.myanimelist"
private const val MYANIMELIST_PLUGIN_VERSION = "2.0.0"
