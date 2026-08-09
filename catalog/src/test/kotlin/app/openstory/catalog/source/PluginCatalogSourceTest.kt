package app.openstory.catalog.source

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.CatalogHomeOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogItemDto
import app.openstory.plugins.api.protocol.catalog.CatalogSectionDto
import app.openstory.plugins.api.protocol.catalog.ScoreDto
import app.openstory.plugins.api.protocol.catalog.WireContentType
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginCatalogSourceTest {
    @Test
    fun homeMapsWireDtoWithoutLosingSourceIdentity() = runTest {
        val runtime = FakePluginRuntime.success(
            operation = PluginOperation.CATALOG_HOME,
            payload = Json.encodeToJsonElement(
                CatalogHomeOutputDto(
                    sections = listOf(
                        CatalogSectionDto(
                            sourceId = "top",
                            title = "Top manga",
                            items = listOf(
                                CatalogItemDto(
                                    sourceId = "123",
                                    title = "Example",
                                    contentType = WireContentType.MANGA,
                                    authors = listOf("Author"),
                                    coverUrl = "https://cdn.myanimelist.net/cover.jpg",
                                    score = ScoreDto(8.4, 10.0),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val source = PluginCatalogSource(hostedPlugin("org.example.catalog"), runtime, Json)

        val result = assertIs<CatalogSourceResult.Success<List<SourceSection>>>(
            source.home(SourceHomeRequest()),
        )

        assertEquals("123", result.value.single().items.single().sourceId)
        assertEquals(SourceContentType.MANGA, result.value.single().items.single().contentType)
        assertEquals(PluginOperation.CATALOG_HOME, runtime.lastOperation)
    }

    @Test
    fun runtimeFailureBecomesCatalogSourceFailure() = runTest {
        val runtime = FakePluginRuntime.failure("plugin.rate_limited", retryable = true)
        val source = PluginCatalogSource(hostedPlugin("org.example.catalog"), runtime, Json)

        val result = assertIs<CatalogSourceResult.Failure>(
            source.search(SourceSearchRequest("x")),
        )

        assertEquals("plugin.rate_limited", result.failure.code)
        assertTrue(result.failure.retryable)
    }
}

private class FakePluginRuntime(
    private val result: PluginCallResult<JsonElement>,
    private val expectedOperation: PluginOperation? = null,
) : PluginRuntime {
    var lastOperation: PluginOperation? = null
        private set

    override suspend fun invoke(
        pluginId: PluginId,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> {
        expectedOperation?.let { assertEquals(it, operation) }
        lastOperation = operation
        return result
    }

    override suspend fun enabled(service: PluginService): List<InstalledPlugin> = emptyList()

    companion object {
        fun success(
            operation: PluginOperation,
            payload: JsonElement,
        ) = FakePluginRuntime(PluginCallResult.Success(payload), operation)

        fun failure(code: String, retryable: Boolean) = FakePluginRuntime(
            PluginCallResult.Failure(code = code, retryable = retryable),
        )
    }
}

private fun hostedPlugin(id: String) = InstalledPlugin(
    pluginId = PluginId(id),
    version = "2.3.4",
    services = setOf(PluginService.CATALOG),
)
