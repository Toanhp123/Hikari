package app.openstory.downloads.assets

import app.openstory.reader.assets.ReaderAssetKeyHash
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReaderAssetBlobIdFactoryTest {
    @Test
    fun `blob ids are generation unique across factory instances and contain no source facts`() {
        val logicalKey = ReaderAssetKeyHash("a".repeat(64))
        val first = ReaderAssetBlobIdFactory {
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        }.create(logicalKey)
        val second = ReaderAssetBlobIdFactory {
            UUID.fromString("00000000-0000-0000-0000-000000000002")
        }.create(logicalKey)

        assertNotEquals(first, second)
        assertTrue(first.value.matches(Regex("[0-9a-f]{64}")))
        assertFalse(first.value.contains(logicalKey.value))
    }
}
