package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.cache.CacheService
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.reader.content.ReaderDocumentReadResult
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.routing.ReaderCacheFactsPort
import app.openstory.reader.routing.ReaderLocalCacheFact
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.document.isLocalPersistable
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlinx.coroutines.CancellationException

class DownloadAwareReaderDocumentStore(
    private val blobs: ChapterBlobStore,
    private val cacheRepository: CacheRepository,
    private val downloads: DownloadRepository,
    private val now: () -> Long,
    private val writeAdmission: StorageWriteAdmission = StorageWriteAdmission.ALLOW_ALL,
    private val cacheQuotaBytes: Long = DEFAULT_CACHE_QUOTA_BYTES,
    private val metadataSource: ReaderCacheMetadataSource = ReaderCacheMetadataSource { emptyList() },
) : ReaderDocumentStore, ReaderCacheFactsPort {
    private val cache = CacheService(cacheRepository, blobs)


    override suspend fun inspect(
        releaseIds: Set<ChapterReleaseId>,
        resumeFingerprints: Map<ChapterReleaseId, String>,
    ): Map<ChapterReleaseId, ReaderLocalCacheFact> = if (releaseIds.isEmpty()) {
        emptyMap()
    } else {
        require(resumeFingerprints.keys.all { it in releaseIds }) {
            "Reader resume fingerprints must belong to the inspected release set."
        }
        val metadata = readCacheMetadata(releaseIds)
        if (metadata == null) {
            releaseIds.associateWith { ReaderLocalCacheFact.Unknown }
        } else {
            val byRelease = metadata.asSequence()
                .filter { it.releaseId in releaseIds }
                .groupBy(ReaderCacheMetadata::releaseId)
            releaseIds.associateWith { releaseId ->
                selectCacheFact(byRelease[releaseId].orEmpty(), resumeFingerprints[releaseId])
            }
        }
    }

    private suspend fun readCacheMetadata(
        releaseIds: Set<ChapterReleaseId>,
    ): List<ReaderCacheMetadata>? = try {
        metadataSource.entriesFor(releaseIds)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun selectCacheFact(
        rows: List<ReaderCacheMetadata>,
        resumeFingerprint: String?,
    ): ReaderLocalCacheFact = if (resumeFingerprint != null) {
        if (rows.any { it.fingerprint == resumeFingerprint && it.checksumPresent }) {
            ReaderLocalCacheFact.Exact(resumeFingerprint)
        } else {
            ReaderLocalCacheFact.Miss
        }
    } else {
        selectBestStoredCacheFact(rows)
    }

    private fun selectBestStoredCacheFact(rows: List<ReaderCacheMetadata>): ReaderLocalCacheFact {
        val newestExplicit = rows.asSequence()
            .filter { it.namespace == ChapterBlobNamespace.EXPLICIT_DOWNLOAD }
            .sortedWith(
                compareByDescending<ReaderCacheMetadata> { it.updatedAtEpochMillis }
                    .thenBy { it.fingerprint },
            )
            .firstOrNull()
        val completedExplicit = newestExplicit?.takeIf { row ->
            row.downloadState == DownloadState.COMPLETED && row.checksumPresent
        }
        val automatic = rows.asSequence()
            .filter { it.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE && it.checksumPresent }
            .sortedWith(
                compareByDescending<ReaderCacheMetadata> { it.lastAccessedAtEpochMillis }
                    .thenBy { it.fingerprint },
            )
            .firstOrNull()
        return completedExplicit?.let { ReaderLocalCacheFact.Unverified(it.fingerprint) }
            ?: automatic?.let { ReaderLocalCacheFact.Unverified(it.fingerprint) }
            ?: ReaderLocalCacheFact.Miss
    }

    override suspend fun readResult(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): ReaderDocumentReadResult {
        var sawCorruption = false
        for (namespace in LOCAL_READ_ORDER) {
            when (val result = readPhysical(namespace, releaseId, fingerprint)) {
                is PhysicalRead.Hit -> return ReaderDocumentReadResult.Hit(result.document)
                PhysicalRead.Missing -> Unit
                PhysicalRead.Corrupt -> sawCorruption = true
            }
        }
        return if (sawCorruption) {
            ReaderDocumentReadResult.FingerprintOrDecodeMismatch
        } else {
            ReaderDocumentReadResult.Missing
        }
    }

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? =
        when (val result = readResult(releaseId, fingerprint)) {
            is ReaderDocumentReadResult.Hit -> result.document
            ReaderDocumentReadResult.Missing,
            ReaderDocumentReadResult.FingerprintOrDecodeMismatch,
            -> null
        }

    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? {
        val record = downloads.find(releaseId)?.takeIf { it.state == DownloadState.COMPLETED } ?: return null
        return when (
            val result = readPhysical(
                ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
                releaseId,
                record.key.contentFingerprint,
            )
        ) {
            is PhysicalRead.Hit -> result.document
            PhysicalRead.Missing,
            PhysicalRead.Corrupt,
            -> null
        }
    }

    private suspend fun readPhysical(
        namespace: ChapterBlobNamespace,
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): PhysicalRead {
        val key = ChapterBlobKey(namespace, releaseId, fingerprint)
        val blob = blobs.read(key) ?: return PhysicalRead.Missing
        val document = ReaderDocumentBlobCodec.decode(blob)
        if (document == null || document.fingerprint != fingerprint) {
            deleteCorruptBestEffort(key)
            return PhysicalRead.Corrupt
        }
        touchBestEffort(key)
        return PhysicalRead.Hit(document)
    }

    private suspend fun deleteCorruptBestEffort(key: ChapterBlobKey) {
        try {
            blobs.delete(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Corruption is already proven by the bytes; cleanup failure cannot erase that fact.
        }
    }

    private suspend fun touchBestEffort(key: ChapterBlobKey) {
        try {
            cacheRepository.touch(key, now())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Access timestamps are best effort and must not invalidate verified content.
        }
    }

    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) {
        require(document.fingerprint == fingerprint)
        val blob = ReaderDocumentBlobCodec.encode(document)
        if (!writeAdmission.canStore(blob.sizeBytes.toLong())) return
        try {
            cache.store(
                ChapterBlobKey(ChapterBlobNamespace.AUTOMATIC_CACHE, releaseId, fingerprint),
                blob,
                now(),
            )
            cache.enforceQuota(cacheQuotaBytes, emptySet())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Cache persistence is best effort; reconciliation cleans partial metadata or blobs.
        }
    }

    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) {
        ChapterBlobNamespace.entries.forEach { namespace ->
            blobs.delete(ChapterBlobKey(namespace, releaseId, fingerprint))
        }
    }

    private companion object {
        val LOCAL_READ_ORDER = listOf(
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
            ChapterBlobNamespace.AUTOMATIC_CACHE,
        )
        const val DEFAULT_CACHE_QUOTA_BYTES = 256L * 1024 * 1024
    }

    private sealed interface PhysicalRead {
        data class Hit(val document: ReaderDocument) : PhysicalRead
        data object Missing : PhysicalRead
        data object Corrupt : PhysicalRead
    }
}

