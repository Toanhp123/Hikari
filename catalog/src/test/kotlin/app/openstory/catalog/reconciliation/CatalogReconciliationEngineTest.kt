package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogReconciliationEngineTest {
    private val engine = CatalogReconciliationEngine(ReconciliationPolicy())

    @Test
    fun titleOnlyExactMatchNeverAutoMerges() {
        val result = engine.assessPair(evidence("a", "story:a", "Exact"), evidence("b", "story:b", "Exact"))

        assertEquals(ReconciliationSemanticDecision.REVIEW, result.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.MERGEABLE, result.mergeEligibility)
        assertTrue(ReconciliationReasonCode.TITLE_ONLY_NOT_AUTO in result.reasons)
    }

    @Test
    fun compatibleWorkIdentifierCanAutoWithoutAuthor() {
        val identifier = workId("mu", "work-1")
        val result = engine.assessPair(
            evidence("a", "story:a", "Alpha", identifiers = setOf(identifier)),
            evidence("b", "story:b", "Other", identifiers = setOf(identifier)),
        )

        assertEquals(ReconciliationSemanticDecision.SAME_WORK, result.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.MERGEABLE, result.mergeEligibility)
        assertEquals(setOf(identifier), result.matchedIdentifiers)
    }

    @Test
    fun incompatibleContentTypeBlocksStrongIdentifier() {
        val identifier = workId("mu", "work-1")
        val result = engine.assessPair(
            evidence("a", "story:a", "Alpha", ContentType.MANGA, identifiers = setOf(identifier)),
            evidence("b", "story:b", "Alpha", ContentType.ANIME, identifiers = setOf(identifier)),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, result.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.INVARIANT_BLOCKED, result.mergeEligibility)
        assertTrue(ReconciliationReasonCode.CONTENT_TYPE_CONFLICT in result.reasons)
    }

    @Test
    fun contradictoryWorkIdentifiersCannotBeOutvotedByTitleAndAuthor() {
        val result = engine.assessPair(
            evidence("a", "story:a", "Exact", authors = setOf("same"), identifiers = setOf(workId("mu", "one"))),
            evidence("b", "story:b", "Exact", authors = setOf("same"), identifiers = setOf(workId("mu", "two"))),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, result.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.INVARIANT_BLOCKED, result.mergeEligibility)
        assertTrue(ReconciliationReasonCode.WORK_IDENTIFIER_CONFLICT in result.reasons)
    }

    @Test
    fun clearDifferentContentTypeWithoutPositiveIdentityEvidenceSeparates() {
        val result = engine.assessPair(
            evidence("a", "story:a", "Alpha", ContentType.MANGA),
            evidence("b", "story:b", "Alpha", ContentType.ANIME),
        )

        assertEquals(ReconciliationSemanticDecision.DIFFERENT_WORK, result.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.INVARIANT_BLOCKED, result.mergeEligibility)
    }

    @Test
    fun legacyTitleAuthorThresholdCanAuto() {
        val result = engine.assessPair(
            evidence("a", "story:a", "Reborn Hero", authors = setOf("writer")),
            evidence("b", "story:b", "Reborn Hero", authors = setOf("writer")),
        )

        assertEquals(ReconciliationSemanticDecision.SAME_WORK, result.semanticDecision)
        assertEquals(1.0, result.confidence)
    }

    @Test
    fun pairAssessmentIsSymmetricAndProviderOrderIndependent() {
        val a = evidence("a", "story:a", "Reborn Hero", authors = setOf("writer"), plugin = "provider-z")
        val b = evidence("b", "story:b", "Reborn Heroes", authors = setOf("writer"), plugin = "provider-a")

        val ab = engine.assessPair(a, b)
        val ba = engine.assessPair(b, a)

        assertEquals(ab.semanticDecision, ba.semanticDecision)
        assertEquals(ab.mergeEligibility, ba.mergeEligibility)
        assertEquals(ab.confidence, ba.confidence)
        assertEquals(ab.reasons, ba.reasons)
        assertEquals(ab.identityEvidenceFingerprint, ba.identityEvidenceFingerprint)
    }

    @Test
    fun smallWinningLeadDowngradesSameWorkToReview() {
        val incoming = evidence("incoming", null, "Alpha", authors = setOf("writer"))
        val a = evidence("a", "story:a", "Alpha", authors = setOf("writer"))
        val b = evidence("b", "story:b", "Alpha", authors = setOf("writer"))

        val selection = engine.rankCandidates(incoming, listOf(a, b))

        assertEquals(ReconciliationSemanticDecision.REVIEW, selection.semanticDecision)
        assertEquals(0.0, selection.winningLead)
        assertTrue(ReconciliationReasonCode.WINNING_LEAD_TOO_SMALL in selection.reasons)
    }

    @Test
    fun clearWinningLeadPreservesAutoPath() {
        val incoming = evidence("incoming", null, "Exact Title", authors = setOf("writer"))
        val a = evidence("a", "story:a", "Exact Title", authors = setOf("writer"))
        val b = evidence("b", "story:b", "Different Enough", authors = setOf("other"))

        val selection = engine.rankCandidates(incoming, listOf(a, b))

        assertEquals(ReconciliationSemanticDecision.SAME_WORK, selection.semanticDecision)
        assertEquals(StoryId("story:a"), selection.ranked.first().storyId)
    }

    @Test
    fun multiSourceCandidateIsCollapsedBeforeWinningLead() {
        val incoming = evidence("incoming", null, "Exact", authors = setOf("writer"))
        val a1 = evidence("a1", "story:a", "Exact", authors = setOf("writer"))
        val a2 = evidence("a2", "story:a", "Exact", authors = setOf("writer"))
        val b = evidence("b", "story:b", "Unrelated", authors = setOf("other"))

        val selection = engine.rankCandidates(incoming, listOf(a1, a2, b))

        assertEquals(1, selection.ranked.count { it.storyId == StoryId("story:a") })
        assertEquals(ReconciliationSemanticDecision.SAME_WORK, selection.semanticDecision)
    }

    private fun evidence(
        source: String,
        story: String?,
        title: String,
        contentType: ContentType = ContentType.MANGA,
        authors: Set<String> = emptySet(),
        identifiers: Set<ExternalIdentifier> = emptySet(),
        plugin: String = "p",
    ) = ReconciliationEvidence(
        sourceKey = SourceKey(PluginId(plugin), source),
        currentStoryId = story?.let(::StoryId),
        contentType = contentType,
        comparisonTitles = setOf(title.lowercase()),
        comparisonAuthors = authors,
        identifiers = identifiers,
        lineageTokens = emptySet(),
        identityEvidenceFingerprint = "fp:$plugin:$source:$story",
    )

    private fun workId(namespace: String, value: String) = ExternalIdentifier(namespace, value, ExternalIdentifierScope.WORK)
}
