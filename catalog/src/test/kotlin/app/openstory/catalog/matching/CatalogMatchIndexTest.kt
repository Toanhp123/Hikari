package app.openstory.catalog.matching

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogMatchIndexTest {
    private val matcher = StoryMatcher()

    @Test
    fun directSourceIdentityResolvesWithoutChangingCanonicalStory() {
        val key = SourceKey(PluginId("plugin"), "source")
        val existing = candidate("story:existing", "Stored", setOf(key))
        val index = CatalogMatchIndex(matcher, listOf(existing))

        val resolution = index.resolve(candidate("incoming", "Different", setOf(key)))

        assertEquals(StoryResolution.Existing(existing.story.id), resolution)
        assertEquals(existing.story, index.story(existing.story.id))
    }

    @Test
    fun evidenceResolutionUsesCanonicalCandidateAndCreatedStoriesBecomeAddressable() {
        val existing = candidate("story:existing", "Reborn Hero", emptySet(), setOf("Author"))
        val index = CatalogMatchIndex(matcher, listOf(existing))

        assertEquals(
            StoryResolution.Existing(existing.story.id),
            index.resolve(candidate("incoming:match", "Reborn Hero", emptySet(), setOf("Author"))),
        )

        val created = index.resolve(candidate("incoming:new", "Completely Different", emptySet())) as StoryResolution.Create
        assertEquals(created.story, index.story(created.story.id))
    }

    @Test
    fun canonicalStoryKeepsPerSourceEvidenceInsteadOfUnioningAuthorsAndContentType() {
        val storyId = StoryId("story:shared")
        val mangaEvidence = CatalogMatchCandidate(
            Story(storyId, ContentType.MANGA),
            setOf("Shared"),
            setOf("Other A", "Other B", "Other C"),
            emptySet(),
        )
        val webNovelEvidence = CatalogMatchCandidate(
            Story(storyId, ContentType.WEB_NOVEL),
            setOf("Shared"),
            setOf("Alice"),
            emptySet(),
        )
        val incoming = CatalogMatchCandidate(
            Story(StoryId("incoming"), ContentType.WEB_NOVEL),
            setOf("Shared"),
            setOf("Alice"),
            emptySet(),
        )
        val index = CatalogMatchIndex(matcher, listOf(mangaEvidence, webNovelEvidence))

        assertEquals(StoryResolution.Existing(storyId), index.resolve(incoming))
    }

    @Test
    fun duplicateSourceEvidencePreservesMinimumLeadBehavior() {
        val storyId = StoryId("story:shared")
        val first = candidate("ignored:first", "Reborn Hero", emptySet(), setOf("Alice")).copy(
            story = Story(storyId, ContentType.MANGA),
        )
        val second = candidate("ignored:second", "Reborn Hero", emptySet(), setOf("Alice")).copy(
            story = Story(storyId, ContentType.MANGA),
        )
        val incoming = candidate("incoming", "Reborn Hero", emptySet(), setOf("Alice"))
        val index = CatalogMatchIndex(matcher, listOf(first, second))

        assertEquals(
            matcher.resolve(incoming, listOf(first, second)),
            index.resolve(incoming),
        )
    }

    @Test
    fun forkKeepsFailedPageMutationsIsolated() {
        val original = CatalogMatchIndex(matcher, emptyList())
        val fork = original.fork()
        val created = fork.resolve(candidate("incoming", "Only In Fork", emptySet())) as StoryResolution.Create

        assertEquals(created.story, fork.story(created.story.id))
        assertFailsWith<NoSuchElementException> { original.story(created.story.id) }
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
