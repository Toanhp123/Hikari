package app.openstory.storage.files

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class AtomicFileChapterBlobStoreTest {
    private val root = Files.createTempDirectory("chapter-blobs").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `write stages bytes fsyncs and closes before atomic rename`() = runTest {
        val files = RecordingBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files)

        store.write(key(), ChapterBlob.fromBytes("new chapter".encodeToByteArray()))

        assertEquals(listOf("write", "sync", "close", "rename"), files.events)
        assertContentEquals("new chapter".encodeToByteArray(), store.read(key())!!.bytes())
        assertTrue(files.temporaryFiles.all { !files.exists(it) })
    }

    @Test
    fun `read rejects a blob whose stored bytes no longer match its checksum`() = runTest {
        val files = RecordingBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files)
        val key = key()
        store.write(key, ChapterBlob.fromBytes("old chapter".encodeToByteArray()))
        files.replaceLastCommittedBytes("corrupt chapter".encodeToByteArray())

        assertNull(store.read(key))
    }

    @Test
    fun `write confines hostile key values below the adapter root`() = runTest {
        val files = RecordingBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files)
        val hostileKey = ChapterBlobKey(
            namespace = ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
            releaseId = ChapterReleaseId("../../outside"),
            contentFingerprint = "../../outside",
        )

        store.write(hostileKey, ChapterBlob.fromBytes("chapter".encodeToByteArray()))

        assertTrue(files.touchedFiles.all { it.toPath().normalize().startsWith(root.toPath().normalize()) })
        assertFalse(File(root.parentFile, "outside").exists())
    }

    @Test
    fun `interrupted write removes its temporary blob and preserves the committed blob`() = runTest {
        val files = RecordingBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files)
        val key = key()
        store.write(key, ChapterBlob.fromBytes("old chapter".encodeToByteArray()))
        files.failNextRename = true

        try {
            store.write(key, ChapterBlob.fromBytes("new chapter".encodeToByteArray()))
        } catch (_: IOException) {
            // A failed rename represents process interruption before commit.
        }

        assertContentEquals("old chapter".encodeToByteArray(), store.read(key)!!.bytes())
        assertTrue(files.temporaryFiles.all { !files.exists(it) })
    }

    @Test
    fun `corrupt read cannot delete a blob committed while validation is in flight`() = runTest {
        val files = RecordingBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files)
        val key = key()
        store.write(key, ChapterBlob.fromBytes("old chapter".encodeToByteArray()))
        files.replaceLastCommittedBytes("corrupt chapter".encodeToByteArray())
        files.blockNextRead()

        val staleReader = async(Dispatchers.Default) {
            store.read(key)
        }
        assertTrue(files.awaitBlockedRead())

        val writerAttempted = CountDownLatch(1)
        val replacementWriter = async(Dispatchers.Default) {
            writerAttempted.countDown()
            store.write(key, ChapterBlob.fromBytes("new chapter".encodeToByteArray()))
        }
        assertTrue(writerAttempted.await(1, TimeUnit.SECONDS))
        assertFalse(files.awaitSecondWriteStart())

        files.releaseBlockedRead()
        assertNull(staleReader.await())
        replacementWriter.await()

        assertContentEquals("new chapter".encodeToByteArray(), store.read(key)!!.bytes())
    }

    @Test
    fun `read returns null when a blob is absent`() = runTest {
        val store = AtomicFileChapterBlobStore(root, RecordingBlobFileOperations())

        assertNull(store.read(key()))
    }

    private fun key() = ChapterBlobKey(
        namespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
        releaseId = ChapterReleaseId("release-1"),
        contentFingerprint = "fingerprint-1",
    )
}

private class RecordingBlobFileOperations : BlobFileOperations {
    val events = mutableListOf<String>()
    val temporaryFiles = mutableListOf<File>()
    val touchedFiles = mutableListOf<File>()
    var failNextRename = false
    @Volatile
    private var shouldBlockNextRead = false
    private val blockedRead = CountDownLatch(1)
    private val releaseRead = CountDownLatch(1)
    private val secondWriteStarted = CountDownLatch(1)
    private val contents = mutableMapOf<File, ByteArray>()

    override fun createTempFile(parent: File): File = File(parent, ".stage-${temporaryFiles.size}").also {
        temporaryFiles += it
        touchedFiles += it
    }

    override fun openOutput(file: File): BlobFileOutput = object : BlobFileOutput {
        override fun write(bytes: ByteArray) {
            events += "write"
            if (events.count { it == "write" } == 2) {
                secondWriteStarted.countDown()
            }
            touchedFiles += file
            contents[file] = bytes.copyOf()
        }

        override fun sync() {
            events += "sync"
        }

        override fun close() {
            events += "close"
        }
    }

    override fun moveAtomically(source: File, target: File) {
        events += "rename"
        touchedFiles += source
        touchedFiles += target
        if (failNextRename) {
            failNextRename = false
            throw IOException("interrupted")
        }
        contents[target] = checkNotNull(contents.remove(source))
    }

    override fun readBytes(file: File): ByteArray? {
        if (shouldBlockNextRead) {
            shouldBlockNextRead = false
            blockedRead.countDown()
            check(releaseRead.await(1, TimeUnit.SECONDS)) { "Timed out waiting to release the blocked read." }
        }
        return contents[file]?.copyOf()
    }

    override fun delete(file: File) {
        contents.remove(file)
    }

    override fun exists(file: File): Boolean = file in contents

    fun replaceLastCommittedBytes(bytes: ByteArray) {
        val target = touchedFiles.last { !it.name.startsWith(".stage-") }
        contents[target] = bytes.copyOf()
    }

    fun blockNextRead() {
        shouldBlockNextRead = true
    }

    fun awaitBlockedRead(): Boolean = blockedRead.await(1, TimeUnit.SECONDS)

    fun awaitSecondWriteStart(): Boolean = secondWriteStarted.await(100, TimeUnit.MILLISECONDS)

    fun releaseBlockedRead() {
        releaseRead.countDown()
    }
}
