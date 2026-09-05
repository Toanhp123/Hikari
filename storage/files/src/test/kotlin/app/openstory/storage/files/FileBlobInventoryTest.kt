package app.openstory.storage.files

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.assets.ReaderAssetBlobId
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FileBlobInventoryTest {
    private val root = Files.createTempDirectory("blob-inventory").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `scan reports known and orphan blobs without exposing filesystem paths`() = runTest {
        val store = AtomicFileChapterBlobStore(root, PlatformBlobFileOperations)
        val known = key("known")
        val orphan = key("orphan")
        store.write(known, ChapterBlob.fromBytes("known".encodeToByteArray()))
        store.write(orphan, ChapterBlob.fromBytes("orphan".encodeToByteArray()))
        val inventory = FileBlobInventory(root, reserveBytes = 0, availableBytes = { 100 })

        val snapshot = inventory.scan(setOf(known), staleBeforeEpochMillis = Long.MAX_VALUE)

        assertEquals(setOf(known), snapshot.presentKeys)
        assertEquals(1, snapshot.orphanArtifacts.size)
        assertFalse(snapshot.orphanArtifacts.single().value.contains(root.absolutePath))
    }

    @Test
    fun `delete removes only selected orphan blob`() = runTest {
        val store = AtomicFileChapterBlobStore(root, PlatformBlobFileOperations)
        val known = key("known")
        val orphan = key("orphan")
        store.write(known, ChapterBlob.fromBytes("known".encodeToByteArray()))
        store.write(orphan, ChapterBlob.fromBytes("orphan".encodeToByteArray()))
        val inventory = FileBlobInventory(root, reserveBytes = 0, availableBytes = { 100 })
        val snapshot = inventory.scan(setOf(known), staleBeforeEpochMillis = Long.MAX_VALUE)

        inventory.delete(snapshot.orphanArtifacts)

        assertEquals("known", store.read(known)?.bytes()?.decodeToString())
        assertNull(store.read(orphan))
    }

    @Test
    fun `scan reports only stale interrupted temp files and ignores unknown files`() = runTest {
        val cache = root.resolve("cache").apply { mkdirs() }
        val stale = cache.resolve(".stage-stale.tmp").apply {
            writeText("partial")
            setLastModified(100)
        }
        cache.resolve(".stage-active.tmp").apply {
            writeText("active")
            setLastModified(900)
        }
        cache.resolve("keep.txt").writeText("unmanaged")
        val inventory = FileBlobInventory(root, reserveBytes = 0, availableBytes = { 100 })

        val snapshot = inventory.scan(emptySet(), staleBeforeEpochMillis = 500)
        inventory.delete(snapshot.interruptedWriteArtifacts)

        assertEquals(1, snapshot.interruptedWriteArtifacts.size)
        assertFalse(stale.exists())
        assertTrue(cache.resolve(".stage-active.tmp").exists())
        assertTrue(cache.resolve("keep.txt").exists())
    }

    @Test
    fun `space guard keeps reserve and rejects invalid estimates`() {
        val inventory = FileBlobInventory(root, reserveBytes = 20, availableBytes = { 100 })

        assertTrue(inventory.canStore(80))
        assertFalse(inventory.canStore(81))
        assertFalse(inventory.canStore(-1))
        assertFalse(
            FileBlobInventory(root, reserveBytes = 20, availableBytes = { 20 })
                .canStore(0),
        )
    }

    @Test
    fun `reader asset scan reports bounded orphan blobs and stale interrupted writes`() = runTest {
        val readerRoot = root.resolve("reader-assets").apply { mkdirs() }
        val known = ReaderAssetBlobId("1".repeat(64))
        val orphan = ReaderAssetBlobId("2".repeat(64))
        readerRoot.resolve("${known.value}.blob").writeText("known")
        readerRoot.resolve("${orphan.value}.blob").apply {
            writeText("orphan")
            setLastModified(100)
        }
        val stale = readerRoot.resolve(".stage-stale.tmp").apply {
            writeText("partial")
            setLastModified(100)
        }
        readerRoot.resolve(".stage-active.tmp").apply {
            writeText("active")
            setLastModified(900)
        }
        val inventory = FileBlobInventory(
            rootDirectory = root,
            readerAssetRootDirectory = readerRoot,
            reserveBytes = 0,
            availableBytes = { 100 },
        )

        val snapshot = inventory.scanReaderAssets(
            expectedBlobIds = setOf(known),
            staleBeforeEpochMillis = 500,
            limit = 2,
        )

        assertEquals(setOf(known), snapshot.presentBlobIds)
        assertEquals(1, snapshot.orphanArtifacts.size)
        assertEquals(1, snapshot.interruptedWriteArtifacts.size)
        assertFalse(snapshot.orphanArtifacts.single().value.contains(root.absolutePath))
        inventory.delete(snapshot.orphanArtifacts + snapshot.interruptedWriteArtifacts)
        assertFalse(readerRoot.resolve("${orphan.value}.blob").exists())
        assertFalse(stale.exists())
    }

    @Test
    fun `fresh metadata missing reader blob is protected from reconciliation as an active publication`() = runTest {
        val readerRoot = root.resolve("reader-assets").apply { mkdirs() }
        val fresh = ReaderAssetBlobId("3".repeat(64))
        readerRoot.resolve("${fresh.value}.blob").apply {
            writeText("publishing")
            setLastModified(900)
        }
        val inventory = FileBlobInventory(
            rootDirectory = root,
            readerAssetRootDirectory = readerRoot,
            reserveBytes = 0,
            availableBytes = { 100 },
        )

        val snapshot = inventory.scanReaderAssets(
            expectedBlobIds = emptySet(),
            staleBeforeEpochMillis = 500,
            limit = 8,
        )

        assertTrue(snapshot.orphanArtifacts.isEmpty())
        assertTrue(snapshot.scanComplete)
        assertTrue(readerRoot.resolve("${fresh.value}.blob").exists())
    }

    private fun key(id: String) = ChapterBlobKey(
        ChapterBlobNamespace.AUTOMATIC_CACHE,
        ChapterReleaseId(id),
        "fingerprint-$id",
    )
}
