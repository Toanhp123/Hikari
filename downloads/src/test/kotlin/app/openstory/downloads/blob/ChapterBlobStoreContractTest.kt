package app.openstory.downloads.blob

import app.openstory.common.id.ChapterReleaseId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChapterBlobStoreContractTest {
    @Test
    fun `verified content rejects a checksum that does not describe its bytes`() {
        assertFailsWith<IllegalArgumentException> {
            ChapterBlob.verified(
                bytes = "chapter".encodeToByteArray(),
                checksum = BlobChecksum("0".repeat(64)),
            )
        }
    }

    @Test
    fun `verified content reports the sha256 checksum for its bytes`() {
        val blob = ChapterBlob.fromBytes("chapter".encodeToByteArray())

        assertEquals(
            "667b9327cc0b3ab64f0bea63ff617f1a7e3d085854965f76f502a02ebc62f075",
            blob.checksum.value,
        )
    }


    @Test
    fun `blob owns its source and returned byte arrays defensively`() {
        val original = "chapter".encodeToByteArray()
        val expected = original.copyOf()
        val blob = ChapterBlob.fromBytes(original)

        original[0] = 'X'.code.toByte()
        val firstRead = blob.bytes()
        firstRead[1] = 'Y'.code.toByte()

        assertContentEquals(expected, blob.bytes())
    }

    @Test
    fun `blob exposes size and a read stream without a public mutable view`() {
        val expected = "chapter".encodeToByteArray()
        val blob = ChapterBlob.fromBytes(expected)

        assertEquals(expected.size, blob.sizeBytes)
        assertContentEquals(expected, blob.inputStream().readBytes())
    }

    @Test
    fun `verified slice hashes only the payload range and owns that range`() {
        val payload = "chapter".encodeToByteArray()
        val encoded = "header:".encodeToByteArray() + payload + ":tail".encodeToByteArray()
        val offset = "header:".encodeToByteArray().size
        val blob = ChapterBlob.verified(
            bytes = encoded,
            offset = offset,
            length = payload.size,
            checksum = BlobChecksum.sha256(payload),
        )

        encoded[offset] = 'X'.code.toByte()

        assertEquals(payload.size, blob.sizeBytes)
        assertContentEquals(payload, blob.bytes())
    }

    @Test
    fun `verified slice rejects a checksum for different bytes`() {
        val encoded = "header:chapter".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            ChapterBlob.verified(
                bytes = encoded,
                offset = "header:".length,
                length = "chapter".length,
                checksum = BlobChecksum.sha256("different".encodeToByteArray()),
            )
        }
    }

    @Test
    fun `blob keys retain namespace release identity and fingerprint without filesystem paths`() {
        val key = ChapterBlobKey(
            namespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
            releaseId = ChapterReleaseId("release/../../outside"),
            contentFingerprint = "fingerprint/../../outside",
        )

        assertEquals(ChapterBlobNamespace.AUTOMATIC_CACHE, key.namespace)
        assertEquals("release/../../outside", key.releaseId.value)
        assertEquals("fingerprint/../../outside", key.contentFingerprint)
    }
}
