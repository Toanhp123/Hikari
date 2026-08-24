package app.openstory.catalog.identity

import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class StorySurvivorSelectorTest {
    private val selector = StorySurvivorSelector()

    @Test
    fun higherMeaningfulDomainCountWinsBeforeTotalState() {
        val broader = candidate(
            id = "story:a",
            footprint = UserStateFootprint(
                hasLibraryMembership = true,
                readingProgressCount = 1,
                protectedContentMappingCount = 0,
                hasPinnedPrimary = false,
                manualChapterOverrideCount = 0,
            ),
        )
        val denser = candidate(
            id = "story:b",
            footprint = UserStateFootprint(
                hasLibraryMembership = false,
                readingProgressCount = 50,
                protectedContentMappingCount = 0,
                hasPinnedPrimary = false,
                manualChapterOverrideCount = 0,
            ),
        )

        assertEquals(broader.storyId, selector.select(broader, denser).survivor.storyId)
        assertEquals(broader.storyId, selector.select(denser, broader).survivor.storyId)
    }

    @Test
    fun higherMeaningfulStateTotalBreaksEqualDomainCount() {
        val lighter = candidate(
            id = "story:a",
            footprint = UserStateFootprint(false, 1, 0, false, 1),
        )
        val heavier = candidate(
            id = "story:b",
            footprint = UserStateFootprint(false, 4, 0, false, 1),
        )

        assertEquals(heavier.storyId, selector.select(lighter, heavier).survivor.storyId)
    }

    @Test
    fun olderTrustworthyCreationTimeBreaksEqualFootprint() {
        val older = candidate("story:z", createdAt = 10L)
        val newer = candidate("story:a", createdAt = 20L)

        assertEquals(older.storyId, selector.select(newer, older).survivor.storyId)
    }

    @Test
    fun unknownCreationTimeSkipsAgeComparisonEntirely() {
        val knownOlderButLexicallyLater = candidate("story:z", createdAt = 1L)
        val unknownButLexicallyEarlier = candidate("story:a", createdAt = null)

        assertEquals(
            unknownButLexicallyEarlier.storyId,
            selector.select(knownOlderButLexicallyLater, unknownButLexicallyEarlier).survivor.storyId,
        )
    }

    @Test
    fun lexicalStoryIdIsFinalStableTieBreak() {
        val a = candidate("story:a", createdAt = null)
        val b = candidate("story:b", createdAt = null)

        assertEquals(a.storyId, selector.select(b, a).survivor.storyId)
        assertEquals(a.storyId, selector.select(a, b).survivor.storyId)
    }

    private fun candidate(
        id: String,
        createdAt: Long? = null,
        footprint: UserStateFootprint = UserStateFootprint(false, 0, 0, false, 0),
    ) = StoryMergeCandidate(
        storyId = StoryId(id),
        identityRevision = 0L,
        createdAtEpochMillis = createdAt,
        footprint = footprint,
    )
}
