package app.openstory.matching

import app.openstory.model.CatalogCanonicalResolution
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.CanonicalStory
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogStoryResolverTest {
    @Test
    fun sameTitleDifferentAuthorDoesNotAutoMerge() {
        val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
        val result = resolver.compare(
            catalogCandidate(title = "Reborn", authors = listOf("Author A")),
            canonicalCandidate(title = "Reborn", authors = setOf("Author B")),
        )

        assertEquals(MergeDecision.REVIEW, result.decision)
        assertTrue(result.explanation.authorConflict)
    }

    @Test
    fun normalizedTitleAndCompatibleAuthorResolveExistingStory() {
        val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
        val source = catalogCandidate(
            sourceId = "source-1",
            title = "Reborn: A Novel",
            authors = listOf("A. Writer"),
        )
        val candidate = canonicalCandidate(
            storyId = "story-existing",
            title = "REBORN — A NOVEL",
            authors = setOf("A. Writer"),
        )

        val resolution = resolver.resolve(
            pluginId = PluginId("catalog.new"),
            source = source,
            candidates = listOf(candidate),
        )

        assertEquals(
            CatalogCanonicalResolution.Existing(StoryId("story-existing")),
            resolution,
        )
    }

    @Test
    fun ambiguousResolutionCreatesSameSourceIsolatedIdRegardlessOfCandidateOrder() {
        val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
        val source = catalogCandidate(
            sourceId = "source-ambiguous",
            title = "Reborn",
            authors = listOf("Incoming Author"),
        )
        val candidates = listOf(
            canonicalCandidate("story-b", "Reborn", setOf("Author B")),
            canonicalCandidate("story-a", "Reborn", setOf("Author A")),
        )

        val first = resolver.resolve(PluginId("catalog.new"), source, candidates)
        val second = resolver.resolve(PluginId("catalog.new"), source, candidates.reversed())

        val expected = CatalogCanonicalResolution.Create(
            StoryId("catalog:catalog.new:source-ambiguous"),
        )
        assertEquals(expected, first)
        assertEquals(expected, second)
    }

    @Test
    fun existingSourceIsolatedCanonicalIdentityIsReused() {
        val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
        val source = catalogCandidate(
            sourceId = "source-orphan",
            title = "New metadata title",
            authors = listOf("New Author"),
        )
        val existing = canonicalCandidate(
            storyId = "catalog:catalog.new:source-orphan",
            title = "Old metadata title",
            authors = setOf("Old Author"),
        )

        val resolution = resolver.resolve(
            pluginId = PluginId("catalog.new"),
            source = source,
            candidates = listOf(existing),
        )

        assertEquals(
            CatalogCanonicalResolution.Existing(existing.id),
            resolution,
        )
    }

    @Test
    fun equallyStrongAutoLinkCandidatesRemainSeparate() {
        val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
        val source = catalogCandidate(
            sourceId = "source-tie",
            title = "Reborn",
            authors = listOf("Shared Author"),
        )
        val candidates = listOf(
            canonicalCandidate("story-b", "Reborn", setOf("Shared Author")),
            canonicalCandidate("story-a", "Reborn", setOf("Shared Author")),
        )

        val resolution = resolver.resolve(PluginId("catalog.new"), source, candidates)

        assertEquals(
            CatalogCanonicalResolution.Create(StoryId("catalog:catalog.new:source-tie")),
            resolution,
        )
    }

    @Test
    fun trustedDirectMappingOutranksOrdinaryHighConfidenceMatch() {
        val sourceIdentity = CatalogSourceIdentity(PluginId("catalog.new"), "source-priority")
        val targetIdentity = CatalogSourceIdentity(PluginId("catalog.trusted"), "trusted-priority")
        val resolver = CatalogStoryResolver(
            defaultCatalogMatchPolicy(
                trustedDirectMappings = setOf(TrustedCatalogMapping(sourceIdentity, targetIdentity)),
            ),
        )
        val directCandidate = canonicalCandidate(
            storyId = "story-direct",
            title = "Unrelated title",
            authors = setOf("Other Author"),
            pluginId = "catalog.trusted",
            externalStoryId = "trusted-priority",
        )
        val ordinaryCandidate = canonicalCandidate(
            storyId = "story-title-author",
            title = "Reborn",
            authors = setOf("Shared Author"),
        )

        val resolution = resolver.resolve(
            pluginId = sourceIdentity.pluginId,
            source = catalogCandidate(
                sourceId = sourceIdentity.sourceId,
                title = "Reborn",
                authors = listOf("Shared Author"),
            ),
            candidates = listOf(ordinaryCandidate, directCandidate),
        )

        assertEquals(
            CatalogCanonicalResolution.Existing(StoryId("story-direct")),
            resolution,
        )
    }

    @Test
    fun trustedPolicyRejectsOneSourceMappedToMultipleTargets() {
        val sourceIdentity = CatalogSourceIdentity(PluginId("catalog.new"), "source-1")

        assertFailsWith<IllegalArgumentException> {
            defaultCatalogMatchPolicy(
                trustedDirectMappings = setOf(
                    TrustedCatalogMapping(
                        sourceIdentity,
                        CatalogSourceIdentity(PluginId("catalog.a"), "a-1"),
                    ),
                    TrustedCatalogMapping(
                        sourceIdentity,
                        CatalogSourceIdentity(PluginId("catalog.b"), "b-1"),
                    ),
                ),
            )
        }
    }

    @Test
    fun missingAuthorsAreNeutralButDoNotCreateUnsafeAutoLink() {
        val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
        val result = resolver.compare(
            catalogCandidate(title = "The Archive", authors = emptyList()),
            canonicalCandidate(title = "The Archive", authors = emptySet()),
        )

        assertEquals(MergeDecision.REVIEW, result.decision)
        assertFalse(result.explanation.authorConflict)
        assertEquals(null, result.explanation.authorSimilarity)
    }

    @Test
    fun trustedDirectMappingAutoLinksOnlyWhenContentTypeMatches() {
        val sourceIdentity = CatalogSourceIdentity(PluginId("catalog.new"), "source-1")
        val targetIdentity = CatalogSourceIdentity(PluginId("catalog.trusted"), "trusted-7")
        val policy = defaultCatalogMatchPolicy(
            trustedDirectMappings = setOf(
                TrustedCatalogMapping(sourceIdentity, targetIdentity),
            ),
        )
        val resolver = CatalogStoryResolver(policy)
        val candidate = canonicalCandidate(
            storyId = "story-trusted",
            title = "Different Title",
            authors = setOf("Different Author"),
            pluginId = "catalog.trusted",
            externalStoryId = "trusted-7",
        )

        val matchingType = resolver.resolve(
            sourceIdentity.pluginId,
            catalogCandidate(sourceId = sourceIdentity.sourceId, contentType = ContentType.WEB_NOVEL),
            listOf(candidate),
        )
        val conflictingType = resolver.resolve(
            sourceIdentity.pluginId,
            catalogCandidate(
                sourceId = sourceIdentity.sourceId,
                contentType = ContentType.LIGHT_NOVEL,
            ),
            listOf(candidate),
        )

        assertEquals(
            CatalogCanonicalResolution.Existing(StoryId("story-trusted")),
            matchingType,
        )
        assertEquals(
            CatalogCanonicalResolution.Create(StoryId("catalog:catalog.new:source-1")),
            conflictingType,
        )
    }

    private fun catalogCandidate(
        sourceId: String = "source",
        title: String = "Reborn",
        authors: List<String> = listOf("Author A"),
        contentType: ContentType = ContentType.WEB_NOVEL,
    ) = CatalogSnapshotItem(
        sourceId = sourceId,
        title = title,
        contentType = contentType,
        authors = authors,
        coverReference = null,
        score = null,
        scoreScale = null,
    )

    private fun canonicalCandidate(
        storyId: String = "story",
        title: String = "Reborn",
        authors: Set<String> = setOf("Author B"),
        contentType: ContentType = ContentType.WEB_NOVEL,
        pluginId: String = "catalog.existing",
        externalStoryId: String = "existing-1",
    ): CanonicalStory {
        val entry = CatalogEntry(
            id = CatalogEntryId("$pluginId:$externalStoryId"),
            catalogPluginId = PluginId(pluginId),
            externalStoryId = externalStoryId,
            sourceUrl = null,
            title = title,
            aliases = emptySet(),
            authors = authors,
            description = null,
            genres = emptySet(),
            contentType = contentType,
            languageTags = emptySet(),
            coverReference = null,
            publicationStatus = null,
            score = null,
            scoreScale = null,
            popularityRank = null,
            pluginVersion = "1.0.0",
            fetchedAtEpochMillis = 1L,
        )
        return CanonicalStory(
            id = StoryId(storyId),
            contentType = contentType,
            preferredTitle = title,
            aliases = emptySet(),
            catalogEntries = listOf(entry),
        )
    }
}
