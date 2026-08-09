package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.BuildConfig
import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.host.PluginHost
import app.openstory.plugin.host.PluginHostLoadException
import app.openstory.di.MyAnimeListCatalogBundledPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.OkHttpClient
import okhttp3.Request

@RunWith(AndroidJUnit4::class)
class MyAnimeListLiveCatalogIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val pluginHost: PluginHost
        get() = (context as OpenStoryApplication).pluginHost

    @Test
    fun liveCatalogReturnsCanonicalMyAnimeListManga() = runBlocking {
        assumeTrue(liveCatalogEnabled())
        assumeTrue(
            "JavaScript sandbox is unavailable on this device WebView provider",
            JavaScriptSandbox.isSupported(),
        )

        val hosted = try {
            pluginHost.catalog(PluginId(MyAnimeListCatalogBundledPlugin.PLUGIN_ID))
        } catch (failure: PluginHostLoadException) {
            fail("MyAnimeList host load failed: ${failure.errorCode}")
            return@runBlocking
        }
        assertEquals(MyAnimeListCatalogBundledPlugin.PLUGIN_ID, hosted.id.value)
        assertTrue(
            "Set OPENSTORY_MAL_CLIENT_ID or -Popenstory.malClientId before running the live MAL test",
            BuildConfig.MYANIMELIST_CLIENT_ID.isNotBlank(),
        )

        val home = hosted.instance.home(CatalogHomeRequest()).success(
            operation = "home",
            diagnostic = ::probeMyAnimeListTopManga,
        )
        assertEquals("mal-top-manga", home.single().sourceId)
        assertTrue(home.single().items.isNotEmpty())
        assertTrue(home.single().items.all { item -> item.sourceId.toLongOrNull() != null })

        delay(LIVE_REQUEST_SPACING_MILLIS)
        val search = hosted.instance.search(
            CatalogSearchRequest(query = "One Piece"),
        ).success("search")
        assertTrue(search.items.any { item -> item.sourceId == ONE_PIECE_MAL_ID })

        delay(LIVE_REQUEST_SPACING_MILLIS)
        val details = hosted.instance.details(ONE_PIECE_MAL_ID).success("details")
        assertEquals(ONE_PIECE_MAL_ID, details.sourceId)
        assertEquals("One Piece", details.title)
        assertEquals(ContentType.MANGA, details.contentType)
        assertNotNull(details.image)
        assertTrue(details.sourceUrl?.startsWith("https://myanimelist.net/manga/13") == true)
    }

    private fun liveCatalogEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString(LIVE_ARGUMENT) == "true"

    private fun probeMyAnimeListTopManga(): String = runCatching {
        val request = Request.Builder()
            .url(MAL_TOP_MANGA_URL)
            .header("User-Agent", OPENSTORY_USER_AGENT)
            .header("X-MAL-CLIENT-ID", BuildConfig.MYANIMELIST_CLIENT_ID)
            .get()
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val bodyPreview = response.body.string()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .take(MAL_ERROR_PREVIEW_CHARS)
            "direct_status=${response.code} direct_body=$bodyPreview"
        }
    }.getOrElse { failure ->
        "direct_probe_failed=${failure::class.java.simpleName}"
    }
}

private fun <T> AppResult<T>.success(
    operation: String,
    diagnostic: (() -> String)? = null,
): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> {
        val diagnosticSuffix = diagnostic?.invoke()?.let { " $it" }.orEmpty()
        fail("MyAnimeList $operation failed: ${error.code}$diagnosticSuffix")
        error("unreachable")
    }
}

private const val LIVE_ARGUMENT = "openstoryLiveCatalog"
private const val LIVE_REQUEST_SPACING_MILLIS = 1_100L
private const val ONE_PIECE_MAL_ID = "13"
private const val MAL_TOP_MANGA_URL =
    "https://api.myanimelist.net/v2/manga/ranking?ranking_type=manga&limit=1"
private const val OPENSTORY_USER_AGENT = "OpenStory/1.0"
private const val MAL_ERROR_PREVIEW_CHARS = 320
