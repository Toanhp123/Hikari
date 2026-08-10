package app.openstory.library.content

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PageDto
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

class PluginContentSourceTest {
    @Test
    fun searchInvokesContentProtocolAndAppliesLibraryCandidateCap() = runTest {
        val runtime = RecordingRuntime { _, operation, _ ->
            assertEquals(PluginOperation.CONTENT_SEARCH, operation)
            PluginCallResult.Success(
                Json.encodeToJsonElement(
                    PageDto(
                        listOf(
                            ContentStoryCandidateDto("one", "One"),
                            ContentStoryCandidateDto("two", "Two"),
                        ),
                    ),
                ),
            )
        }
        val source = source(runtime)

        val result = assertIs<ContentSourceResult.Success<List<ContentSourceStory>>>(
            source.search("Story", limit = 1),
        )

        assertEquals(listOf("one"), result.value.map { it.sourceStoryId })
        assertEquals(1, runtime.invocations)
    }

    @Test
    fun urlOutsideAcceptedHostsNeverInvokesRuntime() = runTest {
        val runtime = RecordingRuntime { _, _, _ -> error("runtime must not be called") }
        val source = source(runtime)

        val result = assertIs<ContentSourceResult.Failure>(
            source.resolveUrl("https://other.example/story/1"),
        )

        assertEquals("content.url_host_denied", result.failure.code)
        assertEquals(0, runtime.invocations)
    }

    @Test
    fun unavailableResolveOperationBecomesBoundedSourceFailure() = runTest {
        val runtime = RecordingRuntime { _, operation, _ ->
            assertEquals(PluginOperation.CONTENT_RESOLVE_URL, operation)
            PluginCallResult.Failure("plugin.operation_unavailable", false)
        }
        val source = source(runtime)

        val result = assertIs<ContentSourceResult.Failure>(
            source.resolveUrl("https://reader.example/story/1"),
        )

        assertEquals("content.url_resolution_unsupported", result.failure.code)
    }

    @Test
    fun runtimeInvocationsAreSerializedPerSource() = runTest {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val runtime = RecordingRuntime { _, _, _ ->
            val current = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, current) }
            delay(10L)
            active.decrementAndGet()
            PluginCallResult.Success(Json.encodeToJsonElement(PageDto<ContentStoryCandidateDto>(emptyList())))
        }
        val source = source(runtime)

        coroutineScope {
            listOf("one", "two").map { query ->
                async { source.search(query, limit = 1) }
            }.awaitAll()
        }

        assertEquals(1, maxActive.get())
    }
}

private fun source(runtime: PluginRuntime) = PluginContentSource(
    installed = InstalledPlugin(
        pluginId = PluginId("org.example.content"),
        version = "1.0.0",
        services = setOf(PluginService.CONTENT),
        allowedNetworkHosts = setOf("reader.example"),
    ),
    runtime = runtime,
    json = Json,
)

private class RecordingRuntime(
    private val invokeBlock: suspend (PluginId, PluginOperation, JsonElement) -> PluginCallResult<JsonElement>,
) : PluginRuntime {
    var invocations = 0
        private set

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> {
        invocations += 1
        return invokeBlock(pluginId, operation, input)
    }

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> = emptyList()
}
