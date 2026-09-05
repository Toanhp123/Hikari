package app.openstory.storage.files

import android.content.Context
import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.reconcile.ReaderAssetReconciliationInventory
import app.openstory.downloads.reconcile.ReaderAssetStorageInventorySnapshot
import app.openstory.downloads.reconcile.StorageArtifactId
import app.openstory.downloads.reconcile.StorageInventorySnapshot
import app.openstory.downloads.reconcile.StorageReconciliationInventory
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileBlobInventory internal constructor(
    private val rootDirectory: File,
    private val readerAssetRootDirectory: File = File(rootDirectory, ReaderAssetBlobFileLayout.ROOT_DIRECTORY_NAME),
    private val reserveBytes: Long,
    private val availableBytes: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StorageReconciliationInventory, ReaderAssetReconciliationInventory {
    init {
        require(reserveBytes >= 0) { "Storage reserve must not be negative." }
    }

    constructor(
        context: Context,
        reserveBytes: Long = DEFAULT_RESERVE_BYTES,
    ) : this(
        rootDirectory = ChapterBlobFileLayout.root(context),
        readerAssetRootDirectory = ReaderAssetBlobFileLayout.root(context),
        reserveBytes = reserveBytes,
        availableBytes = { context.filesDir.usableSpace },
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun scan(
        expectedKeys: Set<ChapterBlobKey>,
        staleBeforeEpochMillis: Long,
    ): StorageInventorySnapshot = withContext(ioDispatcher) {
        val expectedByArtifact = expectedKeys.associateBy(::blobArtifactId)
        val present = mutableSetOf<ChapterBlobKey>()
        val orphans = mutableListOf<StorageArtifactId>()
        val interrupted = mutableListOf<StorageArtifactId>()
        ChapterBlobNamespace.entries.forEach { namespace ->
            val directory = ChapterBlobFileLayout.namespaceDirectory(rootDirectory, namespace)
            directory.listFiles()?.forEach { file ->
                when {
                    file.isFile && BLOB_FILE.matches(file.name) -> {
                        val artifact = blobArtifactId(namespace, file.nameWithoutExtension)
                        expectedByArtifact[artifact]?.let(present::add) ?: orphans.add(artifact)
                    }
                    file.isFile && TEMP_FILE.matches(file.name) &&
                        file.lastModified() < staleBeforeEpochMillis -> {
                        interrupted += tempArtifactId(namespace, file.name)
                    }
                }
            }
        }
        StorageInventorySnapshot(present, orphans, interrupted)
    }

    override suspend fun delete(artifacts: List<StorageArtifactId>) {
        withContext(ioDispatcher) {
            artifacts.distinct().forEach { artifact ->
                artifactFile(artifact)?.let { file ->
                    when {
                        artifact.value.startsWith("$BLOB_ARTIFACT:") ->
                            ChapterBlobFileLocks.withLock(file) { deleteArtifact(file) }
                        artifact.value.startsWith("$READER_ASSET_BLOB_ARTIFACT:") ->
                            ReaderAssetBlobFileLocks.Shared.withLock(file) { entry ->
                                if (!ReaderAssetBlobFileLocks.Shared.hasActiveLeases(entry)) {
                                    deleteArtifact(file)
                                }
                            }
                        else -> deleteArtifact(file)
                    }
                }
            }
        }
    }

    override suspend fun scanReaderAssets(
        expectedBlobIds: Set<ReaderAssetBlobId>,
        staleBeforeEpochMillis: Long,
        limit: Int,
    ): ReaderAssetStorageInventorySnapshot = withContext(ioDispatcher) {
        require(limit >= 0) { "Reader asset inventory limit must not be negative." }
        val present = linkedSetOf<ReaderAssetBlobId>()
        val orphans = mutableListOf<StorageArtifactId>()
        val interrupted = mutableListOf<StorageArtifactId>()
        val expected = expectedBlobIds.associateBy(ReaderAssetBlobId::value)
        val files = readerAssetRootDirectory.listFiles()?.sortedBy(File::getName).orEmpty()
        var scanComplete = true
        for (file in files) {
            if (orphans.size + interrupted.size >= limit) {
                scanComplete = false
                break
            }
            when {
                file.isFile && ReaderAssetBlobFileLayout.BLOB_FILE.matches(file.name) -> {
                    val id = file.nameWithoutExtension
                    val expectedBlob = expected[id]
                    if (expectedBlob != null) {
                        present += expectedBlob
                    } else if (file.lastModified() < staleBeforeEpochMillis) {
                        orphans += readerAssetBlobArtifactId(id)
                    }
                }
                file.isFile && ReaderAssetBlobFileLayout.TEMP_FILE.matches(file.name) &&
                    file.lastModified() < staleBeforeEpochMillis -> {
                    interrupted += readerAssetTempArtifactId(file.name)
                }
            }
        }
        ReaderAssetStorageInventorySnapshot(
            presentBlobIds = present,
            orphanArtifacts = orphans,
            interruptedWriteArtifacts = interrupted,
            scanComplete = scanComplete,
        )
    }

    override suspend fun deleteReaderAssetArtifacts(artifacts: List<StorageArtifactId>) {
        delete(artifacts)
    }

    private fun deleteArtifact(file: File) {
        if (file.exists() && !file.delete()) {
            error("Could not delete app-private storage artifact.")
        }
    }

    override fun canStore(payloadBytes: Long): Boolean {
        val available = availableBytes()
        return payloadBytes >= 0 &&
            available > reserveBytes &&
            payloadBytes <= available - reserveBytes
    }

    private fun artifactFile(artifact: StorageArtifactId): File? {
        readerAssetArtifactFile(artifact)?.let { return it }
        val parts = artifact.value.split(':', limit = ARTIFACT_PARTS)
        val namespace = parts.getOrNull(1)
            ?.let { runCatching { ChapterBlobNamespace.valueOf(it) }.getOrNull() }
        return if (parts.size != ARTIFACT_PARTS || namespace == null) {
            null
        } else {
            val directory = ChapterBlobFileLayout.namespaceDirectory(rootDirectory, namespace)
            when (parts[0]) {
                BLOB_ARTIFACT -> parts[2]
                    .takeIf(BLOB_STEM::matches)
                    ?.let { File(directory, "$it.blob") }
                TEMP_ARTIFACT -> parts[2]
                    .takeIf(TEMP_FILE::matches)
                    ?.let { File(directory, it) }
                else -> null
            }
        }
    }

    private fun readerAssetArtifactFile(artifact: StorageArtifactId): File? = when {
        artifact.value.startsWith("$READER_ASSET_BLOB_ARTIFACT:") ->
            artifact.value.removePrefix("$READER_ASSET_BLOB_ARTIFACT:")
                .takeIf(READER_ASSET_BLOB_STEM::matches)
                ?.let { File(readerAssetRootDirectory, "$it.blob") }
        artifact.value.startsWith("$READER_ASSET_TEMP_ARTIFACT:") ->
            artifact.value.removePrefix("$READER_ASSET_TEMP_ARTIFACT:")
                .takeIf(ReaderAssetBlobFileLayout.TEMP_FILE::matches)
                ?.let { File(readerAssetRootDirectory, it) }
        else -> null
    }

    private fun readerAssetBlobArtifactId(stem: String) =
        StorageArtifactId("$READER_ASSET_BLOB_ARTIFACT:$stem")

    private fun readerAssetTempArtifactId(name: String) =
        StorageArtifactId("$READER_ASSET_TEMP_ARTIFACT:$name")

    private fun blobArtifactId(key: ChapterBlobKey): StorageArtifactId = blobArtifactId(
        key.namespace,
        ChapterBlobFileLayout.blobStem(key),
    )

    private fun blobArtifactId(
        namespace: ChapterBlobNamespace,
        stem: String,
    ) = StorageArtifactId("$BLOB_ARTIFACT:${namespace.name}:$stem")

    private fun tempArtifactId(
        namespace: ChapterBlobNamespace,
        name: String,
    ) = StorageArtifactId("$TEMP_ARTIFACT:${namespace.name}:$name")

    private companion object {
        const val BLOB_ARTIFACT = "blob"
        const val TEMP_ARTIFACT = "temp"
        const val READER_ASSET_BLOB_ARTIFACT = "reader-asset-blob"
        const val READER_ASSET_TEMP_ARTIFACT = "reader-asset-temp"
        const val ARTIFACT_PARTS = 3
        const val DEFAULT_RESERVE_BYTES = 64L * 1024 * 1024
        val BLOB_STEM = Regex("[0-9a-f]{64}")
        val BLOB_FILE = Regex("[0-9a-f]{64}\\.blob")
        val TEMP_FILE = Regex("\\.stage-[A-Za-z0-9._-]+\\.tmp")
        val READER_ASSET_BLOB_STEM = Regex("[0-9a-f]{64}")
    }
}


internal object ChapterBlobFileLayout {
    const val ROOT_DIRECTORY_NAME = "chapter-blobs"

    fun root(context: Context): File = File(context.filesDir, ROOT_DIRECTORY_NAME)

    fun namespaceDirectory(root: File, namespace: ChapterBlobNamespace): File = File(
        root,
        when (namespace) {
            ChapterBlobNamespace.AUTOMATIC_CACHE -> "cache"
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD -> "downloads"
        },
    )

    fun blobFile(root: File, key: ChapterBlobKey): File {
        val target = File(namespaceDirectory(root, key.namespace), "${blobStem(key)}.blob")
        check(target.toPath().normalize().startsWith(root.toPath().normalize())) {
            "Blob path escaped its app-private root."
        }
        return target
    }

    fun blobStem(key: ChapterBlobKey): String = MessageDigest.getInstance("SHA-256")
        .digest("${key.releaseId.value}\u0000${key.contentFingerprint}".encodeToByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