internal object ReaderDocumentBlobCodec {
    fun encode(document: ReaderDocument): ChapterBlob {
        require(document.isLocalPersistable) { "Remote image documents cannot be stored as chapter blobs" }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(FORMAT_VERSION)
            data.writeNullableString(document.title)
            data.writeString(document.fingerprint)
            data.writeInt(document.blocks.size)
            document.blocks.forEach { block -> data.writeBlock(block) }
        }
        return ChapterBlob.fromBytes(output.toByteArray())
    }

    fun decode(blob: ChapterBlob): ReaderDocument? = runCatching {
        DataInputStream(blob.inputStream()).use { data ->
            require(data.readInt() == FORMAT_VERSION)
            val title = data.readNullableString()
            val fingerprint = data.readString()
            val count = data.readInt().also { require(it in 0..MAX_BLOCKS) }
            ReaderDocument(title, List(count) { data.readBlock() }, fingerprint)
        }
    }.getOrNull()

    private fun DataOutputStream.writeBlock(block: ReaderBlock) {
        when (block) {
            is ReaderBlock.Paragraph -> {
                writeByte(PARAGRAPH_TAG)
                writeString(block.id)
                writeString(block.text)
            }
            is ReaderBlock.Heading -> {
                writeByte(HEADING_TAG)
                writeString(block.id)
                writeInt(block.level)
                writeString(block.text)
            }
            is ReaderBlock.Divider -> {
                writeByte(DIVIDER_TAG)
                writeString(block.id)
            }
            is ReaderBlock.Note -> {
                writeByte(NOTE_TAG)
                writeString(block.id)
                writeString(block.text)
            }
            is ReaderBlock.ImagePage -> error("Remote image pages are not persistable")
        }
    }

    private fun DataInputStream.readBlock(): ReaderBlock = when (readByte().toInt()) {
        PARAGRAPH_TAG -> ReaderBlock.Paragraph(readString(), readString())
        HEADING_TAG -> ReaderBlock.Heading(readString(), readInt(), readString())
        DIVIDER_TAG -> ReaderBlock.Divider(readString())
        NOTE_TAG -> ReaderBlock.Note(readString(), readString())
        else -> error("Unknown reader block type.")
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readString() else null

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES)
        return ByteArray(size).also(::readFully).decodeToString()
    }

    private const val FORMAT_VERSION = 1
    private const val PARAGRAPH_TAG = 1
    private const val HEADING_TAG = 2
    private const val DIVIDER_TAG = 3
    private const val NOTE_TAG = 4
    private const val MAX_BLOCKS = 100_000
    private const val MAX_STRING_BYTES = 16 * 1024 * 1024
}
