package app.openstory.storage.files

import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.assets.ReaderAssetBlobWriteResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class AtomicFileReaderAssetBlobStoreTest {
    private val root = Files.createTempDirectory("reader-assets").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `write stages syncs closes and atomically publishes checksum compatible bytes`() = runTest {
        val files = RecordingReaderAssetBlobFileOperations()
        val store = store(files)
        val payload = "encoded-image".encodeToByteArray()

        val result = assertIs<ReaderAssetBlobWriteResult.Stored>(store.writeAtomic(id(), payload))

        assertEquals(listOf("write", "sync", "close", "rename"), files.events)
        assertEquals(payload.size.toLong(), result.blob.sizeBytes)
        val lease = requireNotNull(store.open(id()))
        lease.use { assertContentEquals(payload, it.openStream().readBytes()) }
        assertEquals(result.blob.checksum, app.openstory.downloads.blob.BlobChecksum.sha256(payload))
        assertTrue(files.temporaryFiles.all { temporary -> !files.exists(temporary) })
    }

    @Test
    fun `active read lease delays normal deletion and blocks immediate physical relief`() = runTest {
        val store = store(PlatformReaderAssetBlobFileOperations)
        val id = id()
        val payload = "leased-image".encodeToByteArray()
        assertIs<ReaderAssetBlobWriteResult.Stored>(store.writeAtomic(id, payload))
        val lease = requireNotNull(store.open(id))

        val deletion = async(Dispatchers.Default) { store.deleteWhenUnleased(id) }

        assertFalse(store.tryDeleteNowIfUnleased(id))
        assertContentEquals(payload, lease.openStream().readBytes())
        assertFalse(deletion.isCompleted)
        lease.close()
        deletion.await()
        assertFalse(store.exists(id))
        assertNull(store.open(id))
    }

    @Test
    fun `immediate deletion succeeds after the final lease closes`() = runTest {
        val store = store(PlatformReaderAssetBlobFileOperations)
        val id = id()
        assertIs<ReaderAssetBlobWriteResult.Stored>(store.writeAtomic(id, byteArrayOf(1, 2, 3)))
        val lease = requireNotNull(store.open(id))
        lease.close()

        assertTrue(store.tryDeleteNowIfUnleased(id))
        assertFalse(store.exists(id))
    }

    @Test
    fun `failed replacement preserves the old committed blob and exposes no partial target`() = runTest {
        val files = RecordingReaderAssetBlobFileOperations()
        val store = store(files)
        val id = id()
        assertIs<ReaderAssetBlobWriteResult.Stored>(store.writeAtomic(id, "old".encodeToByteArray()))
        files.failNextRename = IOException("interrupted")

        assertIs<ReaderAssetBlobWriteResult.Unavailable>(
            store.writeAtomic(id, "new-partial".encodeToByteArray()),
        )

        requireNotNull(store.open(id)).use { lease ->
            assertEquals("old", lease.openStream().readBytes().decodeToString())
        }
        assertTrue(files.temporaryFiles.all { temporary -> !files.exists(temporary) })
    }

    @Test
    fun `classified no space is typed while unrelated io is unavailable and cancellation escapes`() = runTest {
        val noSpace = IOException("synthetic-no-space")
        val unavailable = IOException("synthetic-unavailable")
        val classifier = ReaderAssetStorageErrorClassifier { failure -> failure === noSpace }
        val files = RecordingReaderAssetBlobFileOperations()
        val store = AtomicFileReaderAssetBlobStore(root, files, ReaderAssetBlobFileLocks(), classifier)

        files.failNextOpen = noSpace
        assertEquals(ReaderAssetBlobWriteResult.NoSpace, store.writeAtomic(id("1"), byteArrayOf(1)))
        files.failNextOpen = unavailable
        assertEquals(
            unavailable,
            assertIs<ReaderAssetBlobWriteResult.Unavailable>(
                store.writeAtomic(id("2"), byteArrayOf(2)),
            ).cause,
        )
        files.failNextOpen = CancellationException("cancelled")
        assertFailsWith<CancellationException> { store.writeAtomic(id("3"), byteArrayOf(3)) }
    }

    @Test
    fun `oversized payload is rejected before filesystem mutation`() = runTest {
        val files = RecordingReaderAssetBlobFileOperations()
        val store = store(files)

        assertFailsWith<IllegalArgumentException> {
            store.writeAtomic(id(), ByteArray(16 * 1024 * 1024 + 1))
        }
        assertTrue(files.events.isEmpty())
    }

    private fun store(files: ReaderAssetBlobFileOperations) = AtomicFileReaderAssetBlobStore(
        root,
        files,
        ReaderAssetBlobFileLocks(),
        ReaderAssetStorageErrorClassifier { false },
    )

    private fun id(suffix: String = "0") = ReaderAssetBlobId(suffix.padStart(64, '0'))
}

private class RecordingReaderAssetBlobFileOperations : ReaderAssetBlobFileOperations {
    val events = mutableListOf<String>()
    val temporaryFiles = mutableListOf<File>()
    var failNextOpen: Throwable? = null
    var failNextRename: Throwable? = null
    private val contents = mutableMapOf<File, ByteArray>()

    override fun createTempFile(parent: File): File =
        File(parent, ".stage-${temporaryFiles.size}.tmp").also(temporaryFiles::add)

    override fun openOutput(file: File): BlobFileOutput {
        failNextOpen?.let { failure ->
            failNextOpen = null
            throw failure
        }
        return object : BlobFileOutput {
            override fun write(bytes: ByteArray) {
                events += "write"
                contents[file] = (contents[file] ?: byteArrayOf()) + bytes
            }

            override fun sync() {
                events += "sync"
            }

            override fun close() {
                events += "close"
            }
        }
    }

    override fun moveAtomically(source: File, target: File) {
        events += "rename"
        failNextRename?.let { failure ->
            failNextRename = null
            throw failure
        }
        contents[target] = checkNotNull(contents.remove(source))
    }

    override fun openInput(file: File) = contents[file]?.inputStream()

    override fun delete(file: File): Boolean = contents.remove(file) != null

    override fun exists(file: File): Boolean = file in contents

    override fun size(file: File): Long = contents[file]?.size?.toLong() ?: 0L
}
