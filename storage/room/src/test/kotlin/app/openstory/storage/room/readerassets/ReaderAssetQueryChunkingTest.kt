package app.openstory.storage.room.readerassets

import app.openstory.reader.assets.ReaderAssetKeyHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderAssetQueryChunkingTest {
    @Test
    fun manifestSizedLookupStaysWithinRoomBindLimitWithoutDroppingKeys() {
        val keys = (0 until 2_000).mapTo(linkedSetOf()) { index ->
            ReaderAssetKeyHash(index.toString(16).padStart(64, '0'))
        }

        val chunks = readerAssetKeyHashChunks(keys)

        assertEquals(listOf(900, 900, 200), chunks.map(List<String>::size))
        assertTrue(chunks.all { chunk -> chunk.size <= 900 })
        assertEquals(keys.map(ReaderAssetKeyHash::value), chunks.flatten())
    }
}
