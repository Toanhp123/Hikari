package app.openstory.library.matching

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentStoryMatcherTest {
    private val matcher = ContentStoryMatcher()

    @Test
    fun trustedDirectEvidenceAutoLinksWhenContentTypeIsCompatible() {
        val direct = DirectContentStoryIdentity(PluginId("content.a"), "story-42")
        val result = matcher.compare(
            features("Different catalog title", type = ContentType.WEB_NOVEL, direct = setOf(direct)),
            features("Source title", type = ContentType.WEB_NOVEL, direct = setOf(direct)),
        )

        assertEquals(ContentMatchDecision.AUTO_LINK, result.decision)
        assertEquals(1.0, result.score)
        assertTrue(result.explanation.directEvidence)
        assertEquals(1, result.policyVersion)
    }

    @Test
    fun directEvidenceStillRejectsConflictingContentType() {
        val direct = DirectContentStoryIdentity(PluginId("content.a"), "story-42")
        val result = matcher.compare(
            features("Fixture", type = ContentType.WEB_NOVEL, direct = setOf(direct)),
            features("Fixture", type = ContentType.MANGA, direct = setOf(direct)),
        )

        assertEquals(ContentMatchDecision.REJECT, result.decision)
        assertTrue(result.explanation.contentTypeConflict)
    }

    @Test
    fun exactTitleWithConflictingAuthorsRequiresReview() {
        val result = matcher.compare(
            features("The Return", authors = setOf("Author A")),
            features("The Return", authors = setOf("Author B")),
        )

        assertEquals(ContentMatchDecision.REVIEW, result.decision)
        assertTrue(result.explanation.authorConflict)
    }

    @Test
    fun missingOptionalEvidenceDoesNotReduceExactTitleScore() {
        val result = matcher.compare(
            features("The Return"),
            features("The Return"),
        )

        assertEquals(1.0, result.score)
        assertEquals(ContentMatchDecision.AUTO_LINK, result.decision)
        assertEquals(1.0, result.explanation.titleSimilarity)
        assertEquals(null, result.explanation.authorSimilarity)
        assertEquals(null, result.explanation.contentTypeMatch)
    }

    @Test
    fun policyThresholdsSeparateReviewFromReject() {
        val policy = ContentMatchPolicy(autoLinkAt = 0.95, reviewAt = 0.50)
        val thresholdMatcher = ContentStoryMatcher(policy)
        val review = thresholdMatcher.compare(
            features("Alpha Beta Gamma"),
            features("Alpha Beta Delta"),
        )
        val reject = thresholdMatcher.compare(
            features("Alpha Beta Gamma"),
            features("Completely Different"),
        )

        assertEquals(ContentMatchDecision.REVIEW, review.decision)
        assertEquals(ContentMatchDecision.REJECT, reject.decision)
    }

    @Test
    fun highestAliasSimilarityDrivesTheResult() {
        val result = matcher.compare(
            ContentStoryFeatures(
                title = "Completely Different",
                aliases = linkedSetOf("Exact Match", "Noise"),
            ),
            ContentStoryFeatures(
                title = "Other",
                aliases = linkedSetOf("Noise Two", "Exact Match"),
            ),
        )

        assertEquals(1.0, result.score)
        assertEquals(1.0, result.explanation.titleSimilarity)
        assertEquals(ContentMatchDecision.AUTO_LINK, result.decision)
    }

    @Test
    fun explanationIsDeterministicAcrossAliasSetOrder() {
        val leftA = ContentStoryFeatures("Primary", aliases = linkedSetOf("Beta", "Alpha"), authors = setOf("Writer"))
        val leftB = ContentStoryFeatures("Primary", aliases = linkedSetOf("Alpha", "Beta"), authors = setOf("Writer"))
        val candidate = ContentStoryFeatures("Alpha", aliases = setOf("Primary"), authors = setOf("Writer"))

        assertEquals(matcher.compare(leftA, candidate), matcher.compare(leftB, candidate))
    }

    private fun features(
        title: String,
        authors: Set<String> = emptySet(),
        type: ContentType? = null,
        direct: Set<DirectContentStoryIdentity> = emptySet(),
    ) = ContentStoryFeatures(
        title = title,
        authors = authors,
        contentType = type,
        directMappings = direct,
    )
}
