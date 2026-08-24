package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogIngestReconciliationIndexTest {
    private val engine = CatalogReconciliationEngine(ReconciliationPolicy())
    private val factory = CatalogStoryIdFactory()

    @Test
    fun existingSourceKeyUsesDirectOwnerBeforeSemanticMatching() {
        val existing = evidence("source", "story:a", "Old")
        val index = CatalogIngestReconciliationIndex(engine, factory, listOf(existing))

        val resolved = assertIs<IncomingSourceResolution.Existing>(index.resolve(evidence("source", null, "Different")))

        assertEquals(IncomingSourceAction.DIRECT_OWNER, resolved.action)
        assertEquals(StoryId("story:a"), resolved.storyId)
    }

    @Test
    fun eligibleSameWorkAutoLinksWithoutTemporaryStory() {
        val identifier = ExternalIdentifier("mu", "work", ExternalIdentifierScope.WORK)
        val index = CatalogIngestReconciliationIndex(
            engine,
            factory,
            listOf(evidence("a", "story:a", "Alpha", identifiers = setOf(identifier))),
        )

        val resolved = assertIs<IncomingSourceResolution.Existing>(
            index.resolve(evidence("incoming", null, "Other", identifiers = setOf(identifier))),
        )

        assertEquals(IncomingSourceAction.AUTO_LINK, resolved.action)
        assertEquals(StoryId("story:a"), resolved.storyId)
    }

    @Test
    fun reviewCreatesSeparateStoryForNow() {
        val index = CatalogIngestReconciliationIndex(engine, factory, listOf(evidence("a", "story:a", "Exact")))

        val resolved = assertIs<IncomingSourceResolution.Create>(index.resolve(evidence("incoming", null, "Exact")))

        assertEquals(IncomingSourceAction.CREATE_FOR_REVIEW, resolved.action)
        assertEquals(StoryId("story:a"), resolved.reviewCandidateStoryId)
    }

    @Test
    fun noMatchCreatesSeparateStory() {
        val index = CatalogIngestReconciliationIndex(engine, factory, listOf(evidence("a", "story:a", "Alpha")))

        val resolved = assertIs<IncomingSourceResolution.Create>(index.resolve(evidence("incoming", null, "Zeta")))

        assertEquals(IncomingSourceAction.CREATE_SEPARATE, resolved.action)
        assertEquals(null, resolved.reviewCandidateStoryId)
    }

    @Test
    fun forkLocalResolutionIsVisibleToLaterItem() {
        val parent = CatalogIngestReconciliationIndex(engine, factory, emptyList())
        val fork = parent.fork()
        val first = assertIs<IncomingSourceResolution.Create>(
            fork.resolve(evidence("first", null, "Same", authors = setOf("writer"))),
        )
        val second = assertIs<IncomingSourceResolution.Existing>(
            fork.resolve(evidence("second", null, "Same", authors = setOf("writer"))),
        )

        assertEquals(IncomingSourceAction.AUTO_LINK, second.action)
        assertEquals(first.story.id, second.storyId)
    }

    @Test
    fun discardedForkDoesNotMutateParent() {
        val parent = CatalogIngestReconciliationIndex(engine, factory, emptyList())
        parent.fork().resolve(evidence("first", null, "Same", authors = setOf("writer")))

        val parentResolution = assertIs<IncomingSourceResolution.Create>(
            parent.resolve(evidence("second", null, "Same", authors = setOf("writer"))),
        )

        assertEquals(IncomingSourceAction.CREATE_SEPARATE, parentResolution.action)
    }

    private fun evidence(
        source: String,
        story: String?,
        title: String,
        authors: Set<String> = emptySet(),
        identifiers: Set<ExternalIdentifier> = emptySet(),
    ) = ReconciliationEvidence(
        sourceKey = SourceKey(PluginId("p"), source),
        currentStoryId = story?.let(::StoryId),
        contentType = ContentType.MANGA,
        comparisonTitles = setOf(title.lowercase()),
        comparisonAuthors = authors,
        identifiers = identifiers,
        lineageTokens = emptySet(),
        identityEvidenceFingerprint = "fp:$source:$story",
    )
}
