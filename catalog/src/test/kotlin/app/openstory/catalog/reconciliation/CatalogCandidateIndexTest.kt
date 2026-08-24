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

class CatalogCandidateIndexTest {
    private val index = InMemoryCatalogCandidateIndex()

    @Test
    fun sameWorkIdentifierIsCandidate() {
        val shared = workId("isbn", "same")
        index.rebuild(listOf(evidence("a", "story:a", titles = setOf("Alpha"), identifiers = setOf(shared))))

        assertEquals(listOf(StoryId("story:a")), index.candidatesFor(evidence("incoming", null, setOf("Other"), identifiers = setOf(shared))))
    }

    @Test
    fun titleAliasTokenOverlapIsCandidate() {
        index.rebuild(listOf(evidence("a", "story:a", titles = setOf("Reborn Hero"))))

        assertEquals(
            listOf(StoryId("story:a")),
            index.candidatesFor(evidence("incoming", null, titles = setOf("Hero Returns"))),
        )
    }

    @Test
    fun authorOnlyOverlapMayShortlist() {
        index.rebuild(listOf(evidence("a", "story:a", titles = setOf("One"), authors = setOf("writer"))))

        assertEquals(
            listOf(StoryId("story:a")),
            index.candidatesFor(evidence("incoming", null, titles = setOf("Unrelated"), authors = setOf("writer"))),
        )
    }

    @Test
    fun unrelatedSourceIsAbsent() {
        index.rebuild(listOf(evidence("a", "story:a", titles = setOf("One"), authors = setOf("writer"))))

        assertTrue(index.candidatesFor(evidence("incoming", null, titles = setOf("Two"), authors = setOf("other"))).isEmpty())
    }

    @Test
    fun upsertReplacesOldTokensForSameSourceKey() {
        val oldIdentifier = workId("work", "old")
        val newIdentifier = workId("work", "new")
        index.rebuild(
            listOf(
                evidence(
                    "a",
                    "story:a",
                    titles = setOf("Old Token"),
                    authors = setOf("old author"),
                    identifiers = setOf(oldIdentifier),
                ),
            ),
        )
        index.upsert(
            evidence(
                "a",
                "story:a",
                titles = setOf("New Token"),
                authors = setOf("new author"),
                identifiers = setOf(newIdentifier),
            ),
        )

        assertTrue(index.candidatesFor(evidence("incoming1", null, titles = setOf("Old"))).isEmpty())
        assertTrue(
            index.candidatesFor(
                evidence("incoming2", null, titles = setOf("Other"), authors = setOf("old author")),
            ).isEmpty(),
        )
        assertTrue(
            index.candidatesFor(
                evidence("incoming3", null, titles = setOf("Other"), identifiers = setOf(oldIdentifier)),
            ).isEmpty(),
        )
        assertEquals(
            listOf(StoryId("story:a")),
            index.candidatesFor(evidence("incoming4", null, titles = setOf("New"))),
        )
    }

    @Test
    fun evidenceLookupReturnsOnlyRequestedStoriesInStableSourceOrder() {
        val a2 = evidence("a2", "story:a", titles = setOf("Shared"))
        val unrelated = evidence("unrelated", "story:z", titles = setOf("Other"))
        val a1 = evidence("a1", "story:a", titles = setOf("Shared"))
        index.rebuild(listOf(a2, unrelated, a1))

        assertEquals(listOf(a1, a2), index.evidenceFor(setOf(StoryId("story:a"))))
    }

    @Test
    fun removeDeletesOnlyThatSourceContribution() {
        index.rebuild(
            listOf(
                evidence("a", "story:a", titles = setOf("Shared Alpha")),
                evidence("b", "story:b", titles = setOf("Shared Beta")),
            ),
        )
        index.remove(SourceKey(PluginId("p"), "a"))

        assertEquals(
            listOf(StoryId("story:b")),
            index.candidatesFor(evidence("incoming", null, titles = setOf("Shared"))),
        )
    }

    @Test
    fun equalRetrievalStrengthOrdersByStoryId() {
        index.rebuild(
            listOf(
                evidence("b", "story:b", titles = setOf("Shared")),
                evidence("a", "story:a", titles = setOf("Shared")),
            ),
        )

        assertEquals(
            listOf(StoryId("story:a"), StoryId("story:b")),
            index.candidatesFor(evidence("incoming", null, titles = setOf("Shared"))),
        )
    }

    private fun evidence(
        source: String,
        story: String?,
        titles: Set<String>,
        authors: Set<String> = emptySet(),
        identifiers: Set<ExternalIdentifier> = emptySet(),
    ) = ReconciliationEvidence(
        sourceKey = SourceKey(PluginId("p"), source),
        currentStoryId = story?.let(::StoryId),
        contentType = ContentType.MANGA,
        comparisonTitles = titles,
        comparisonAuthors = authors,
        identifiers = identifiers,
        lineageTokens = emptySet(),
        identityEvidenceFingerprint = "fp:$source:${titles.sorted()}:${authors.sorted()}:${identifiers.size}",
    )

    private fun workId(namespace: String, value: String) = ExternalIdentifier(namespace, value, ExternalIdentifierScope.WORK)
}
