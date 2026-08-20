package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.di.PluginRuntimeEntryPoint
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchRequestDto
import app.openstory.plugins.runtime.PluginCallResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaUpdatesLiveCatalogIntegrationTest {
    @Test
    fun liveCatalogHomeSearchAndDetailsReturnCanonicalData() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString(LIVE_ARGUMENT) == "true")
        assumeTrue(JavaScriptSandbox.isSupported())
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val runtime = EntryPointAccessors.fromApplication(context, PluginRuntimeEntryPoint::class.java).runtime()

        val homeResult = runtime.invoke(
            PluginId(MANGAUPDATES_PLUGIN_ID),
            PluginOperation.CATALOG_HOME,
            Json.encodeToJsonElement(emptyMap<String, String>()),
        )
        val homePayload = homeResult.requireMangaUpdatesLiveSuccess("catalog.home")
        val home = Json.decodeFromJsonElement(CatalogHomeOutputDto.serializer(), homePayload)
        assertEquals(3, home.sections.size)
        assertEquals(listOf("POPULAR", "LATEST_UPDATES", "TOP_RATED"), home.sections.map { it.kind.name })
        assertTrue(home.sections.all { it.items.isNotEmpty() })

        val searchResult = runtime.invoke(
            PluginId(MANGAUPDATES_PLUGIN_ID),
            PluginOperation.CATALOG_SEARCH,
            Json.encodeToJsonElement(CatalogSearchRequestDto(query = "One Piece")),
        )
        val searchPayload = searchResult.requireMangaUpdatesLiveSuccess("catalog.search")
        val search = Json.decodeFromJsonElement(CatalogSearchOutputDto.serializer(), searchPayload)
        val onePiece = search.items.firstOrNull { item -> item.title.equals("One Piece", ignoreCase = true) }
        assertNotNull("MangaUpdates search did not return One Piece", onePiece)
        onePiece!!
        assertTrue(onePiece.sourceId.matches(Regex("[1-9][0-9]*")))
        assertEquals("MANGA", onePiece.contentType.name)

        val detailsResult = runtime.invoke(
            PluginId(MANGAUPDATES_PLUGIN_ID),
            PluginOperation.CATALOG_DETAILS,
            Json.encodeToJsonElement(CatalogDetailsRequestDto(onePiece.sourceId)),
        )
        val detailsPayload = detailsResult.requireMangaUpdatesLiveSuccess("catalog.details")
        val details = Json.decodeFromJsonElement(CatalogDetailsOutputDto.serializer(), detailsPayload)
        assertEquals(onePiece.sourceId, details.sourceId)
        assertTrue(details.title.isNotBlank())
        val sourceUrl = details.sourceUrl
        assertTrue(sourceUrl == null || sourceUrl.contains("mangaupdates.com"))
    }
}

private fun <T> PluginCallResult<T>.requireMangaUpdatesLiveSuccess(operation: String): T = when (this) {
    is PluginCallResult.Success -> value
    is PluginCallResult.Failure -> throw AssertionError(
        "$operation failed: code=$code retryable=$retryable safeDetail=${safeDetail ?: "-"}",
    )
}

private const val MANGAUPDATES_PLUGIN_ID = "org.openstory.catalog.mangaupdates"
private const val LIVE_ARGUMENT = "openstoryLiveMangaUpdates"
