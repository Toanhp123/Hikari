package app.openstory.plugin.host.js

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JavaScriptCatalogPlugin(
    private val source: String,
    private val runtime: JavaScriptPluginRuntime,
    private val decoder: JsWireDtoDecoder,
) : CatalogPlugin {
    override suspend fun home(
        request: CatalogHomeRequest,
    ): AppResult<List<CatalogSection>> = runtime.invoke(
        source = source,
        operation = "home",
        inputJson = JSON.encodeToString(CatalogHomeRequest.serializer(), request),
        decodeOutput = decoder::decodeCatalogHome,
    )

    override suspend fun search(
        request: CatalogSearchRequest,
    ): AppResult<Page<CatalogCard>> = runtime.invoke(
        source = source,
        operation = "search",
        inputJson = JSON.encodeToString(CatalogSearchRequest.serializer(), request),
        decodeOutput = decoder::decodeCatalogSearch,
    )

    override suspend fun details(sourceId: String): AppResult<CatalogDetails> = runtime.invoke(
        source = source,
        operation = "details",
        inputJson = buildJsonObject { put("sourceId", sourceId) }.toString(),
        decodeOutput = decoder::decodeCatalogDetails,
    )

    override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> = runtime.invoke(
        source = source,
        operation = "filters",
        inputJson = EMPTY_INPUT,
        decodeOutput = decoder::decodeCatalogFilters,
    )

    private companion object {
        val JSON = Json { explicitNulls = false }
        const val EMPTY_INPUT = "{}"
    }
}
