package app.openstory.library.merge

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRejection

const val CONTENT_MAPPING_PROTECTED_CONFLICT = "content_mapping.protected_conflict"
const val CONTENT_MAPPING_RESOLUTION_INVALID = "content_mapping.resolution_invalid"
const val CONTENT_MAPPING_REVERSAL_STATE_CHANGED = "content_mapping.reversal_state_changed"

data class ContentMappingMergePlan(
    val mappings: List<ContentMapping>,
    val rejections: List<ContentMappingRejection>,
    val pluginsToRecompute: Set<PluginId>,
)

data class ContentMappingMergeResolution(
    val pluginId: PluginId,
    val sourceStoryId: String,
) {
    init {
        require(sourceStoryId.isNotBlank())
    }
}

data class ContentMappingProtectedConflict(
    val pluginId: PluginId,
    val candidateSourceStoryIds: Set<String>,
) {
    init {
        require(candidateSourceStoryIds.size >= 2)
    }
}

sealed interface ContentMappingMergeDecision {
    data class Ready(val plan: ContentMappingMergePlan) : ContentMappingMergeDecision

    data class RequiresReview(
        val reasons: Set<String>,
        val protectedConflicts: List<ContentMappingProtectedConflict>,
    ) : ContentMappingMergeDecision {
        init {
            require(reasons.isNotEmpty())
        }
    }
}

class ContentMappingStoryMergePolicy {
    fun reversalBlockers(
        survivorId: StoryId,
        currentMappings: List<ContentMapping>,
        currentRejections: List<ContentMappingRejection>,
        survivorBeforeMappings: List<ContentMapping>,
        retiredBeforeMappings: List<ContentMapping>,
        survivorBeforeRejections: List<ContentMappingRejection>,
        retiredBeforeRejections: List<ContentMappingRejection>,
    ): Set<String> {
        val historicalMappings = survivorBeforeMappings + retiredBeforeMappings
        val mappingsMatch = currentMappingsAreValidHistoricalMerge(survivorId, currentMappings, historicalMappings)
        val expectedRejections = mergeRejections(
            survivorId,
            survivorBeforeRejections + retiredBeforeRejections,
        ).toSet()
        val rejectionsMatch = currentRejections.toSet() == expectedRejections
        return if (mappingsMatch && rejectionsMatch) emptySet() else setOf(CONTENT_MAPPING_REVERSAL_STATE_CHANGED)
    }

    private fun currentMappingsAreValidHistoricalMerge(
        survivorId: StoryId,
        currentMappings: List<ContentMapping>,
        historicalMappings: List<ContentMapping>,
    ): Boolean {
        val historicalByPlugin = historicalMappings.groupBy(ContentMapping::pluginId)
        val currentByPlugin = currentMappings.groupBy(ContentMapping::pluginId)
        val noUnexpectedPlugin = currentByPlugin.keys.all { it in historicalByPlugin }
        return noUnexpectedPlugin && historicalByPlugin.all { (pluginId, historical) ->
            validCurrentMappingGroup(
                survivorId = survivorId,
                historical = historical,
                current = currentByPlugin[pluginId].orEmpty(),
            )
        }
    }

    private fun validCurrentMappingGroup(
        survivorId: StoryId,
        historical: List<ContentMapping>,
        current: List<ContentMapping>,
    ): Boolean {
        val protected = historical.filter { it.origin.isProtected }
        val protectedTargets = protected.map(ContentMapping::sourceStoryId).distinct()
        val automatedTargets = historical.map(ContentMapping::sourceStoryId).distinct()
        val allowed = when {
            protectedTargets.size > 1 -> null
            protected.isNotEmpty() -> setOfNotNull(chooseStable(protected)?.copy(storyId = survivorId))
            automatedTargets.size > 1 -> emptySet()
            else -> setOfNotNull(chooseStable(historical)?.copy(storyId = survivorId))
        }
        return allowed != null && current.toSet() == allowed
    }

