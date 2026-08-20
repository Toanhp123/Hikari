package app.openstory.reader.content

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.manifest.ReaderCapability
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
    fun registryUsesChapterOperationCapabilityForReaderAvailability() = runTest {
        val readerPlugin = InstalledPlugin(PluginId("reader.plugin"), "1", setOf(PluginService.CONTENT))
        val runtime = FakeRuntime(enabledForChapter = listOf(readerPlugin))
        val registry = PluginReaderDocumentSourceRegistry(
            runtime,
            Json,
            ReaderDocumentSanitizer(),
        )

        assertEquals(listOf(PluginId("reader.plugin")), registry.enabled().map { it.pluginId })
        assertEquals(setOf(PluginId("reader.plugin")), registry.enabledPluginIds())
        assertEquals(PluginOperation.CONTENT_CHAPTER, runtime.enabledOperation)
    }

    @Test
    fun registrySeparatesReadableSourcesFromOfflineDownloadSources() = runTest {
        val onlineOnly = InstalledPlugin(
            PluginId("online.plugin"),
            "1",
            setOf(PluginService.CONTENT),
            readerCapability = ReaderCapability(offlineDownload = false),
        )
        val offline = InstalledPlugin(
            PluginId("offline.plugin"),
            "1",
            setOf(PluginService.CONTENT),
            readerCapability = ReaderCapability(offlineDownload = true),
        )
        val legacy = InstalledPlugin(PluginId("legacy.plugin"), "1", setOf(PluginService.CONTENT))
        val registry = PluginReaderDocumentSourceRegistry(
            FakeRuntime(enabledForChapter = listOf(onlineOnly, offline, legacy)),
            Json,
            ReaderDocumentSanitizer(),
        )

        assertEquals(
            setOf(PluginId("online.plugin"), PluginId("offline.plugin"), PluginId("legacy.plugin")),
            registry.enabledPluginIds(),
        )
        assertEquals(
            setOf(PluginId("offline.plugin"), PluginId("legacy.plugin")),
            registry.offlineDownloadPluginIds(),
        )
    }

    @Test
    fun remoteImagePayloadRequiresExplicitReaderCapability() = runTest {
        val denied = PluginReaderDocumentSource(
            InstalledPlugin(PluginId("plugin"), "1", setOf(PluginService.CONTENT)),
            FakeRuntime(imagePayload = true),
            Json,
            ReaderDocumentSanitizer(),
        )
        val allowed = PluginReaderDocumentSource(
            InstalledPlugin(
                PluginId("plugin"),
                "1",
                setOf(PluginService.CONTENT),
                readerCapability = ReaderCapability(offlineDownload = false, remoteImages = true),
            ),
            FakeRuntime(imagePayload = true),
            Json,
            ReaderDocumentSanitizer(),
        )

        assertEquals(
            "reader.document_block_invalid",
            assertIs<ReaderSourceResult.Failure>(denied.fetch(release())).code,
        )
        assertIs<ReaderSourceResult.Success>(allowed.fetch(release()))
    }

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
    private val imagePayload: Boolean = false,
    private val enabledForChapter: List<InstalledPlugin> = emptyList(),
) : PluginRuntime {
    var operation: PluginOperation? = null
    var sourceReleaseId: String? = null
    var enabledOperation: PluginOperation? = null

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
                        if (imagePayload) {
                            buildJsonObject {
                                put("type", "image")
                                put("stableId", "hash/page-001.png")
                                put("imageUrl", "https://node.example/page-001.png")
                            }
                        } else {
                            buildJsonObject {
                                put("type", "paragraph")
                                put("text", if (invalidPayload) "" else "Safe paragraph")
                            }
                        },
                    )
                }
            },
        )
    }

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> = emptyList()

    override suspend fun enabled(operation: PluginOperation): List<InstalledPlugin> {
        enabledOperation = operation
        return enabledForChapter
    }
}
