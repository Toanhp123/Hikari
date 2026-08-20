package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogFiltersOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchRequestDto
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.capabilities.CapabilityDispatcher
import app.openstory.plugins.runtime.capabilities.http.PluginHttpRequest
import app.openstory.plugins.runtime.capabilities.http.PluginHttpResponse
import app.openstory.plugins.runtime.capabilities.http.PluginRequestPolicy
import app.openstory.plugins.runtime.execution.AndroidxJavaScriptEngine
import app.openstory.plugins.runtime.execution.PluginOperationRunner
import app.openstory.plugins.runtime.install.PackageVerifier
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaUpdatesCatalogContractIntegrationTest {
    @Test
    fun referencePluginExecutesCatalogFlowAgainstFixtureApi() = runBlocking {
        assumeTrue(JavaScriptSandbox.isSupported())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext.applicationContext
        val testContext = instrumentation.context
        val packageBytes = appContext.assets.open(MANGAUPDATES_ASSET_PATH).use { it.readBytes() }
        val verified = PackageVerifier().verify(
            packageBytes,
            PluginArtifact(
                pluginId = MANGAUPDATES_PLUGIN_ID,
                version = MANGAUPDATES_PLUGIN_VERSION,
                downloadUrl = "https://bundled.openstory.app/mangaupdates-catalog.osp",
                sha256 = packageBytes.sha256(),
            ),
        )
        val pluginPackage = verified.requireSuccess("package verification")
        val manifest = pluginPackage.manifest
        assertEquals(
            setOf(
                "api.mangaupdates.com",
                "cdn.mangaupdates.com",
                "mangaupdates.com",
                "www.mangaupdates.com",
            ),
            manifest.capabilities.network?.hosts,
        )
        val dispatcher = FixtureMangaUpdatesDispatcher(
            searchBody = mangaUpdatesFixtureText(testContext, "search.json"),
            detailsBody = mangaUpdatesFixtureText(testContext, "details.json"),
            popularBody = mangaUpdatesFixtureText(testContext, "home-popular.json"),
            latestBody = mangaUpdatesFixtureText(testContext, "home-latest.json"),
            topRatedBody = mangaUpdatesFixtureText(testContext, "home-top-rated.json"),
        )
        val engine = AndroidxJavaScriptEngine(appContext)
        try {
            val runner = PluginOperationRunner(engine, dispatcher, NoOpMangaUpdatesDiagnostics)

            val homePayload = runner.run(
                PluginId(MANGAUPDATES_PLUGIN_ID),
                manifest,
                pluginPackage.entries.getValue("main.js").decodeToString(),
                PluginOperation.CATALOG_HOME,
                Json.encodeToJsonElement(emptyMap<String, String>()),
            ).requireSuccess("catalog.home")
            val home = Json.decodeFromJsonElement<CatalogHomeOutputDto>(homePayload)
            assertEquals(
                listOf("mangaupdates-popular", "mangaupdates-latest", "mangaupdates-top-rated"),
                home.sections.map { it.sourceId },
            )
            assertEquals(listOf("POPULAR", "LATEST_UPDATES", "TOP_RATED"), home.sections.map { it.kind.name })
            assertEquals("One Piece", home.sections[0].items.first().title)
            assertEquals(1L, home.sections[0].items.first().popularityRank)
            assertEquals("One Piece", home.sections[1].items.first().title)
            assertEquals(1787203200000L, home.sections[1].items.first().latestUpdate?.atEpochMillis)
            assertEquals("Vol. 112 Ch. 1152", home.sections[1].items.first().latestUpdate?.releaseLabel)
            assertEquals("Berserk", home.sections[2].items.first().title)
            assertEquals(3, dispatcher.requests.size)
            assertTrue(dispatcher.requests[0].body.orEmpty().contains("\"orderby\":\"week_pos\""))
            assertEquals("GET", dispatcher.requests[1].method)
            assertTrue(dispatcher.requests[1].url.contains("/v1/releases/days"))
            assertTrue(dispatcher.requests[1].url.contains("page=1"))
            assertTrue(dispatcher.requests[1].url.contains("include_metadata=true"))
            assertTrue(dispatcher.requests[2].body.orEmpty().contains("\"orderby\":\"rating\""))

            val searchPayload = runner.run(
                PluginId(MANGAUPDATES_PLUGIN_ID),
                manifest,
                pluginPackage.entries.getValue("main.js").decodeToString(),
                PluginOperation.CATALOG_SEARCH,
                Json.encodeToJsonElement(CatalogSearchRequestDto(query = "One Piece")),
            ).requireSuccess("catalog.search", dispatcher.requests)
            val search = Json.decodeFromJsonElement<CatalogSearchOutputDto>(searchPayload)
            val onePiece = search.items.first { it.title == "One Piece" }
            assertEquals(ONE_PIECE_MANGAUPDATES_ID, onePiece.sourceId)
            assertEquals("MANGA", onePiece.contentType.name)
            assertEquals(listOf("Oda Eiichiro"), onePiece.authors)
            assertEquals("https://cdn.mangaupdates.com/image/i999999.jpg", onePiece.coverUrl)
            assertEquals(8.72, onePiece.score?.value ?: 0.0, 0.001)
            assertNull(search.nextToken)

            val searchRequest = dispatcher.requests.single {
                it.url.endsWith("/v1/series/search") && it.body.orEmpty().contains("\"search\":\"One Piece\"")
            }
            assertEquals("POST", searchRequest.method)
            assertEquals("application/json", searchRequest.headers["Content-Type"])
            assertTrue(searchRequest.body.orEmpty().contains("\"search\":\"One Piece\""))
            assertTrue(searchRequest.body.orEmpty().contains("\"stype\":\"title\""))

            val detailsPayload = runner.run(
                PluginId(MANGAUPDATES_PLUGIN_ID),
                manifest,
                pluginPackage.entries.getValue("main.js").decodeToString(),
                PluginOperation.CATALOG_DETAILS,
                Json.encodeToJsonElement(CatalogDetailsRequestDto(ONE_PIECE_MANGAUPDATES_ID)),
            ).requireSuccess("catalog.details", dispatcher.requests)
            val details = Json.decodeFromJsonElement<CatalogDetailsOutputDto>(detailsPayload)
            assertEquals(ONE_PIECE_MANGAUPDATES_ID, details.sourceId)
            assertEquals("https://www.mangaupdates.com/series/ttaew4a/one-piece", details.sourceUrl)
            assertEquals("One Piece", details.title)
            assertTrue("ONE PIECE" in details.aliases)
            assertTrue("ワンピース" in details.aliases)
            assertTrue("Oda Eiichiro" in details.authors)
            assertTrue("Action" in details.genres)
            assertEquals(setOf("ja"), details.languageTags)
            assertEquals("ONGOING", details.publicationStatus?.name)

            val detailsRequest = dispatcher.requests.single { it.url.endsWith("/v1/series/$ONE_PIECE_MANGAUPDATES_ID") }
            assertEquals("GET", detailsRequest.method)

            val filtersPayload = runner.run(
                PluginId(MANGAUPDATES_PLUGIN_ID),
                manifest,
                pluginPackage.entries.getValue("main.js").decodeToString(),
                PluginOperation.CATALOG_FILTERS,
                Json.encodeToJsonElement(emptyMap<String, String>()),
            ).requireSuccess("catalog.filters")
            assertTrue(Json.decodeFromJsonElement<CatalogFiltersOutputDto>(filtersPayload).filters.isEmpty())
        } finally {
            engine.close()
        }
    }
}

