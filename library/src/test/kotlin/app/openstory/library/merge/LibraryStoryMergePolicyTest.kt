package app.openstory.library.merge

import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryStoryMergePolicyTest {
    private val policy = LibraryStoryMergePolicy()
    private val survivor = StoryId("story:survivor")

    @Test
    fun noEntriesProducesNoMembership() {
        assertNull(policy.plan(survivor, null, null).entry)
    }

    @Test
    fun oneEntryMovesToSurvivorWithoutChangingUserState() {
        val entry = LibraryEntry(StoryId("story:retired"), LibraryStatus.READING, 10, 20)

        assertEquals(entry.copy(storyId = survivor), policy.plan(survivor, null, entry).entry)
    }

    @Test
    fun twoEntriesUseEarliestAddedAndMostRecentlyUpdatedStatus() {
        val left = LibraryEntry(StoryId("story:left"), LibraryStatus.READING, 20, 30)
        val right = LibraryEntry(StoryId("story:right"), LibraryStatus.COMPLETED, 10, 40)

        assertEquals(
            LibraryEntry(survivor, LibraryStatus.COMPLETED, 10, 40),
            policy.plan(survivor, left, right).entry,
        )
    }

    @Test
    fun tiedUpdateTimestampUsesStableStoryIdTieBreakIndependentOfArgumentOrder() {
        val lexicallyEarlier = LibraryEntry(StoryId("story:a"), LibraryStatus.PAUSED, 10, 40)
        val lexicallyLater = LibraryEntry(StoryId("story:z"), LibraryStatus.READING, 20, 40)

        val expected = LibraryEntry(survivor, LibraryStatus.PAUSED, 10, 40)
        assertEquals(expected, policy.plan(survivor, lexicallyEarlier, lexicallyLater).entry)
        assertEquals(expected, policy.plan(survivor, lexicallyLater, lexicallyEarlier).entry)
    }
}
