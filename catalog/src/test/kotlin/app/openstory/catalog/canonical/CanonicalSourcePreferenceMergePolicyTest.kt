package app.openstory.catalog.canonical

import app.openstory.catalog.identity.SourceKey
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.common.merge.DomainMergeDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CanonicalSourcePreferenceMergePolicyTest {
    private val policy = CanonicalSourcePreferenceMergePolicy()
    private val survivor = StoryId("story:survivor")
    private val x = SourceKey(PluginId("p"), "x")
    private val y = SourceKey(PluginId("p"), "y")

    @Test
    fun autoPlusAutoRemainsAutoWithMonotonicRevision() {
        val result = assertIs<DomainMergeDecision.Ready<CanonicalSourcePreference>>(
            policy.plan(survivor, auto("story:a", 3), auto("story:b", 8)),
        )

        assertEquals(CanonicalSourcePreference(survivor, CanonicalSourcePreferenceMode.AUTO, null, 9), result.value)
    }

    @Test
    fun pinnedPlusAutoPreservesPin() {
        val result = assertIs<DomainMergeDecision.Ready<CanonicalSourcePreference>>(
            policy.plan(survivor, pinned("story:a", x, 2), auto("story:b", 4)),
        )

        assertEquals(x, result.value.pinnedSource)
        assertEquals(5, result.value.revision)
    }

    @Test
    fun equalPinsCoalesce() {
        val result = assertIs<DomainMergeDecision.Ready<CanonicalSourcePreference>>(
            policy.plan(survivor, pinned("story:a", x, 2), pinned("story:b", x, 4)),
        )

        assertEquals(x, result.value.pinnedSource)
    }

    @Test
    fun conflictingPinsRequireReviewIndependentOfArgumentOrder() {
        val first = policy.plan(survivor, pinned("story:a", x, 2), pinned("story:b", y, 4))
        val second = policy.plan(survivor, pinned("story:b", y, 4), pinned("story:a", x, 2))

        assertEquals(first, second)
        assertEquals(
            setOf(CANONICAL_SOURCE_PREFERENCE_PIN_CONFLICT),
            assertIs<DomainMergeDecision.RequiresReview>(first).reasons,
        )
    }

    private fun auto(story: String, revision: Long) = CanonicalSourcePreference(
        StoryId(story), CanonicalSourcePreferenceMode.AUTO, null, revision,
    )

    private fun pinned(story: String, source: SourceKey, revision: Long) = CanonicalSourcePreference(
        StoryId(story), CanonicalSourcePreferenceMode.PINNED, source, revision,
    )
}
