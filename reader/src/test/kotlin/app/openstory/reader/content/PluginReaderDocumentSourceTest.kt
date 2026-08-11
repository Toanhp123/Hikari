package app.openstory.reader.content

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import app.openstory.reader.document.ReaderDocumentSanitizer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginReaderDocumentSourceTest {
    @Test
    fun invokesChapterOperationAndSanitizesPayload() = runTest {
        val runtime = FakeRuntime()
        val source = PluginReaderDocumentSource(
            InstalledPlugin(PluginId("plugin"), "1", setOf(PluginService.CONTENT)),
            runtime,
            Json,
            ReaderDocumentSanitizer(),
        )

        val result = source.fetch(release())

        assertIs<ReaderSourceResult.Success>(result)
        assertEquals(PluginOperation.CONTENT_CHAPTER, runtime.operation)
        assertEquals("source-release", runtime.sourceReleaseId)
    }

    @Test
    fun rejectsInvalidPluginPayloadBeforeRepositoryWriteThrough() = runTest {
        val source = PluginReaderDocumentSource(
            InstalledPlugin(PluginId("plugin"), "1", setOf(PluginService.CONTENT)),
            FakeRuntime(invalidPayload = true),
            Json,
            ReaderDocumentSanitizer(),
        )

        val failure = assertIs<ReaderSourceResult.Failure>(source.fetch(release()))

        assertEquals("reader.source_payload_invalid", failure.code)
        assertEquals(false, failure.retryable)
    }

    private fun release() = ChapterRelease(
        ChapterReleaseId("release"), StoryId("story"), PluginId("plugin"), "source-story", "source-release",
        "Chapter", ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null), "en", 1, null,
    )
}

private class FakeRuntime(
    private val invalidPayload: Boolean = false,
) : PluginRuntime {
    var operation: PluginOperation? = null
    var sourceReleaseId: String? = null

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> {
        this.operation = operation
        sourceReleaseId = input.jsonObject.getValue("sourceReleaseId").jsonPrimitive.content
        return PluginCallResult.Success(
            buildJsonObject {
                put("title", "Chapter")
                putJsonArray("blocks") {
                    add(
                        buildJsonObject {
                            put("type", "paragraph")
                            put("text", if (invalidPayload) "" else "Safe paragraph")
                        },
                    )
                }
            },
        )
    }

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> = emptyList()
}
