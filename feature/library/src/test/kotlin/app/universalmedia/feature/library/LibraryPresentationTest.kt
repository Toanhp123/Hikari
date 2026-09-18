package app.universalmedia.feature.library

import app.universalmedia.core.model.MediaId
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPresentationTest {
    @Test
    fun representationLabelNeverChangesCardIdentity() {
        val id = MediaId.generate()
        val original = LibraryCardUi(id, "movie.mp4")
        val renamed = original.copy(displayName = "renamed.mp4")
        assertEquals(id, renamed.mediaId)
        assertEquals("renamed.mp4", renamed.label)
    }

    @Test
    fun missingOrBlankRepresentationLabelHasReadableFallback() {
        assertEquals("Untitled video", LibraryCardUi(MediaId.generate(), null).label)
        assertEquals("Untitled video", LibraryCardUi(MediaId.generate(), "  ").label)
    }
}
