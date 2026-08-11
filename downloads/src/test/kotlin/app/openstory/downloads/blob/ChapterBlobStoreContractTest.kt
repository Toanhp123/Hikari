package app.openstory.downloads.blob

import app.openstory.common.id.ChapterReleaseId
import kotlin.test.Test
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
