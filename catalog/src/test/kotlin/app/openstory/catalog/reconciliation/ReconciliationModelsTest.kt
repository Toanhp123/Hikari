package app.openstory.catalog.reconciliation

import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReconciliationModelsTest {
    @Test
    fun unorderedCaseKeyCanonicalizesStoryOrder() {
        val left = StoryId("story-b")
        val right = StoryId("story-a")

        assertEquals(
            ReconciliationCaseKey(StoryId("story-a"), StoryId("story-b")),
            ReconciliationCaseKey.of(left, right),
        )
    }

    @Test
    fun selfPairCannotBecomeReviewCase() {
        val storyId = StoryId("story-a")
        assertFailsWith<IllegalArgumentException> { ReconciliationCaseKey.of(storyId, storyId) }
    }

    @Test
    fun policyKeepsExistingMatcherThresholds() {
        val policy = ReconciliationPolicy()

        assertEquals(0.92, policy.autoTitleSimilarityAt)
        assertEquals(0.75, policy.reviewTitleSimilarityAt)
        assertEquals(0.50, policy.autoAuthorSimilarityAt)
        assertEquals(0.05, policy.minimumWinningLead)
    }
}
