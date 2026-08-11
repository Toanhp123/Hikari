package app.openstory.library.mapping

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.common.id.PluginId
import app.openstory.library.content.ContentSource
import java.text.Normalizer
import java.util.Locale

internal data class ContentSearchPlan(
    val quick: List<ContentSource>,
    val deferred: List<ContentSource>,
)

internal class ContentSearchPlanner(
    private val policy: ContentMappingSearchPolicy,
) {
    fun plan(
        enabled: List<ContentSource>,
        preferredPluginIds: List<PluginId>,
    ): ContentSearchPlan {
        val preferenceRank = preferredPluginIds.distinct().withIndex()
            .associate { indexed -> indexed.value to indexed.index }
        val ordered = enabled.sortedWith(
            compareBy<ContentSource> { source -> preferenceRank[source.pluginId] ?: Int.MAX_VALUE }
                .thenBy { source -> source.pluginId.value },
        )
        return ContentSearchPlan(
            quick = ordered.take(policy.quickSourceCount),
            deferred = ordered.drop(policy.quickSourceCount),
        )
    }

    fun queryVariants(projection: CatalogStoryProjection): List<String> {
        val aliasOrder = compareBy<String> { value -> value.lowercase(Locale.ROOT) }
            .thenBy { value -> value }
        val raw = listOf(projection.title) + projection.aliases.sortedWith(aliasOrder)
        val seen = linkedSetOf<String>()
        return buildList {
            raw.forEach { value ->
                val bounded = value.trim().take(MAX_QUERY_LENGTH).trim()
                val key = normalizeQueryKey(bounded)
                if (bounded.isNotEmpty() && seen.add(key)) add(bounded)
            }
        }.take(policy.maxQueryVariants)
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 1_024
    }
}

private fun normalizeQueryKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .trim()
