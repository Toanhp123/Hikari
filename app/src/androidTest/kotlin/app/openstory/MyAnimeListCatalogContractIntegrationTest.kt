package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.di.JavaScriptExecutorEntryPoint
import app.openstory.di.MyAnimeListCatalogBundledPlugin
import app.openstory.model.ContentType
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.PluginUrlPolicy
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.host.install.AndroidBundledPluginAssets
import app.openstory.plugin.host.install.PackageVerifier
import app.openstory.plugin.host.install.ZipPackageArchiveInspector
import app.openstory.plugin.host.js.JavaScriptCatalogPlugin
import app.openstory.plugin.host.js.JavaScriptPluginRuntime
import app.openstory.plugin.host.js.JsCapabilityDispatcher
import app.openstory.plugin.host.js.JsWireDtoDecoder
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyAnimeListCatalogContractIntegrationTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test
    fun bundledPluginMapsMalApiResponsesToCanonicalMyAnimeListDtos() = runBlocking {
        assumeTrue(
            "JavaScript sandbox is unavailable on this device WebView provider",
            JavaScriptSandbox.isSupported(),
        )

        val executor = EntryPointAccessors.fromApplication(
            context,
            JavaScriptExecutorEntryPoint::class.java,
        ).javaScriptExecutor()
        val bundledPackage = AndroidBundledPluginAssets(
            context = context,
            descriptors = listOf(MyAnimeListCatalogBundledPlugin.descriptor),
            ioDispatcher = Dispatchers.IO,
        ).packages().single()
        val verified = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(bundledPackage.installRequest)
        if (verified is AppResult.Failure) {
            fail("MyAnimeList package validation failed: ${verified.error.code}")
        }

        val entries = readMyAnimeListTestEntries(bundledPackage.installRequest.packageBytes)
        val manifest = Json { ignoreUnknownKeys = true }
            .decodeFromString(PluginManifest.serializer(), entries.manifest)
        val runtime = JavaScriptPluginRuntime(
            executor = executor,
            dispatcher = JsCapabilityDispatcher(
                manifest = manifest,
                http = MyAnimeListFixtureGateway(
                    InstrumentationRegistry.getInstrumentation().context.assets,
                ),
            ),
        )
        val catalog = JavaScriptCatalogPlugin(
            source = entries.mainJs,
            runtime = runtime,
            decoder = JsWireDtoDecoder(
                PluginWireDtoValidator(PluginUrlPolicy(manifest.allowedHosts)),
            ),
        )

        val home = catalog.home(CatalogHomeRequest()).success("home")
        assertEquals("mal-top-manga", home.single().sourceId)
        assertEquals(listOf("2", "13"), home.single().items.map { it.sourceId })

        val search = catalog.search(
            CatalogSearchRequest(query = "One Piece"),
        ).success("search")
        assertEquals(listOf("13"), search.items.map { it.sourceId })
        assertEquals(null, search.nextToken)

        val details = catalog.details("13").success("details")
        assertEquals("13", details.sourceId)
        assertEquals("One Piece", details.title)
        assertEquals(ContentType.MANGA, details.contentType)
        assertEquals("https://myanimelist.net/manga/13", details.sourceUrl)
        assertEquals(3L, details.popularityRank)
        assertTrue("Oda, Eiichiro" in details.authors)
        assertTrue("Pirates" in details.genres)
    }
}

private class MyAnimeListFixtureGateway(
    private val assets: android.content.res.AssetManager,
) : PluginHttpGateway {
    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> {
        val uri = runCatching { URI(request.url) }.getOrNull()
        return if (uri?.host == "api.myanimelist.net") {
            fixtureResponse(uri)
        } else {
            AppResult.Failure(
                AppError.Network(
                    code = "network.fixture_host_not_found",
                    retryable = false,
                ),
            )
        }
    }

    private fun fixtureResponse(uri: URI): AppResult<PluginHttpResponse> {
        val fixture = when (uri.path) {
            "/v2/manga/ranking" -> "top.json"
            "/v2/manga" -> "search.json"
            "/v2/manga/13" -> "details.json"
            else -> null
        }
        return if (fixture == null) {
            AppResult.Failure(
                AppError.Network(
                    code = "network.fixture_route_not_found",
                    retryable = false,
                ),
            )
        } else {
            fixtureSuccess(fixture)
        }
    }

    private fun fixtureSuccess(fixture: String): AppResult<PluginHttpResponse> {
        val bodyText = assets.open("plugins/myanimelist-catalog-fixtures/$fixture")
            .bufferedReader()
            .use { it.readText() }
        val body = bodyText.encodeToByteArray()
        return AppResult.Success(
            PluginHttpResponse(
                status = 200,
                headers = emptyMap(),
                body = body,
                decodedText = bodyText,
            ),
        )
    }
}

private fun <T> AppResult<T>.success(operation: String): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> {
        fail("MyAnimeList $operation failed: ${error.code}")
        error("unreachable")
    }
}

private fun readMyAnimeListTestEntries(packageBytes: ByteArray): MyAnimeListTestEntries {
    var manifest: String? = null
    var mainJs: String? = null
    ZipInputStream(ByteArrayInputStream(packageBytes)).use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            when (entry.name) {
                "manifest.json" -> manifest = archive.readBytes().decodeToString()
                "main.js" -> mainJs = archive.readBytes().decodeToString()
            }
            archive.closeEntry()
            entry = archive.nextEntry
        }
    }
    return MyAnimeListTestEntries(
        manifest = requireNotNull(manifest),
        mainJs = requireNotNull(mainJs),
    )
}

private data class MyAnimeListTestEntries(
    val manifest: String,
    val mainJs: String,
)
