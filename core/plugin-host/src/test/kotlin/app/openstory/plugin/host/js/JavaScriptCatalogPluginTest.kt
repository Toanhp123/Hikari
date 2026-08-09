package app.openstory.plugin.host.js

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.PluginUrlPolicy
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class JavaScriptCatalogPluginTest {
    @Test
    fun homeUsesJavascriptRuntimeAndHostOwnedDecoder() = runTest {
        val fixture = fixture()

        val home = fixture.plugin.home(CatalogHomeRequest()).successValue("home")

        assertEquals("JavaScript Lantern", home.single().items.single().title)
        assertEquals(listOf("home"), fixture.executor.operations)
    }

    @Test
    fun searchUsesJavascriptRuntimeAndHostOwnedDecoder() = runTest {
        val fixture = fixture()

        val search = fixture.plugin.search(CatalogSearchRequest(query = "lantern")).successValue("search")

        assertEquals("JavaScript Lantern", search.items.single().title)
        assertEquals(listOf("search"), fixture.executor.operations)
        assertEquals(true, fixture.executor.inputs.single().contains("\"query\":\"lantern\""))
    }

    @Test
    fun detailsUsesJavascriptRuntimeAndHostOwnedDecoder() = runTest {
        val fixture = fixture()

        val details = fixture.plugin.details("javascript-lantern").successValue("details")

        assertEquals("JavaScript Lantern", details.title)
        assertEquals(listOf("details"), fixture.executor.operations)
        assertEquals("{\"sourceId\":\"javascript-lantern\"}", fixture.executor.inputs.single())
    }

    @Test
    fun filtersUseJavascriptRuntimeAndHostOwnedDecoder() = runTest {
        val fixture = fixture()

        val filters = fixture.plugin.filters().successValue("filters")

        assertEquals(emptyList(), filters)
        assertEquals(listOf("filters"), fixture.executor.operations)
        assertEquals("{}", fixture.executor.inputs.single())
    }

    private fun fixture(): CatalogPluginFixture {
        val executor = FixtureExecutor()
        val decoder = JsWireDtoDecoder(
            PluginWireDtoValidator(PluginUrlPolicy(setOf(HOST))),
        )
        return CatalogPluginFixture(
            plugin = JavaScriptCatalogPlugin(
                source = "globalThis.openstoryPlugin = {};",
                runtime = JavaScriptPluginRuntime(
                    executor = executor,
                    dispatcher = JsCapabilityDispatcher(manifest(), NoOpGateway),
                ),
                decoder = decoder,
            ),
            executor = executor,
        )
    }

    private fun manifest() = PluginManifest(
        id = "org.openstory.catalog.javascript.fixture",
        name = "JavaScript Fixture",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://openstory.example/javascript-fixture.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = setOf(HOST),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.JAVASCRIPT,
        entry = "main.js",
    )

    private class FixtureExecutor : JsIsolateExecutor {
        val operations = mutableListOf<String>()
        val inputs = mutableListOf<String>()

        override suspend fun execute(
            source: String,
            operation: String,
            inputJson: String,
            limits: JsRuntimeLimits,
            bridge: suspend (String) -> String,
        ): String {
            operations += operation
            inputs += inputJson
            return when (operation) {
                "home" -> HOME_JSON
                "search" -> SEARCH_JSON
                "details" -> DETAILS_JSON
                "filters" -> "[]"
                else -> error("Unexpected operation: $operation")
            }
        }
    }

    private data class CatalogPluginFixture(
        val plugin: JavaScriptCatalogPlugin,
        val executor: FixtureExecutor,
    )

    private companion object {
        const val HOST = "javascript.openstory.example"
        val HOME_JSON =
            """
            [
              {
                "sourceId": "javascript-featured",
                "title": "JavaScript Featured",
                "items": [
                  {
                    "sourceId": "javascript-lantern",
                    "title": "JavaScript Lantern",
                    "contentType": "WEB_NOVEL",
                    "authors": ["OpenStory JS"],
                    "image": null,
                    "score": null
                  }
                ]
              }
            ]
            """.trimIndent()
        val SEARCH_JSON =
            """
            {
              "items": [
                {
                  "sourceId": "javascript-lantern",
                  "title": "JavaScript Lantern",
                  "contentType": "WEB_NOVEL",
                  "authors": ["OpenStory JS"],
                  "image": null,
                  "score": null
                }
              ],
              "nextToken": null
            }
            """.trimIndent()
        val DETAILS_JSON =
            """
            {
              "sourceId": "javascript-lantern",
              "sourceUrl": "https://javascript.openstory.example/story/javascript-lantern",
              "title": "JavaScript Lantern",
              "aliases": [],
              "authors": ["OpenStory JS"],
              "description": "JavaScript runtime fixture metadata.",
              "genres": ["Fantasy"],
              "contentType": "WEB_NOVEL",
              "languageTags": ["en"],
              "image": null,
              "score": null,
              "popularityRank": 7
            }
            """.trimIndent()
    }
}

private object NoOpGateway : PluginHttpGateway {
    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> = error("Unit fixture must not call the host bridge.")
}

private fun <T> AppResult<T>.successValue(operation: String): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> fail("$operation failed with ${error.code}")
}
