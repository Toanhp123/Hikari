package app.openstory.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.storage.files.AtomicFileChapterBlobStore
import app.openstory.storage.files.FileBlobInventory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LowStorageBehaviorTest {
    @Test
    fun lowStorageRefusalDoesNotDeleteExplicitDownload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AtomicFileChapterBlobStore(context)
        val key = ChapterBlobKey(
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
            ChapterReleaseId("low-storage-protected"),
            "fingerprint-low-storage-protected",
        )
        val blob = ChapterBlob.fromBytes("protected".encodeToByteArray())
        store.write(key, blob)
        try {
            val inventory = FileBlobInventory(context, reserveBytes = Long.MAX_VALUE)

            assertFalse(inventory.canStore(blob.bytes().size.toLong()))
            assertEquals("protected", store.read(key)?.bytes()?.decodeToString())
        } finally {
            store.delete(key)
        }
    }
}
