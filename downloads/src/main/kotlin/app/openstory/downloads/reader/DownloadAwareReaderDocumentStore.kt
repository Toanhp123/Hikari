package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.cache.CacheService
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class DownloadAwareReaderDocumentStore(
    private val blobs: ChapterBlobStore,
    private val cacheRepository: CacheRepository,
    private val now: () -> Long,
) : ReaderDocumentStore {
    private val cache = CacheService(cacheRepository, blobs)

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? {
        for (namespace in listOf(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, ChapterBlobNamespace.AUTOMATIC_CACHE)) {
            val key = ChapterBlobKey(namespace, releaseId, fingerprint)
            val blob = blobs.read(key) ?: continue
            val document = ReaderDocumentBlobCodec.decode(blob)
            if (document == null || document.fingerprint != fingerprint) {
                blobs.delete(key)
                continue
            }
            cacheRepository.touch(key, now())
            return document
        }
        return null
    }

    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) {
        require(document.fingerprint == fingerprint)
        cache.store(
            ChapterBlobKey(ChapterBlobNamespace.AUTOMATIC_CACHE, releaseId, fingerprint),
            ReaderDocumentBlobCodec.encode(document),
            now(),
        )
    }

    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) {
        ChapterBlobNamespace.entries.forEach { namespace ->
            blobs.delete(ChapterBlobKey(namespace, releaseId, fingerprint))
        }
    }
}

internal object ReaderDocumentBlobCodec {
    fun encode(document: ReaderDocument): ChapterBlob {
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
        DataInputStream(ByteArrayInputStream(blob.bytes())).use { data ->
            require(data.readInt() == FORMAT_VERSION)
            val title = data.readNullableString()
            val fingerprint = data.readString()
            val count = data.readInt().also { require(it in 0..MAX_BLOCKS) }
            ReaderDocument(title, List(count) { data.readBlock() }, fingerprint)
        }
    }.getOrNull()

    private fun DataOutputStream.writeBlock(block: ReaderBlock) {
        when (block) {
            is ReaderBlock.Paragraph -> { writeByte(1); writeString(block.id); writeString(block.text) }
            is ReaderBlock.Heading -> { writeByte(2); writeString(block.id); writeInt(block.level); writeString(block.text) }
            is ReaderBlock.Divider -> { writeByte(3); writeString(block.id) }
            is ReaderBlock.Note -> { writeByte(4); writeString(block.id); writeString(block.text) }
        }
    }

    private fun DataInputStream.readBlock(): ReaderBlock = when (readByte().toInt()) {
        1 -> ReaderBlock.Paragraph(readString(), readString())
        2 -> ReaderBlock.Heading(readString(), readInt(), readString())
        3 -> ReaderBlock.Divider(readString())
        4 -> ReaderBlock.Note(readString(), readString())
        else -> error("Unknown reader block type.")
    }

    private fun DataOutputStream.writeNullableString(value: String?) { writeBoolean(value != null); if (value != null) writeString(value) }
    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null
    private fun DataOutputStream.writeString(value: String) { val bytes = value.encodeToByteArray(); writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.readString(): String { val size = readInt(); require(size in 0..MAX_STRING_BYTES); return ByteArray(size).also(::readFully).decodeToString() }

    private const val FORMAT_VERSION = 1
    private const val MAX_BLOCKS = 100_000
    private const val MAX_STRING_BYTES = 16 * 1024 * 1024
}