    fun plan(
        survivorId: StoryId,
        leftMappings: List<ContentMapping>,
        rightMappings: List<ContentMapping>,
        leftRejections: List<ContentMappingRejection>,
        rightRejections: List<ContentMappingRejection>,
        resolutions: List<ContentMappingMergeResolution> = emptyList(),
    ): ContentMappingMergeDecision {
        val mappingsByPlugin = (leftMappings + rightMappings).groupBy(ContentMapping::pluginId)
        val protectedConflicts = mappingsByPlugin.mapNotNull { (pluginId, mappings) ->
            val protectedTargets = mappings.filter { it.origin.isProtected }
                .map(ContentMapping::sourceStoryId)
                .toSortedSet()
            protectedTargets.takeIf { it.size > 1 }?.let {
                ContentMappingProtectedConflict(pluginId, it)
            }
        }.sortedBy { it.pluginId.value }

        val resolutionsByPlugin = resolutions.groupBy(ContentMappingMergeResolution::pluginId)
        val hasDuplicateResolution = resolutionsByPlugin.values.any { it.size != 1 }
        val conflictPlugins = protectedConflicts.mapTo(mutableSetOf(), ContentMappingProtectedConflict::pluginId)
        val hasExtraneousResolution = resolutionsByPlugin.keys.any { it !in conflictPlugins }
        val hasInvalidTarget = protectedConflicts.any { conflict ->
            val resolution = resolutionsByPlugin[conflict.pluginId]?.singleOrNull() ?: return@any false
            resolution.sourceStoryId !in conflict.candidateSourceStoryIds
        }
        val unresolved = protectedConflicts.filter { it.pluginId !in resolutionsByPlugin }
        return when {
            hasDuplicateResolution || hasExtraneousResolution || hasInvalidTarget ->
                ContentMappingMergeDecision.RequiresReview(
                    reasons = setOf(CONTENT_MAPPING_RESOLUTION_INVALID),
                    protectedConflicts = protectedConflicts,
                )
            unresolved.isNotEmpty() -> ContentMappingMergeDecision.RequiresReview(
                reasons = setOf(CONTENT_MAPPING_PROTECTED_CONFLICT),
                protectedConflicts = unresolved,
            )
            else -> readyPlan(
                survivorId = survivorId,
                mappingsByPlugin = mappingsByPlugin,
                resolutionsByPlugin = resolutionsByPlugin,
                rejections = leftRejections + rightRejections,
            )
        }
    }

    private fun readyPlan(
        survivorId: StoryId,
        mappingsByPlugin: Map<PluginId, List<ContentMapping>>,
        resolutionsByPlugin: Map<PluginId, List<ContentMappingMergeResolution>>,
        rejections: List<ContentMappingRejection>,
    ): ContentMappingMergeDecision.Ready {
        val recompute = linkedSetOf<PluginId>()
        val mergedMappings = mappingsByPlugin.entries
            .sortedBy { it.key.value }
            .mapNotNull { (pluginId, mappings) ->
                chooseMapping(pluginId, mappings, resolutionsByPlugin, recompute)
                    ?.copy(storyId = survivorId)
            }
        val mergedRejections = mergeRejections(survivorId, rejections)
        return ContentMappingMergeDecision.Ready(
            ContentMappingMergePlan(
                mappings = mergedMappings,
                rejections = mergedRejections,
                pluginsToRecompute = recompute.toSet(),
            ),
        )
    }

    private fun chooseMapping(
        pluginId: PluginId,
        mappings: List<ContentMapping>,
        resolutionsByPlugin: Map<PluginId, List<ContentMappingMergeResolution>>,
        recompute: MutableSet<PluginId>,
    ): ContentMapping? {
        val protected = mappings.filter { it.origin.isProtected }
        val resolvedTarget = resolutionsByPlugin[pluginId]?.singleOrNull()?.sourceStoryId
        val automatedTargets = mappings.map(ContentMapping::sourceStoryId).distinct()
        return when {
            resolvedTarget != null -> chooseStable(protected.filter { it.sourceStoryId == resolvedTarget })
            protected.isNotEmpty() -> {
                val target = protected.map(ContentMapping::sourceStoryId).distinct().single()
                chooseStable(protected.filter { it.sourceStoryId == target })
            }
            automatedTargets.size > 1 -> {
                recompute += pluginId
                null
            }
            else -> chooseStable(mappings)
        }
    }

    private fun chooseStable(mappings: List<ContentMapping>): ContentMapping? = mappings.maxWithOrNull(
        compareBy<ContentMapping> { it.updatedAt }
            .thenBy { it.policyVersion }
            .thenBy { it.origin.name }
            .thenBy { it.storyId.value },
    )

    private fun mergeRejections(
        survivorId: StoryId,
        rejections: List<ContentMappingRejection>,
    ): List<ContentMappingRejection> = rejections
        .groupBy { Triple(it.pluginId, it.sourceStoryId, it.policyVersion) }
        .entries
        .sortedWith(
            compareBy<Map.Entry<Triple<PluginId, String, Int>, List<ContentMappingRejection>>> { it.key.first.value }
                .thenBy { it.key.second }
                .thenBy { it.key.third },
        )
        .map { (_, rows) ->
            rows.maxWithOrNull(
                compareBy<ContentMappingRejection> { it.rejectedAt }
                    .thenBy { it.storyId.value },
            )!!.copy(storyId = survivorId)
        }
}
