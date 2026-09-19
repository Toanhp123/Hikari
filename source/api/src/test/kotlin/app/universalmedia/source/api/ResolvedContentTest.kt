package app.universalmedia.source.api

import org.junit.Assert.assertFalse
import org.junit.Test

class ResolvedContentTest {
    @Test fun runtimeLocatorIsRedactedFromDiagnostics() {
        val video = ResolvedVideo("content://private/tree/folder/document/movie", "video/mp4")
        assertFalse(video.toString().contains("content://"))
        assertFalse(video.toString().contains("movie"))
    }
}
