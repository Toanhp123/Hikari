package app.openstory.plugin.host.selector.runtime

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.content.ChapterDocument
import app.openstory.plugin.api.content.ChapterSyncDelta
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.api.content.ContentSearchRequest
import app.openstory.plugin.api.content.ContentStoryCandidate
import app.openstory.plugin.api.content.ContentStoryDetails
import app.openstory.plugin.api.content.SourceChapterRelease
import app.openstory.plugin.api.selector.content.ContentSelectorEndpoints
import app.openstory.plugin.host.selector.mapper.ContentSelectorMapper

internal class SelectorContentPlugin(
    private val endpoints: ContentSelectorEndpoints,
    private val executor: SelectorEndpointExecutor,
    private val mapper: ContentSelectorMapper,
) : ContentPlugin {
    override suspend fun search(
        request: ContentSearchRequest,
    ): AppResult<Page<ContentStoryCandidate>> {
        val endpoint = endpoints.search ?: return unavailable("content.search")
        val budget = executor.budget(endpoint.request)
        val input = linkedMapOf("query" to request.query)
        request.nextToken?.let { input["nextToken"] = it }
        return executor.load(endpoint.request, input).flatMapSuspend { document ->
            executor.evaluate(document, endpoint.items, "items", budget).flatMapSuspend { items ->
                evaluateOptional(document, endpoint.nextToken, "nextToken", budget).flatMap { token ->
                    mapper.mapSearch(items, token)
                }
            }
        }
    }

    override suspend fun story(sourceStoryId: String): AppResult<ContentStoryDetails> =
        executeSingle(
            endpoint = endpoints.story,
            input = mapOf("sourceStoryId" to sourceStoryId),
            path = "story",
            request = { it.request },
            binding = { it.details },
            mapper = mapper::mapStory,
        )

    override suspend fun latest(
        sourceStoryId: String,
        limit: Int,
    ): AppResult<List<SourceChapterRelease>> = executeReleases(
        endpoint = endpoints.latest,
        input = mapOf("sourceStoryId" to sourceStoryId, "limit" to limit.toString()),
        name = "content.latest",
    )

    override suspend fun allChapters(
        sourceStoryId: String,
    ): AppResult<List<SourceChapterRelease>> = executeReleases(
        endpoint = endpoints.allChapters,
        input = mapOf("sourceStoryId" to sourceStoryId),
        name = "content.allChapters",
    )

    override suspend fun sync(
        sourceStoryId: String,
        cursor: String?,
    ): AppResult<ChapterSyncDelta> {
        val endpoint = endpoints.sync ?: return unavailable("content.sync")
        val budget = executor.budget(endpoint.request)
        val input = mapOf("sourceStoryId" to sourceStoryId, "cursor" to cursor.orEmpty())
        return executor.load(endpoint.request, input).flatMapSuspend { document ->
            executor.evaluate(document, endpoint.delta, "delta", budget)
                .flatMap(mapper::mapSync)
        }
    }

    override suspend fun chapter(sourceReleaseId: String): AppResult<ChapterDocument> {
        val endpoint = endpoints.chapter ?: return unavailable("content.chapter")
        val budget = executor.budget(endpoint.request)
        return executor.load(
            endpoint.request,
            mapOf("sourceReleaseId" to sourceReleaseId),
        ).flatMapSuspend { document ->
            mapper.mapChapter(document, endpoint.document, budget)
        }
    }

    private suspend fun executeReleases(
        endpoint: app.openstory.plugin.api.selector.content.ContentReleasesSelector?,
        input: Map<String, String>,
        name: String,
    ): AppResult<List<SourceChapterRelease>> {
        endpoint ?: return unavailable(name)
        val budget = executor.budget(endpoint.request)
        return executor.load(endpoint.request, input).flatMapSuspend { document ->
            executor.evaluate(document, endpoint.releases, "releases", budget)
                .flatMap(mapper::mapReleases)
        }
    }

    private suspend fun <E, T> executeSingle(
        endpoint: E?,
        input: Map<String, String>,
        path: String,
        request: (E) -> app.openstory.plugin.api.selector.SelectorRequestPlan,
        binding: (E) -> app.openstory.plugin.api.selector.SelectorBinding,
        mapper: (app.openstory.plugin.host.selector.binding.SelectorBoundValue) -> AppResult<T>,
    ): AppResult<T> {
        endpoint ?: return unavailable("content.$path")
        val plan = request(endpoint)
        val budget = executor.budget(plan)
        return executor.load(plan, input).flatMapSuspend { document ->
            executor.evaluate(document, binding(endpoint), path, budget).flatMap(mapper)
        }
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
