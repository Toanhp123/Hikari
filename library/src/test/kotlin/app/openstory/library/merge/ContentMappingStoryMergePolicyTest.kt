package app.openstory.library.merge

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentMappingStoryMergePolicyTest {
    private val policy = ContentMappingStoryMergePolicy()
    private val survivor = StoryId("story:survivor")
    private val plugin = PluginId("content.plugin")

    @Test
    fun sameTargetAutomatedAndProtectedCoalesceToProtected() {
        val automated = mapping("story:a", "same", ContentMappingOrigin.AUTOMATED, 10)
        val protected = mapping("story:b", "same", ContentMappingOrigin.USER_APPROVED, 20)

        val result = assertIs<ContentMappingMergeDecision.Ready>(
            policy.plan(survivor, listOf(automated), listOf(protected), emptyList(), emptyList()),
        )

        assertEquals(
            listOf(protected.copy(storyId = survivor)),
            result.plan.mappings,
        )
        assertTrue(result.plan.pluginsToRecompute.isEmpty())
    }

    @Test
    fun protectedDifferentTargetWinsOverAutomated() {
        val protected = mapping("story:a", "protected", ContentMappingOrigin.USER_URL, 10)
        val automated = mapping("story:b", "derived", ContentMappingOrigin.AUTOMATED, 50)

        val result = assertIs<ContentMappingMergeDecision.Ready>(
            policy.plan(survivor, listOf(protected), listOf(automated), emptyList(), emptyList()),
        )

        assertEquals("protected", result.plan.mappings.single().sourceStoryId)
        assertTrue(result.plan.pluginsToRecompute.isEmpty())
    }

    @Test
    fun conflictingAutomatedTargetsAreDiscardedAndRecomputed() {
        val result = assertIs<ContentMappingMergeDecision.Ready>(
            policy.plan(
                survivor,
                listOf(mapping("story:a", "x", ContentMappingOrigin.AUTOMATED, 10)),
                listOf(mapping("story:b", "y", ContentMappingOrigin.AUTOMATED, 20)),
                emptyList(),
                emptyList(),
            ),
        )

        assertTrue(result.plan.mappings.isEmpty())
        assertEquals(setOf(plugin), result.plan.pluginsToRecompute)
    }

    @Test
    fun conflictingProtectedTargetsRequireTypedReview() {
        val result = assertIs<ContentMappingMergeDecision.RequiresReview>(
            policy.plan(
                survivor,
                listOf(mapping("story:a", "x", ContentMappingOrigin.USER_APPROVED, 10)),
                listOf(mapping("story:b", "y", ContentMappingOrigin.USER_URL, 20)),
                emptyList(),
                emptyList(),
            ),
        )

        assertEquals(setOf(CONTENT_MAPPING_PROTECTED_CONFLICT), result.reasons)
        assertEquals(setOf("x", "y"), result.protectedConflicts.single().candidateSourceStoryIds)
    }

    @Test
    fun explicitProtectedResolutionMustSelectOneConflictCandidate() {
        val left = mapping("story:a", "x", ContentMappingOrigin.USER_APPROVED, 10)
        val right = mapping("story:b", "y", ContentMappingOrigin.USER_URL, 20)
        val ready = assertIs<ContentMappingMergeDecision.Ready>(
            policy.plan(
                survivor,
                listOf(left),
                listOf(right),
                emptyList(),
                emptyList(),
                listOf(ContentMappingMergeResolution(plugin, "x")),
            ),
        )
        val invalid = assertIs<ContentMappingMergeDecision.RequiresReview>(
            policy.plan(
                survivor,
                listOf(left),
                listOf(right),
                emptyList(),
                emptyList(),
                listOf(ContentMappingMergeResolution(plugin, "z")),
            ),
        )

        assertEquals("x", ready.plan.mappings.single().sourceStoryId)
        assertEquals(setOf(CONTENT_MAPPING_RESOLUTION_INVALID), invalid.reasons)
    }

    @Test
    fun reversalRequiresReviewWhenHistoricalProtectedConflictNeededAnUnrecordedResolution() {
        val left = mapping("story:a", "x", ContentMappingOrigin.USER_APPROVED, 10)
        val right = mapping("story:b", "y", ContentMappingOrigin.USER_URL, 20)
        val current = listOf(left.copy(storyId = survivor))

        val blockers = policy.reversalBlockers(
            survivorId = survivor,
            currentMappings = current,
            currentRejections = emptyList(),
            survivorBeforeMappings = listOf(left),
            retiredBeforeMappings = listOf(right),
            survivorBeforeRejections = emptyList(),
            retiredBeforeRejections = emptyList(),
        )

        assertEquals(setOf(CONTENT_MAPPING_REVERSAL_STATE_CHANGED), blockers)
    }

    @Test
    fun duplicateResolutionForOnePluginIsRejected() {
        val result = assertIs<ContentMappingMergeDecision.RequiresReview>(
            policy.plan(
                survivor,
                listOf(mapping("story:a", "x", ContentMappingOrigin.USER_APPROVED, 10)),
                listOf(mapping("story:b", "y", ContentMappingOrigin.USER_URL, 20)),
                emptyList(),
                emptyList(),
                listOf(
                    ContentMappingMergeResolution(plugin, "x"),
                    ContentMappingMergeResolution(plugin, "x"),
                ),
            ),
        )

        assertEquals(setOf(CONTENT_MAPPING_RESOLUTION_INVALID), result.reasons)
    }

    @Test
    fun rejectionsUnionAndCoalesceUsingLatestTimestamp() {
        val first = rejection("story:a", "reject", 1, 10)
        val newer = rejection("story:b", "reject", 1, 50)
        val other = rejection("story:b", "other", 2, 20)

        val result = assertIs<ContentMappingMergeDecision.Ready>(
            policy.plan(survivor, emptyList(), emptyList(), listOf(first), listOf(newer, other)),
        )

        assertEquals(
            listOf(
                newer.copy(storyId = survivor),
                other.copy(storyId = survivor),
            ).sortedBy { "${it.pluginId.value}:${it.sourceStoryId}:${it.policyVersion}" },
            result.plan.rejections,
        )
    }

    @Test
    fun argumentOrderProducesIdenticalPlan() {
        val left = listOf(mapping("story:a", "same", ContentMappingOrigin.USER_APPROVED, 10))
        val right = listOf(mapping("story:b", "same", ContentMappingOrigin.USER_URL, 10))

        val first = policy.plan(survivor, left, right, emptyList(), emptyList())
        val second = policy.plan(survivor, right, left, emptyList(), emptyList())

        assertEquals(first, second)
    }

    private fun mapping(story: String, source: String, origin: ContentMappingOrigin, updated: Long) =
        ContentMapping(StoryId(story), plugin, source, origin, 1, updated)

    private fun rejection(story: String, source: String, version: Int, rejected: Long) =
        ContentMappingRejection(StoryId(story), plugin, source, version, rejected)
}
