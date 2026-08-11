package app.openstory.library.mapping

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.content.ContentSourceRegistry
import app.openstory.library.matching.ContentStoryFeatures
import app.openstory.library.matching.ContentStoryMatcher
import java.net.URI
import java.util.Locale

class ContentMappingSearchService(
    private val projections: CatalogStoryProjectionRepository,
    private val sources: ContentSourceRegistry,
    matcher: ContentStoryMatcher,
    private val policy: ContentMappingSearchPolicy = ContentMappingSearchPolicy(),
) {
    private val planner = ContentSearchPlanner(policy)
    private val executor = ContentSourceSearchExecutor(matcher, policy)

    suspend fun quick(
        storyId: StoryId,
        preferredPluginIds: List<PluginId> = emptyList(),
    ): ContentMappingSearchReport {
        val context = prepare(storyId, preferredPluginIds)
            ?: return globalFailure(ContentMappingSearchStage.QUICK, "content.story_not_found")
        return executor.searchStage(
            stage = ContentMappingSearchStage.QUICK,
            canonical = context.canonical,
            queries = context.queries,
            selected = context.plan.quick,
            timeoutMillis = policy.quickSourceTimeoutMillis,
        )
    }

    suspend fun deferred(
        storyId: StoryId,
        preferredPluginIds: List<PluginId> = emptyList(),
    ): ContentMappingSearchReport {
        val context = prepare(storyId, preferredPluginIds)
            ?: return globalFailure(ContentMappingSearchStage.DEFERRED, "content.story_not_found")
        return executor.searchStage(
            stage = ContentMappingSearchStage.DEFERRED,
            canonical = context.canonical,
            queries = context.queries,
            selected = context.plan.deferred,
            timeoutMillis = policy.deferredSourceTimeoutMillis,
        )
    }

    suspend fun searchAll(
        storyId: StoryId,
        preferredPluginIds: List<PluginId> = emptyList(),
    ): ContentMappingSearchReport {
        val context = prepare(storyId, preferredPluginIds)
            ?: return globalFailure(ContentMappingSearchStage.ALL, "content.story_not_found")
        val quick = executor.searchStage(
            ContentMappingSearchStage.QUICK,
            context.canonical,
            context.queries,
            context.plan.quick,
            policy.quickSourceTimeoutMillis,
        )
        val timedOutPluginIds = quick.failures
            .filter { failure -> failure.code == SOURCE_TIMEOUT }
            .mapNotNull(ContentMappingSearchFailure::pluginId)
            .toSet()
        val timedOutQuickSources = context.plan.quick.filter { source -> source.pluginId in timedOutPluginIds }
        val deferred = executor.searchStage(
            ContentMappingSearchStage.DEFERRED,
            context.canonical,
            context.queries,
            timedOutQuickSources + context.plan.deferred,
            policy.deferredSourceTimeoutMillis,
        )
        return mergeReports(quick, deferred)
    }

    suspend fun resolveUrl(
        storyId: StoryId,
        url: String,
    ): ContentMappingSearchReport {
        val host = httpsHost(url)
        val projection = host?.let { projections.find(storyId) }
        val eligible = if (host != null && projection != null) {
            sources.enabled()
                .filter { source -> host in source.allowedHosts }
                .sortedBy { source -> source.pluginId.value }
        } else {
            emptyList()
        }
        return when {
            host == null -> globalFailure(ContentMappingSearchStage.URL, "content.url_invalid")
            projection == null -> globalFailure(ContentMappingSearchStage.URL, "content.story_not_found")
            eligible.isEmpty() -> globalFailure(ContentMappingSearchStage.URL, "content.url_host_unclaimed")
            else -> executor.resolveUrl(projection.toFeatures(), url, eligible)
        }
    }

    private suspend fun prepare(
        storyId: StoryId,
        preferredPluginIds: List<PluginId>,
    ): SearchContext? {
        val projection = projections.find(storyId) ?: return null
        val enabled = sources.enabled().sortedBy { source -> source.pluginId.value }
        return SearchContext(
            canonical = projection.toFeatures(),
            queries = planner.queryVariants(projection),
            plan = planner.plan(enabled, preferredPluginIds),
        )
    }
}

private data class SearchContext(
    val canonical: ContentStoryFeatures,
    val queries: List<String>,
    val plan: ContentSearchPlan,
)

private fun CatalogStoryProjection.toFeatures() = ContentStoryFeatures(
    title = title,
    aliases = aliases,
    authors = authors,
    contentType = contentType,
)

private fun mergeReports(
    quick: ContentMappingSearchReport,
    deferred: ContentMappingSearchReport,
): ContentMappingSearchReport {
    val retriedPluginIds = deferred.searchedPluginIds.toSet()
    return ContentMappingSearchReport(
        stage = ContentMappingSearchStage.ALL,
        searchedPluginIds = (quick.searchedPluginIds + deferred.searchedPluginIds).distinct(),
        queryVariants = quick.queryVariants,
        candidates = (quick.candidates + deferred.candidates)
            .distinctBy { candidate -> candidate.pluginId to candidate.sourceStoryId },
        failures = quick.failures.filterNot { failure -> failure.pluginId in retriedPluginIds } + deferred.failures,
    )
}

private fun globalFailure(
    stage: ContentMappingSearchStage,
    code: String,
) = ContentMappingSearchReport(
    stage = stage,
    searchedPluginIds = emptyList(),
    queryVariants = emptyList(),
    candidates = emptyList(),
    failures = listOf(ContentMappingSearchFailure(null, code, false)),
)

private fun httpsHost(value: String): String? = value
    .takeIf { it.length <= MAX_URL_LENGTH }
    ?.let { runCatching { URI(it) }.getOrNull() }
    ?.takeIf { uri -> uri.scheme == "https" && uri.userInfo == null }
    ?.host
    ?.lowercase(Locale.ROOT)

private const val MAX_URL_LENGTH = 4_096
private const val SOURCE_TIMEOUT = "content.source_timeout"