private fun mangaUpdatesFixtureText(context: android.content.Context, name: String): String =
    context.assets.open("plugins/mangaupdates-catalog-fixtures/$name").bufferedReader().use { it.readText() }

private fun <T> PluginCallResult<T>.requireSuccess(
    label: String,
    requests: List<PluginHttpRequest> = emptyList(),
): T = when (this) {
    is PluginCallResult.Success -> value
    is PluginCallResult.Failure -> throw AssertionError(
        "$label failed: code=$code retryable=$retryable safeDetail=${safeDetail ?: "-"} requests=$requests",
    )
}

private class FixtureMangaUpdatesDispatcher(
    private val searchBody: String,
    private val detailsBody: String,
    private val popularBody: String,
    private val latestBody: String,
    private val topRatedBody: String,
) : CapabilityDispatcher {
    val requests = mutableListOf<PluginHttpRequest>()

    override suspend fun dispatch(
        pluginId: PluginId,
        operation: String?,
        method: String,
        payload: JsonElement,
        requestPolicy: PluginRequestPolicy,
    ): PluginCallResult<JsonElement> {
        val validContext = pluginId.value == MANGAUPDATES_PLUGIN_ID &&
            operation in setOf(
                PluginOperation.CATALOG_HOME.wireName,
                PluginOperation.CATALOG_SEARCH.wireName,
                PluginOperation.CATALOG_DETAILS.wireName,
            ) &&
            requestPolicy.allowedHosts == setOf(
                "api.mangaupdates.com",
                "cdn.mangaupdates.com",
                "mangaupdates.com",
                "www.mangaupdates.com",
            )
        if (!validContext || method != "http.execute") {
            return PluginCallResult.Failure("plugin.capability_denied", false)
        }
        val request = Json.decodeFromJsonElement(PluginHttpRequest.serializer(), payload)
        requests += request
        val requestBody = request.body.orEmpty()
        val body = when {
            request.url == "https://api.mangaupdates.com/v1/series/search" &&
                request.method == "POST" && requestBody.contains("\"orderby\":\"week_pos\"") -> popularBody
            request.url.startsWith("https://api.mangaupdates.com/v1/releases/days?") &&
                request.method == "GET" -> latestBody
            request.url == "https://api.mangaupdates.com/v1/series/search" &&
                request.method == "POST" && requestBody.contains("\"orderby\":\"rating\"") -> topRatedBody
            request.url == "https://api.mangaupdates.com/v1/series/search" && request.method == "POST" -> searchBody
            request.url == "https://api.mangaupdates.com/v1/series/$ONE_PIECE_MANGAUPDATES_ID" -> detailsBody
            else -> return PluginCallResult.Failure("plugin.fixture_url_unexpected", false)
        }
        return PluginCallResult.Success(
            Json.encodeToJsonElement(PluginHttpResponse.serializer(), PluginHttpResponse(200, body)),
        )
    }
}

private object NoOpMangaUpdatesDiagnostics : PluginDiagnosticsSink {
    override suspend fun record(event: PluginDiagnosticEvent) = Unit
    override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> = emptyList()
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private const val MANGAUPDATES_PLUGIN_ID = "org.openstory.catalog.mangaupdates"
private const val MANGAUPDATES_PLUGIN_VERSION = "1.1.4"
private const val MANGAUPDATES_ASSET_PATH = "plugins/mangaupdates-catalog.osp"
private const val ONE_PIECE_MANGAUPDATES_ID = "64897697818"
