package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.di.MyAnimeListBundledPlugin
import app.openstory.di.PluginRuntimeEntryPoint
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.CatalogSearchOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchRequestDto
import app.openstory.plugins.runtime.PluginCallResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyAnimeListLiveCatalogIntegrationTest {
    @Test
    fun liveCatalogReturnsCanonicalMyAnimeListManga() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString(LIVE_ARGUMENT) == "true")
        assumeTrue(JavaScriptSandbox.isSupported())
        assertTrue(BuildConfig.MYANIMELIST_CLIENT_ID.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val runtime = EntryPointAccessors.fromApplication(context, PluginRuntimeEntryPoint::class.java).runtime()

        val result = runtime.invoke(
            PluginId(MyAnimeListBundledPlugin.PLUGIN_ID),
            PluginOperation.CATALOG_SEARCH,
            Json.encodeToJsonElement(CatalogSearchRequestDto(query = "One Piece")),
        )

        val output = Json.decodeFromJsonElement(
            CatalogSearchOutputDto.serializer(),
            (result as PluginCallResult.Success<*>).value as kotlinx.serialization.json.JsonElement,
        )
        assertTrue(output.items.any { item -> item.sourceId == ONE_PIECE_MAL_ID })
        assertEquals("MANGA", output.items.first { it.sourceId == ONE_PIECE_MAL_ID }.contentType.name)
    }
}

private const val LIVE_ARGUMENT = "openstoryLiveCatalog"
private const val ONE_PIECE_MAL_ID = "13"
