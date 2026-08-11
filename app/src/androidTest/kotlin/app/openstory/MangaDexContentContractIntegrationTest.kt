package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.api.protocol.PageDto
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.content.ContentResolveUrlRequestDto
import app.openstory.plugins.api.protocol.content.ContentSearchRequestDto
import app.openstory.plugins.api.protocol.content.ContentChaptersRequestDto
import app.openstory.plugins.api.protocol.content.ContentReleaseDto
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaDexContentContractIntegrationTest {
    @Test
    fun referencePluginExecutesSearchAndUrlResolutionAgainstFixtureApi() = runBlocking {
        assumeTrue(JavaScriptSandbox.isSupported())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val appContext = instrumentation.targetContext.applicationContext
        val packageBytes = mangaDexPackageBytes(appContext)
        val verified = PackageVerifier().verify(
            packageBytes,
            PluginArtifact(
                pluginId = MANGADEX_PLUGIN_ID,
                version = MANGADEX_PLUGIN_VERSION,
                downloadUrl = "https://bundled.openstory.app/mangadex-content.osp",
                sha256 = mangaDexSha256(packageBytes),
            ),
        )
        val pluginPackage = verified.requireSuccess("package verification")
        val manifest = pluginPackage.manifest
        assertEquals(setOf("api.mangadex.org", "mangadex.org"), manifest.capabilities.network?.hosts)
        val script = pluginPackage.entries.getValue("main.js").decodeToString()
        val dispatcher = FixtureMangaDexDispatcher(
            searchBody = fixtureText(testContext, "search.json"),
            detailsBody = fixtureText(testContext, "details.json"),
            chaptersBody = fixtureText(testContext, "chapters.json"),
        )
        val engine = AndroidxJavaScriptEngine(appContext)
        try {
            val runner = PluginOperationRunner(engine, dispatcher, NoOpDiagnostics)
            val search = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_SEARCH,
                Json.encodeToJsonElement(ContentSearchRequestDto("One Piece")),
            )
            val searchPayload = search.requireSuccess("content.search", dispatcher.requestedUrls)
            val page = Json.decodeFromJsonElement(
                PageDto.serializer(ContentStoryCandidateDto.serializer()),
                searchPayload,
            )
            val candidate = page.items.single()
            assertEquals(ONE_PIECE_MANGADEX_ID, candidate.sourceStoryId)
            assertEquals("One Piece", candidate.title)
            assertEquals(ONE_PIECE_MANGADEX_URL, candidate.sourceUrl)
            assertTrue("Eiichiro Oda" in candidate.authors)

            val resolved = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_RESOLVE_URL,
                Json.encodeToJsonElement(ContentResolveUrlRequestDto(ONE_PIECE_MANGADEX_URL)),
            )
            val resolvedPayload = resolved.requireSuccess("content.resolveUrl", dispatcher.requestedUrls)
            val resolvedCandidate = Json.decodeFromJsonElement(ContentStoryCandidateDto.serializer(), resolvedPayload)
            assertEquals(ONE_PIECE_MANGADEX_ID, resolvedCandidate.sourceStoryId)
            assertEquals("One Piece", resolvedCandidate.title)
            assertTrue(dispatcher.requestedUrls.any { it.contains("title=One%20Piece") })
            assertTrue(dispatcher.requestedUrls.any { it.contains("/manga/$ONE_PIECE_MANGADEX_ID") })

            val chapters = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_CHAPTERS,
                Json.encodeToJsonElement(ContentChaptersRequestDto(ONE_PIECE_MANGADEX_ID)),
            )
            val chapterPayload = chapters.requireSuccess("content.chapters", dispatcher.requestedUrls)
            val chapterPage = Json.decodeFromJsonElement(
                PageDto.serializer(ContentReleaseDto.serializer()),
                chapterPayload,
            )
            assertEquals("1100", chapterPage.items.single().rawNumber)
            assertEquals("en", chapterPage.items.single().languageTag)
            assertTrue(dispatcher.requestedUrls.any { it.contains("/feed?") })
        } finally {
            engine.close()
        }
    }
}

private fun <T> PluginCallResult<T>.requireSuccess(
    label: String,
    requestedUrls: List<String> = emptyList(),
): T = when (this) {
    is PluginCallResult.Success -> value
    is PluginCallResult.Failure -> throw AssertionError(
        "$label failed: code=$code retryable=$retryable safeDetail=${safeDetail ?: "-"} " +
            "requestedUrls=$requestedUrls",
    )
}

private class FixtureMangaDexDispatcher(
    private val searchBody: String,
    private val detailsBody: String,
    private val chaptersBody: String,
) : CapabilityDispatcher {
    val requestedUrls = mutableListOf<String>()

    override suspend fun dispatch(
        pluginId: PluginId,
        operation: String?,
        method: String,
        payload: JsonElement,
        requestPolicy: PluginRequestPolicy,
    ): PluginCallResult<JsonElement> {
        val validContext = pluginId.value == MANGADEX_PLUGIN_ID &&
            operation in setOf(
                PluginOperation.CONTENT_SEARCH.wireName,
                PluginOperation.CONTENT_RESOLVE_URL.wireName,
                PluginOperation.CONTENT_CHAPTERS.wireName,
            ) &&
            requestPolicy.allowedHosts == setOf("api.mangadex.org", "mangadex.org")
        if (!validContext || method != "http.execute") {
            return PluginCallResult.Failure("plugin.capability_denied", false)
        }
        val request = Json.decodeFromJsonElement(PluginHttpRequest.serializer(), payload)
        requestedUrls += request.url
        val body = when {
            request.url.startsWith("https://api.mangadex.org/manga?") -> searchBody
            request.url.startsWith("https://api.mangadex.org/manga/$ONE_PIECE_MANGADEX_ID?") -> detailsBody
            request.url.startsWith("https://api.mangadex.org/manga/$ONE_PIECE_MANGADEX_ID/feed?") -> chaptersBody
            else -> return PluginCallResult.Failure("plugin.fixture_url_unexpected", false)
        }
        return PluginCallResult.Success(
            Json.encodeToJsonElement(PluginHttpResponse.serializer(), PluginHttpResponse(HTTP_OK, body)),
        )
    }
}

private object NoOpDiagnostics : PluginDiagnosticsSink {
    override suspend fun record(event: PluginDiagnosticEvent) = Unit
    override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> = emptyList()
}

private const val HTTP_OK = 200
