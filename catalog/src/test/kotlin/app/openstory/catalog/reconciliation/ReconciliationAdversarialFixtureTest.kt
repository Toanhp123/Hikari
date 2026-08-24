package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.matching.TitleNormalizer
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconciliationAdversarialFixtureTest {
    private val engine = CatalogReconciliationEngine(ReconciliationPolicy())

    @Test
    fun sameTitleDifferentContentTypeIsInvariantBlockedDifferentWork() {
        val assessment = engine.assessPair(
            evidence("a", "story:a", ContentType.MANGA, titles = setOf("The Same Work")),
            evidence("b", "story:b", ContentType.LIGHT_NOVEL, titles = setOf("The Same Work")),
        )

        assertEquals(ReconciliationSemanticDecision.DIFFERENT_WORK, assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.INVARIANT_BLOCKED, assessment.mergeEligibility)
        assertEquals(1.0, assessment.titleSimilarity)
        assertTrue(ReconciliationReasonCode.CONTENT_TYPE_CONFLICT in assessment.reasons)
        assertTrue(ReconciliationReasonCode.TITLE_EXACT in assessment.reasons)
    }

    @Test
    fun sequelLikeNearIdenticalTitleDoesNotAutoWithoutAuthorEvidence() {
        val assessment = engine.assessPair(
            evidence("a", "story:a", titles = setOf("Chronicles Hero Return")),
            evidence("b", "story:b", titles = setOf("Chronicles Hero Return 2")),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.MERGEABLE, assessment.mergeEligibility)
        assertTrue(requireNotNull(assessment.titleSimilarity) >= ReconciliationPolicy().reviewTitleSimilarityAt)
        assertTrue(ReconciliationReasonCode.AUTHOR_MISSING in assessment.reasons)
        assertTrue(ReconciliationReasonCode.TITLE_ONLY_NOT_AUTO in assessment.reasons)
    }

    @Test
    fun alternateEditionWithSameWorkIdentifierAutoMatches() {
        val workId = workIdentifier("openlibrary.work", "OL123W")
        val assessment = engine.assessPair(
            evidence("a", "story:a", titles = setOf("Localized Title"), identifiers = setOf(workId)),
            evidence("b", "story:b", titles = setOf("Original Title"), identifiers = setOf(workId)),
        )

        assertEquals(ReconciliationSemanticDecision.SAME_WORK, assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.MERGEABLE, assessment.mergeEligibility)
        assertTrue(ReconciliationReasonCode.WORK_IDENTIFIER_MATCH in assessment.reasons)
    }

    @Test
    fun transliterationAliasOverlapCanReachReviewWithoutAuthorEvidence() {
        val assessment = engine.assessPair(
            evidence("a", "story:a", titles = setOf("Shingeki no Kyojin", "Attack on Titan")),
            evidence("b", "story:b", titles = setOf("Attack on Titan")),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, assessment.semanticDecision)
        assertEquals(1.0, assessment.titleSimilarity)
        assertTrue(ReconciliationReasonCode.TITLE_EXACT in assessment.reasons)
        assertTrue(ReconciliationReasonCode.TITLE_ONLY_NOT_AUTO in assessment.reasons)
    }

    @Test
    fun sameAuthorWithUnrelatedTitleDoesNotCreatePositiveIdentityDecision() {
        val assessment = engine.assessPair(
            evidence("a", "story:a", titles = setOf("Blue Moon"), authors = setOf("Same Writer")),
            evidence("b", "story:b", titles = setOf("Iron Kingdom"), authors = setOf("Same Writer")),
        )

        assertEquals(ReconciliationSemanticDecision.NO_MATCH, assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.MERGEABLE, assessment.mergeEligibility)
        assertTrue(ReconciliationReasonCode.AUTHOR_MATCH in assessment.reasons)
    }

    @Test
    fun exactTitleWithMissingAuthorsRemainsReview() {
        val assessment = engine.assessPair(
            evidence("a", "story:a", titles = setOf("Exact Title")),
            evidence("b", "story:b", titles = setOf("Exact Title")),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, assessment.semanticDecision)
        assertEquals(1.0, assessment.titleSimilarity)
        assertTrue(ReconciliationReasonCode.AUTHOR_MISSING in assessment.reasons)
        assertTrue(ReconciliationReasonCode.TITLE_ONLY_NOT_AUTO in assessment.reasons)
    }

    @Test
    fun sameCompatibleWorkIdentifierWithoutAuthorsAutoMatches() {
        val id = workIdentifier("mangaupdates.series", "123")
        val assessment = engine.assessPair(
            evidence("a", "story:a", titles = setOf("Title A"), identifiers = setOf(id)),
            evidence("b", "story:b", titles = setOf("Title B"), identifiers = setOf(id)),
        )

        assertEquals(ReconciliationSemanticDecision.SAME_WORK, assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.MERGEABLE, assessment.mergeEligibility)
        assertTrue(ReconciliationReasonCode.WORK_IDENTIFIER_MATCH in assessment.reasons)
    }

    @Test
    fun conflictingWorkIdentifiersCannotBeOutvotedByPerfectTitleAndAuthor() {
        val assessment = engine.assessPair(
            evidence(
                "a",
                "story:a",
                titles = setOf("Exact Title"),
                authors = setOf("Exact Author"),
                identifiers = setOf(workIdentifier("isbn.work", "A")),
            ),
            evidence(
                "b",
                "story:b",
                titles = setOf("Exact Title"),
                authors = setOf("Exact Author"),
                identifiers = setOf(workIdentifier("isbn.work", "B")),
            ),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.INVARIANT_BLOCKED, assessment.mergeEligibility)
        assertTrue(ReconciliationReasonCode.WORK_IDENTIFIER_CONFLICT in assessment.reasons)
        assertTrue(assessment.conflictingIdentifiers.size == 2)
    }

    @Test
    fun providerRecordSameValueInDifferentNamespacesAndScopesIsNotWorkIdentity() {
        val assessment = engine.assessPair(
            evidence(
                "a",
                "story:a",
                titles = setOf("Exact Title"),
                identifiers = setOf(identifier("provider.a", "42", ExternalIdentifierScope.PROVIDER_RECORD)),
            ),
            evidence(
                "b",
                "story:b",
                titles = setOf("Exact Title"),
                identifiers = setOf(identifier("provider.b", "42", ExternalIdentifierScope.PUBLICATION)),
            ),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, assessment.semanticDecision)
        assertTrue(assessment.matchedIdentifiers.isEmpty())
        assertTrue(assessment.conflictingIdentifiers.isEmpty())
        assertTrue(ReconciliationReasonCode.WORK_IDENTIFIER_MATCH !in assessment.reasons)
    }

    @Test
    fun twoCloseCandidatesInsideWinningLeadAreDowngradedToReview() {
        val incoming = evidence("incoming", null, titles = setOf("Exact"), authors = setOf("Writer"))
        val selection = engine.rankCandidates(
            incoming,
            listOf(
                evidence("a", "story:a", titles = setOf("Exact"), authors = setOf("Writer")),
                evidence("b", "story:b", titles = setOf("Exact"), authors = setOf("Writer")),
            ),
        )

        assertEquals(ReconciliationSemanticDecision.REVIEW, selection.semanticDecision)
        assertEquals(0.0, selection.winningLead)
        assertTrue(ReconciliationReasonCode.WINNING_LEAD_TOO_SMALL in selection.reasons)
        assertEquals(listOf(StoryId("story:a"), StoryId("story:b")), selection.ranked.map { it.storyId })
    }

    @Test
    fun providerOrderPermutationDoesNotChangeDecisionEligibilityLeadOrReasons() {
        val incoming = evidence("incoming", null, titles = setOf("Exact"), authors = setOf("Writer"))
        val candidates = listOf(
            evidence("z", "story:z", titles = setOf("Exact"), authors = setOf("Writer")),
            evidence("a", "story:a", titles = setOf("Exact"), authors = setOf("Writer")),
        )

        val forward = engine.rankCandidates(incoming, candidates)
        val reverse = engine.rankCandidates(incoming, candidates.reversed())

        assertEquals(forward.semanticDecision, reverse.semanticDecision)
        assertEquals(forward.mergeEligibility, reverse.mergeEligibility)
        assertEquals(forward.winningLead, reverse.winningLead)
        assertEquals(forward.reasons, reverse.reasons)
        assertEquals(forward.ranked.map { it.storyId }, reverse.ranked.map { it.storyId })
    }

    private fun evidence(
        sourceId: String,
        storyId: String?,
        contentType: ContentType = ContentType.MANGA,
        titles: Set<String>,
        authors: Set<String> = emptySet(),
        identifiers: Set<ExternalIdentifier> = emptySet(),
        lineage: Set<String> = emptySet(),
    ): ReconciliationEvidence = ReconciliationEvidence(
        sourceKey = SourceKey(PluginId("fixture"), sourceId),
        currentStoryId = storyId?.let(::StoryId),
        contentType = contentType,
        comparisonTitles = titles.mapTo(linkedSetOf(), TitleNormalizer::normalize),
        comparisonAuthors = authors.mapTo(linkedSetOf(), TitleNormalizer::normalize),
        identifiers = identifiers,
        lineageTokens = lineage.mapTo(linkedSetOf(), TitleNormalizer::normalize),
        identityEvidenceFingerprint = listOf(
            sourceId,
            storyId.orEmpty(),
            contentType.name,
            titles.sorted().joinToString("|"),
            authors.sorted().joinToString("|"),
            identifiers.sortedBy { "${it.namespace}:${it.scope}:${it.value}" }.joinToString("|"),
            lineage.sorted().joinToString("|"),
        ).joinToString("#"),
    )

    private fun workIdentifier(namespace: String, value: String): ExternalIdentifier =
        identifier(namespace, value, ExternalIdentifierScope.WORK)

    private fun identifier(
        namespace: String,
        value: String,
        scope: ExternalIdentifierScope,
    ): ExternalIdentifier = ExternalIdentifier(namespace, value, scope)
}
