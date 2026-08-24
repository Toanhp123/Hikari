package app.openstory.catalog.matching

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryMatcherTest {
    private val matcher = StoryMatcher()
    @Test
    fun sameSourceIdentityKeepsExistingStory() {
        val existing = candidate("story-existing", "Same", setOf(SourceKey(PluginId("p"), "s")))
        val incoming = candidate("incoming", "Different", setOf(SourceKey(PluginId("p"), "s")))
        assertEquals(StoryResolution.Existing(StoryId("story-existing")), matcher.resolve(incoming, listOf(existing)))
    }
    @Test
    fun conflictingAuthorsDoNotAutoMerge() {
        val result = matcher.compare(
            candidate("incoming", "Reborn", emptySet(), setOf("A")),
            candidate("existing", "Reborn", emptySet(), setOf("B")),
        )
        assertEquals(MergeDecision.REVIEW, result.decision)
    }
    @Test
    fun resolutionIgnoresCandidateOrder() {
        val incoming = candidate("incoming", "Reborn", emptySet(), setOf("Incoming"))
        val a = candidate("story:a", "Reborn", emptySet(), setOf("A"))
        val b = candidate("story:b", "Reborn", emptySet(), setOf("B"))
        assertEquals(matcher.resolve(incoming, listOf(a, b)), matcher.resolve(incoming, listOf(b, a)))
    }

    @Test
    fun compareUsesHighestTitleSimilarityAcrossAliases() {
        val incoming = CatalogMatchCandidate(
            Story(StoryId("incoming"), ContentType.MANGA),
            titles = linkedSetOf("Completely Different", "Exact Match"),
            authors = setOf("Author"),
            sourceKeys = emptySet(),
        )
        val existing = CatalogMatchCandidate(
            Story(StoryId("existing"), ContentType.MANGA),
            titles = linkedSetOf("Noise", "Exact Match"),
            authors = setOf("Author"),
            sourceKeys = emptySet(),
        )

        val result = matcher.compare(incoming, existing)

        assertEquals(1.0, result.score)
        assertEquals(MergeDecision.AUTO_LINK, result.decision)
    }

    @Test
    fun legacyMatcherCarriesButDoesNotInterpretExternalIdentifiers() {
        val identifier = ExternalIdentifier(
            namespace = "work",
            value = "shared",
            scope = ExternalIdentifierScope.WORK,
        )
        val incoming = candidate("incoming", "Same", emptySet(), setOf("Author")).copy(
            externalIdentifiers = setOf(identifier),
        )
        val existing = candidate("existing", "Same", emptySet(), setOf("Author")).copy(
            externalIdentifiers = emptySet(),
        )

        assertEquals(
            matcher.compare(
                incoming.copy(externalIdentifiers = emptySet()),
                existing,
            ),
            matcher.compare(incoming, existing),
        )
    }

    @Test
    fun missingCandidateTitlesRemainSeparateInsteadOfCrashing() {
        val incoming = candidate("incoming", "Title", emptySet(), setOf("Author"))
        val candidate = CatalogMatchCandidate(
            Story(StoryId("existing"), ContentType.MANGA),
            emptySet(),
            setOf("Author"),
            emptySet(),
        )

        assertEquals(MergeDecision.SEPARATE, matcher.compare(incoming, candidate).decision)
    }
    private fun candidate(
        id: String,
        title: String,
        keys: Set<SourceKey>,
        authors: Set<String> = emptySet(),
    ) = CatalogMatchCandidate(
        Story(StoryId(id), ContentType.MANGA),
        setOf(title),
        authors,
        keys,
    )
}
