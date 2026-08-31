package app.openstory

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.plugins.api.protocol.PageDto
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.content.ContentResolveUrlRequestDto
import app.openstory.plugins.api.protocol.content.ContentSearchRequestDto
import app.openstory.plugins.api.protocol.content.ChapterDocumentDto
import app.openstory.plugins.api.protocol.content.ContentChapterRequestDto
import app.openstory.plugins.api.protocol.content.ContentChaptersRequestDto
import app.openstory.plugins.api.protocol.content.ContentReleaseDto
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
import app.openstory.plugins.api.protocol.content.ImagePageBlockDto
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
    fun referencePluginExecutesContentFlowAgainstFixtureApi() = runBlocking {
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
        assertEquals(false, manifest.capabilities.reader?.offlineDownload)
        assertEquals(true, manifest.capabilities.reader?.remoteImages)
        assertEquals(
            ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT,
            manifest.capabilities.reader?.imageIdentity,
        )
        assertEquals(ReaderImageLocatorContract.MUTABLE_OR_UNKNOWN, manifest.capabilities.reader?.imageLocator)
        assertEquals(ReaderImagePersistenceContract.PUBLIC, manifest.capabilities.reader?.imagePersistence)
        val script = pluginPackage.entries.getValue("main.js").decodeToString()
        val atHomeBody = fixtureText(testContext, "at-home.json")
        val dispatcher = FixtureMangaDexDispatcher(
            searchBody = fixtureText(testContext, "search.json"),
            detailsBody = fixtureText(testContext, "details.json"),
            chaptersBody = fixtureText(testContext, "chapters.json"),
            atHomeBodies = listOf(
                atHomeBody,
                atHomeBody.replace("https://uploads.mangadex.org", "https://rotated.mangadex.example"),
                atHomeBody.replace(FIXTURE_CHAPTER_HASH, CHANGED_CHAPTER_HASH),
                atHomeBody.replace(FIXTURE_FIRST_FILENAME, CHANGED_FIRST_FILENAME),
            ),
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

            val chapter = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_CHAPTER,
                Json.encodeToJsonElement(ContentChapterRequestDto(FIXTURE_CHAPTER_ID)),
            )
            val chapterDocument = Json.decodeFromJsonElement<ChapterDocumentDto>(
                chapter.requireSuccess("content.chapter", dispatcher.requestedUrls),
            )
            val imagePages = chapterDocument.blocks.map { block -> block as ImagePageBlockDto }
            assertEquals(2, imagePages.size)
            assertEquals(
                "$FIXTURE_CHAPTER_HASH/$FIXTURE_FIRST_FILENAME",
                imagePages.first().stableId,
            )
            assertEquals(
                "https://uploads.mangadex.org/data/$FIXTURE_CHAPTER_HASH/$FIXTURE_FIRST_FILENAME",
                imagePages.first().imageUrl,
            )

            val rotated = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_CHAPTER,
                Json.encodeToJsonElement(ContentChapterRequestDto(FIXTURE_CHAPTER_ID)),
            ).requireSuccess("content.chapter rotated", dispatcher.requestedUrls)
            val rotatedPage = Json.decodeFromJsonElement<ChapterDocumentDto>(rotated)
                .blocks.first() as ImagePageBlockDto
            assertEquals(imagePages.first().stableId, rotatedPage.stableId)
            assertEquals(
                "https://rotated.mangadex.example/data/$FIXTURE_CHAPTER_HASH/$FIXTURE_FIRST_FILENAME",
                rotatedPage.imageUrl,
            )

            val changedHash = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_CHAPTER,
                Json.encodeToJsonElement(ContentChapterRequestDto(FIXTURE_CHAPTER_ID)),
            ).requireSuccess("content.chapter changed hash", dispatcher.requestedUrls)
            val changedHashPage = Json.decodeFromJsonElement<ChapterDocumentDto>(changedHash)
                .blocks.first() as ImagePageBlockDto
            assertEquals("$CHANGED_CHAPTER_HASH/$FIXTURE_FIRST_FILENAME", changedHashPage.stableId)

            val changedFilename = runner.run(
                PluginId(MANGADEX_PLUGIN_ID),
                manifest,
                script,
                PluginOperation.CONTENT_CHAPTER,
                Json.encodeToJsonElement(ContentChapterRequestDto(FIXTURE_CHAPTER_ID)),
            ).requireSuccess("content.chapter changed filename", dispatcher.requestedUrls)
            val changedFilenamePage = Json.decodeFromJsonElement<ChapterDocumentDto>(changedFilename)
                .blocks.first() as ImagePageBlockDto
            assertEquals("$FIXTURE_CHAPTER_HASH/$CHANGED_FIRST_FILENAME", changedFilenamePage.stableId)
            assertTrue(dispatcher.requestedUrls.any { it.endsWith("/at-home/server/$FIXTURE_CHAPTER_ID") })
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
    private val atHomeBodies: List<String>,
) : CapabilityDispatcher {
    val requestedUrls = mutableListOf<String>()
    private var atHomeRequestCount = 0

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
                PluginOperation.CONTENT_CHAPTER.wireName,
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
            request.url == "https://api.mangadex.org/at-home/server/$FIXTURE_CHAPTER_ID" ->
                atHomeBodies.getOrElse(atHomeRequestCount++) { atHomeBodies.last() }
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
private const val FIXTURE_CHAPTER_ID = "11111111-2222-4333-8444-555555555555"
private const val FIXTURE_CHAPTER_HASH = "3303dd03ac8d27452cce3f2a882e94b2"
private const val CHANGED_CHAPTER_HASH = "4404ee14bd9e38563ddf4f3b993fa5c3"
private const val FIXTURE_FIRST_FILENAME =
    "1-f7a76de10d346de7ba01786762ebbedc666b412ad0d4b73baa330a2a392dbcdd.png"
private const val CHANGED_FIRST_FILENAME =
    "1-a8b87ef21e457ef8cb12897873fcce777c523be1e5c84cbb441b3b4a403cedee.png"
