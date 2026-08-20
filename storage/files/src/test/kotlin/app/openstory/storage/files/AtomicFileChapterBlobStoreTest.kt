package app.openstory.storage.files

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class AtomicFileChapterBlobStoreTest {
    private val root = Files.createTempDirectory("chapter-blobs").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `blocking file callbacks execute on the injected io dispatcher`() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "blob-io-test")
        }
        val ioDispatcher = executor.asCoroutineDispatcher()
        try {
            val files = ThreadRecordingBlobFileOperations()
            val store = AtomicFileChapterBlobStore(root, files, ioDispatcher)

            store.write(key(), ChapterBlob.fromBytes("chapter".encodeToByteArray()))
            store.read(key())
            store.delete(key())

            assertTrue(files.callbackThreads.isNotEmpty())
            assertTrue(files.callbackThreads.all { it == "blob-io-test" })
        } finally {
            ioDispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `write for one blob key does not block an unrelated blob key`() = runTest {
        val files = ParallelBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files, Dispatchers.IO)
        val first = async(Dispatchers.Default) {
            store.write(key("release-a"), ChapterBlob.fromBytes("a".encodeToByteArray()))
        }
        assertTrue(files.awaitFirstWrite())

        val second = async(Dispatchers.Default) {
            store.write(key("release-b"), ChapterBlob.fromBytes("b".encodeToByteArray()))
        }

        assertTrue(files.awaitSecondWrite())
        files.releaseFirstWrite()
        first.await()
        second.await()
    }

    @Test
    fun `write stages bytes fsyncs and closes before atomic rename`() = runTest {
        val files = RecordingBlobFileOperations()
        val store = AtomicFileChapterBlobStore(root, files)

        store.write(key(), ChapterBlob.fromBytes("new chapter".encodeToByteArray()))

        assertEquals(listOf("write", "write", "write", "sync", "close", "rename"), files.events)
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

    private fun key(releaseId: String = "release-1") = ChapterBlobKey(
        namespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
        releaseId = ChapterReleaseId(releaseId),
        contentFingerprint = "fingerprint-$releaseId",
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
    private var outputSessions = 0
    private val contents = mutableMapOf<File, ByteArray>()

    override fun createTempFile(parent: File): File = File(parent, ".stage-${temporaryFiles.size}").also {
        temporaryFiles += it
        touchedFiles += it
    }

    override fun openOutput(file: File): BlobFileOutput {
        outputSessions += 1
        if (outputSessions == 2) {
            secondWriteStarted.countDown()
        }
        return object : BlobFileOutput {
            override fun write(bytes: ByteArray) {
                events += "write"
                touchedFiles += file
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

    override fun exists(file: File): Boolean = contents.containsKey(file)

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

private class ThreadRecordingBlobFileOperations : BlobFileOperations {
    val callbackThreads = CopyOnWriteArrayList<String>()
    private val contents = mutableMapOf<File, ByteArray>()

    private fun record() {
        callbackThreads += Thread.currentThread().name
    }

    override fun createTempFile(parent: File): File {
        record()
        return File(parent, ".stage-thread.tmp")
    }

    override fun openOutput(file: File): BlobFileOutput {
        record()
        return object : BlobFileOutput {
            override fun write(bytes: ByteArray) {
                record()
                contents[file] = (contents[file] ?: byteArrayOf()) + bytes
            }

            override fun sync() = record()

            override fun close() = record()
        }
    }

    override fun moveAtomically(source: File, target: File) {
        record()
        contents[target] = checkNotNull(contents.remove(source))
    }

    override fun readBytes(file: File): ByteArray? {
        record()
        return contents[file]?.copyOf()
    }

    override fun delete(file: File) {
        record()
        contents.remove(file)
    }

    override fun exists(file: File): Boolean {
        record()
        return file in contents
    }
}

private class ParallelBlobFileOperations : BlobFileOperations {
    private val firstWriteStarted = CountDownLatch(1)
    private val releaseFirstWrite = CountDownLatch(1)
    private val secondWriteStarted = CountDownLatch(1)
    private val outputSessions = java.util.concurrent.atomic.AtomicInteger()
    private val contents = java.util.concurrent.ConcurrentHashMap<File, ByteArray>()

    override fun createTempFile(parent: File): File = File(parent, ".stage-${System.nanoTime()}.tmp")

    override fun openOutput(file: File): BlobFileOutput {
        val session = outputSessions.incrementAndGet()
        var firstChunk = true
        return object : BlobFileOutput {
            override fun write(bytes: ByteArray) {
                if (firstChunk) {
                    firstChunk = false
                    when (session) {
                        1 -> {
                            firstWriteStarted.countDown()
                            check(releaseFirstWrite.await(1, TimeUnit.SECONDS)) { "Timed out releasing first write." }
                        }
                        2 -> secondWriteStarted.countDown()
                    }
                }
                contents.compute(file) { _, current -> (current ?: byteArrayOf()) + bytes }
            }

            override fun sync() = Unit

            override fun close() = Unit
        }
    }

    override fun moveAtomically(source: File, target: File) {
        contents[target] = checkNotNull(contents.remove(source))
    }

    override fun readBytes(file: File): ByteArray? = contents[file]?.copyOf()

    override fun delete(file: File) {
        contents.remove(file)
    }

    override fun exists(file: File): Boolean = contents.containsKey(file)

    fun awaitFirstWrite(): Boolean = firstWriteStarted.await(1, TimeUnit.SECONDS)

    fun awaitSecondWrite(): Boolean = secondWriteStarted.await(500, TimeUnit.MILLISECONDS)

    fun releaseFirstWrite() {
        releaseFirstWrite.countDown()
    }
}
