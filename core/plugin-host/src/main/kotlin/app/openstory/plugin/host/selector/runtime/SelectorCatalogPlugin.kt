package app.openstory.plugin.host.selector.runtime

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import app.openstory.plugin.host.selector.mapper.CatalogSelectorMapper

internal class SelectorCatalogPlugin(
    private val endpoints: CatalogSelectorEndpoints,
    private val executor: SelectorEndpointExecutor,
    private val mapper: CatalogSelectorMapper,
) : CatalogPlugin {
    override suspend fun home(
        request: CatalogHomeRequest,
    ): AppResult<List<CatalogSection>> {
        val endpoint = endpoints.home ?: return unavailable("catalog.home")
        val budget = executor.budget(endpoint.request)
        val input = mapOf(
            "languageTags" to request.languageTags.sorted().joinToString(","),
            "contentTypes" to request.contentTypes.map(Enum<*>::name).sorted().joinToString(","),
        )
        return executor.load(endpoint.request, input).flatMapSuspend { document ->
            executor.evaluate(document, endpoint.sections, "sections", budget)
                .flatMap(mapper::mapHome)
        }
    }

    override suspend fun search(
        request: CatalogSearchRequest,
    ): AppResult<Page<CatalogCard>> {
        val endpoint = endpoints.search ?: return unavailable("catalog.search")
        val budget = executor.budget(endpoint.request)
        val input = linkedMapOf("query" to request.query)
        request.nextToken?.let { input["nextToken"] = it }
        request.filterValues.forEach { (id, values) -> input[id] = values.joinToString(",") }
        return executor.load(endpoint.request, input).flatMapSuspend { document ->
            executor.evaluate(document, endpoint.items, "items", budget).flatMapSuspend { items ->
                evaluateOptional(document, endpoint.nextToken, "nextToken", budget).flatMap { token ->
                    mapper.mapSearch(items, token, endpoint.nextTokenKind)
                }
            }
        }
    }

    override suspend fun details(sourceId: String): AppResult<CatalogDetails> {
        val endpoint = endpoints.details ?: return unavailable("catalog.details")
        val budget = executor.budget(endpoint.request)
        return executor.load(endpoint.request, mapOf("sourceId" to sourceId))
            .flatMapSuspend { document ->
                executor.evaluate(document, endpoint.details, "details", budget)
                    .flatMap(mapper::mapDetails)
            }
    }

    override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> {
        val endpoint = endpoints.filters ?: return unavailable("catalog.filters")
        return mapper.mapFilters(endpoint.filters)
    }

    private suspend fun evaluateOptional(
        document: app.openstory.plugin.host.selector.HtmlDocument,
        binding: app.openstory.plugin.api.selector.SelectorBinding?,
        path: String,
        budget: app.openstory.plugin.host.selector.binding.SelectorEvaluationBudget,
    ): AppResult<app.openstory.plugin.host.selector.binding.SelectorBoundValue?> =
        binding?.let { executor.evaluate(document, it, path, budget) }
            ?: AppResult.Success(null)
}
