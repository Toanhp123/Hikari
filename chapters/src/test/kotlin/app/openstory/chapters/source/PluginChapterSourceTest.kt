package app.openstory.chapters.source

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PageDto
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.content.ContentChapterListModeDto
import app.openstory.plugins.api.protocol.content.ContentChaptersRequestDto
import app.openstory.plugins.api.protocol.content.ContentReleaseDto
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class PluginChapterSourceTest {
    @Test
    fun registryDiscoversOnlyChapterListProviders() = runTest {
        var discovered: PluginOperation? = null
        val runtime = object : PluginRuntime {
            override suspend fun invoke(
                pluginId: PluginId,
                operation: PluginOperation,
                input: JsonElement,
            ): PluginCallResult<JsonElement> = error("unused")

            override suspend fun enabled(service: PluginService): List<InstalledPlugin> =
                error("unexpected service lookup")

            override suspend fun enabled(operation: PluginOperation): List<InstalledPlugin> {
                discovered = operation
                return emptyList()
            }
        }

        PluginChapterSourceRegistry(runtime, Json).enabled()

        assertEquals(PluginOperation.CONTENT_CHAPTERS, discovered)
    }

    @Test
    fun chapterModesUseOneBoundedContentOperation() = runTest {
        val requests = mutableListOf<ContentChaptersRequestDto>()
        val runtime = runtime { _, operation, input ->
            assertEquals(PluginOperation.CONTENT_CHAPTERS, operation)
            requests += Json.decodeFromJsonElement(ContentChaptersRequestDto.serializer(), input)
            PluginCallResult.Success(Json.encodeToJsonElement(PageDto<ContentReleaseDto>(emptyList())))
        }
        val source = source(runtime)

        ChapterListMode.entries.forEach { mode ->
            source.chapters(ChapterSourceRequest("story", mode, checkpoint = "mark", nextToken = "page"))
        }

        assertEquals(
            listOf(
                ContentChapterListModeDto.RECENT,
                ContentChapterListModeDto.FULL,
                ContentChapterListModeDto.INCREMENTAL,
            ),
            requests.map(ContentChaptersRequestDto::mode),
        )
        assertEquals(listOf("mark", "mark", "mark"), requests.map(ContentChaptersRequestDto::checkpoint))
        assertEquals(listOf("page", "page", "page"), requests.map(ContentChaptersRequestDto::nextToken))
    }

    @Test
    fun boundedProtocolReleasesMapIntoChapterOwnedPage() = runTest {
        val runtime = runtime { _, _, _ ->
            PluginCallResult.Success(
                Json.encodeToJsonElement(
                    PageDto(
                        items = listOf(ContentReleaseDto("release-1", "Opening", "1", "en", 123L)),
                        nextToken = "next",
                    ),
                ),
            )
        }

        val result = assertIs<ChapterSourceResult.Success>(source(runtime).chapters(ChapterSourceRequest("story")))

        assertEquals("release-1", result.page.releases.single().sourceReleaseId)
        assertEquals("Opening", result.page.releases.single().title)
        assertEquals("1", result.page.releases.single().rawNumber)
        assertEquals("next", result.page.nextToken)
    }

    @Test
    fun malformedOutputBecomesNonRetryableSourceFailure() = runTest {
        val runtime = runtime { _, _, _ ->
            PluginCallResult.Success(Json.parseToJsonElement("""{"items":[{"sourceReleaseId":""}]}"""))
        }

        val result = assertIs<ChapterSourceResult.Failure>(source(runtime).chapters(ChapterSourceRequest("story")))

        assertEquals("chapter.source_payload_invalid", result.failure.code)
        assertEquals(false, result.failure.retryable)
    }

    @Test
    fun cancellationIsNeverConvertedIntoSourceFailure() = runTest {
        val source = source(runtime { _, _, _ -> throw CancellationException("cancel") })

        assertFailsWith<CancellationException> { source.chapters(ChapterSourceRequest("story")) }
    }

    @Test
    fun onePluginFailureDoesNotCancelPeerResult() = runTest {
        val failed = source(runtime { _, _, _ -> error("offline") }, "org.example.failed")
        val healthy = source(
            runtime { _, _, _ ->
                PluginCallResult.Success(
                    Json.encodeToJsonElement(
                        PageDto(items = listOf(ContentReleaseDto("healthy", null, "2", "en", null))),
                    ),
                )
            },
            "org.example.healthy",
        )

        val results = coroutineScope {
            listOf(failed, healthy).map { chapterSource ->
                async { chapterSource.chapters(ChapterSourceRequest("story")) }
            }.awaitAll()
        }

        assertEquals("chapter.source_failed", assertIs<ChapterSourceResult.Failure>(results[0]).failure.code)
        assertEquals("healthy", assertIs<ChapterSourceResult.Success>(results[1]).page.releases.single().sourceReleaseId)
    }
}

private fun source(
    runtime: PluginRuntime,
    pluginId: String = "org.example.content",
) = PluginChapterSource(
    installed = InstalledPlugin(
        pluginId = PluginId(pluginId),
        version = "1.0.0",
        services = setOf(PluginService.CONTENT),
    ),
    runtime = runtime,
    json = Json,
)

private fun runtime(
    block: suspend (PluginId, PluginOperation, JsonElement) -> PluginCallResult<JsonElement>,
) = object : PluginRuntime {
    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = block(pluginId, operation, input)

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> = emptyList()
}
