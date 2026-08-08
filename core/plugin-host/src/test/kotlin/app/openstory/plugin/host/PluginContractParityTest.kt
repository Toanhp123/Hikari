package app.openstory.plugin.host

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
import app.openstory.plugin.host.js.JavaScriptPluginRuntime
import app.openstory.plugin.host.js.JsCapabilityDispatcher
import app.openstory.plugin.host.js.JsIsolateExecutor
import app.openstory.plugin.host.js.JsWireDtoDecoder
import app.openstory.plugin.host.selector.runtime.SelectorCanonicalFixtureTest
import app.openstory.plugin.host.selector.validation.PluginWireDtoValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginContractParityTest {
    @Test
    fun selectorAndJavaScriptFixturesReturnSameCatalogDetailsContract() = runTest {
        val selectorDetails = SelectorCanonicalFixtureTest().catalogDetails()
        val decoder = JsWireDtoDecoder(
            PluginWireDtoValidator(PluginUrlPolicy(setOf("fixture.example"))),
        )
        val runtime = JavaScriptPluginRuntime(
            executor = JsIsolateExecutor { _, _, _, _, _ -> JAVASCRIPT_FIXTURE_OUTPUT },
            dispatcher = JsCapabilityDispatcher(javaScriptManifest(), NoOpGateway),
        )
        val javascriptDetails = assertIs<AppResult.Success<*>>(
            runtime.invoke(
                source = "globalThis.openstoryPlugin = { details: async () => ({}) };",
                operation = "details",
                inputJson = "{}",
                decodeOutput = decoder::decodeCatalogDetails,
            ),
        ).value

        assertEquals(selectorDetails, javascriptDetails)
    }

    private fun javaScriptManifest(): PluginManifest = PluginManifest(
        id = "fixture.javascript",
        name = "JavaScript Contract Fixture",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://fixture.example/manifest.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = setOf("fixture.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.JAVASCRIPT,
        entry = "main.js",
    )

    private companion object {
        val JAVASCRIPT_FIXTURE_OUTPUT =
            """
            {
              "sourceId":"catalog-1",
              "sourceUrl":"https://fixture.example/story/catalog-1",
              "title":"Novel",
              "aliases":["Alias"],
              "authors":["Author"],
              "description":"Description",
              "genres":["Fantasy"],
              "contentType":"LIGHT_NOVEL",
              "languageTags":["en"],
              "image":{
                "url":"https://fixture.example/images/cover.jpg",
                "declaredHost":"fixture.example"
              },
              "score":{"value":8.5,"scale":10.0},
              "popularityRank":1
            }
            """.trimIndent()
    }
}

private object NoOpGateway : PluginHttpGateway {
    override suspend fun execute(
        request: PluginHttpRequest,
        budget: RequestBudget,
    ): AppResult<PluginHttpResponse> = error("Contract fixture must not call the network.")
}
