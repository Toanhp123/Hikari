package app.openstory.work

import app.openstory.chapters.sync.ChapterSyncBatchCursor
import app.openstory.common.id.StoryId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChapterSyncCursorCodecTest {
    private val codec = ChapterSyncCursorCodec()

    @Test
    fun roundTripsVersionedBoundedCursor() {
        val cursor = ChapterSyncBatchCursor(123L, StoryId("story:cursor"))
        assertEquals(cursor, codec.decode(codec.encode(cursor)))
    }

    @Test
    fun rejectsBlankOversizedUnknownVersionNegativeTimestampAndInvalidStoryId() {
        assertFailsWith<IllegalArgumentException> { codec.decode("") }
        assertFailsWith<IllegalArgumentException> { codec.decode("a".repeat(513)) }
        assertFailsWith<IllegalArgumentException> { codec.decode(payload("{\"v\":2,\"t\":1,\"s\":\"story:a\"}")) }
        assertFailsWith<IllegalArgumentException> { codec.decode(payload("{\"v\":1,\"t\":-1,\"s\":\"story:a\"}")) }
        assertFailsWith<IllegalArgumentException> { codec.decode(payload("{\"v\":1,\"t\":1,\"s\":\"bad id\"}")) }
    }

    private fun payload(json: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.encodeToByteArray())
}
