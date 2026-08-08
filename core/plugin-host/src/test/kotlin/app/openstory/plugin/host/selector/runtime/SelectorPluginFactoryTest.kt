package app.openstory.plugin.host.selector.runtime

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.SelectorDefinition
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.catalog.CatalogSearchSelector
import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SelectorPluginFactoryTest {
    @Test
    fun catalogSearchExecutesLoaderEvaluatorMapperAndValidator() = runTest {
        val definition = SelectorDefinition(
            catalog = CatalogSelectorEndpoints(
                search = CatalogSearchSelector(
                    request = SelectorRequestPlan(
                        listOf(HttpGet("/search?q={query}")),
                    ),
                    items = ListBinding(
                        css = "article",
                        item = ObjectBinding(
                            linkedMapOf(
                                "sourceId" to AttributeBinding(attribute = "data-id"),
                                "title" to TextBinding("h2"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val gateway = StaticGateway(
            "<article data-id='novel-1'><h2>Novel One</h2></article>",
        )

        val plugins = assertIs<AppResult.Success<SelectorPlugins>>(
            SelectorPluginFactory().create(manifest(), definition, gateway),
        ).value
        val result = assertNotNull(plugins.catalog).search(
            CatalogSearchRequest(query = "Novel"),
        )

        val page = assertIs<AppResult.Success<Page<CatalogCard>>>(result).value
        assertEquals("novel-1", page.items.single().sourceId)
        assertEquals("Novel One", page.items.single().title)
        assertEquals("https://allowed.example/search?q=Novel", gateway.requests.single().url)
    }

    private fun manifest() = PluginManifest(
        id = "community.fixture",
        name = "Fixture",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://allowed.example/update.json",
        api = PluginApiVersion(major = 1, minor = 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = setOf("allowed.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.DECLARATIVE,
        entry = "selector.json",
        declarativeOrigin = "https://allowed.example/",
    )

    private class StaticGateway(
        private val html: String,
    ) : PluginHttpGateway {
        val requests = mutableListOf<PluginHttpRequest>()

        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            requests += request
            return AppResult.Success(
                PluginHttpResponse(
                    status = 200,
                    headers = emptyMap(),
                    body = html.encodeToByteArray(),
                    decodedText = html,
                ),
            )
        }
    }
}
