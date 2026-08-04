package app.openstory.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdsTest {

    @Test
    fun differentIdentifierTypesRetainStableValue() {
        assertEquals(
            "story_1",
            StoryId("story_1").value,
        )
        assertEquals(
            "chapter_1",
            ChapterId("chapter_1").value,
        )
        assertFailsWith<IllegalArgumentException> {
            ReleaseId(" ")
        }
    }

    @Test
    fun contentTypesReserveSupportedAndFutureMedia() {
        assertEquals(
            setOf(
                ContentType.LIGHT_NOVEL,
                ContentType.WEB_NOVEL,
                ContentType.MANGA,
                ContentType.ANIME,
            ),
            ContentType.entries.toSet(),
        )
    }

    @Test
    fun libraryStatusesCoverLocalReadingLifecycle() {
        assertEquals(
            setOf(
                LibraryStatus.WANT_TO_READ,
                LibraryStatus.READING,
                LibraryStatus.PAUSED,
                LibraryStatus.COMPLETED,
                LibraryStatus.DROPPED,
            ),
            LibraryStatus.entries.toSet(),
        )
    }

    @Test
    fun languageTagNormalizesSeparatorsAndCase() {
        assertEquals(
            "en-us",
            LanguageTag("EN_us").value,
        )
        assertEquals(
            "vi",
            LanguageTag("VI").value,
        )
    }

    @Test
    fun contentAndStatusEnumsRoundTripThroughSerialization() {
        val contentType = ContentType.WEB_NOVEL
        val encodedContentType = Json.encodeToString(
            ContentType.serializer(),
            contentType,
        )

        assertEquals(
            contentType,
            Json.decodeFromString(
                ContentType.serializer(),
                encodedContentType,
            ),
        )

        val libraryStatus = LibraryStatus.READING
        val encodedLibraryStatus = Json.encodeToString(
            LibraryStatus.serializer(),
            libraryStatus,
        )

        assertEquals(
            libraryStatus,
            Json.decodeFromString(
                LibraryStatus.serializer(),
                encodedLibraryStatus,
            ),
        )
    }
}
