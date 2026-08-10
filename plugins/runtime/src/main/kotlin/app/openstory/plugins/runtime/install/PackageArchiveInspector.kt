package app.openstory.plugins.runtime.install

import app.openstory.plugins.api.manifest.PluginManifest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

private const val MIB = 1024 * 1024
private const val DEFAULT_MAX_COMPRESSED_BYTES = 8 * MIB
private const val DEFAULT_MAX_EXPANDED_BYTES = 32L * MIB
private const val BYTE_MASK = 0xff
private const val SHORT_BYTES = 2
private const val INT_BYTES = 4
private const val SHORT_SHIFT = 8
private const val INT_HIGH_SHIFT = 16
private const val UNSIGNED_INT_MASK = 0xffffffffL

data class InspectedPluginPackage(
    val manifest: PluginManifest,
    val entries: Map<String, ByteArray>,
)

class PackageArchiveInspector(
    private val maxCompressedBytes: Int = DEFAULT_MAX_COMPRESSED_BYTES,
    private val maxExpandedBytes: Long = DEFAULT_MAX_EXPANDED_BYTES,
    private val maxEntries: Int = 256,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun inspect(bytes: ByteArray): InspectedPluginPackage {
        require(bytes.size <= maxCompressedBytes) { "Archive exceeds compressed byte budget" }
        requireNoSymbolicLinks(bytes)
        val entries = linkedMapOf<String, ByteArray>()
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                require(entries.size < maxEntries) { "Archive has too many entries" }
                val name = normalizedEntryName(entry.name)
                require(!entry.isDirectory) { "Archive directories are not allowed" }
                require(name !in entries) { "Archive contains duplicate entries" }
                val content = readEntry(archive, maxExpandedBytes - expanded)
                expanded += content.size
                entries[name] = content
                archive.closeEntry()
                entry = archive.nextEntry
            }
        }
        require(LEGACY_SELECTOR_ENTRY !in entries) { "Selector packages are not supported" }
        require(entries.keys.all(::isAllowedEntry)) { "Archive contains an unsupported entry" }
        val manifestBytes = requireNotNull(entries[MANIFEST_ENTRY]) { "Archive is missing manifest.json" }
        require(entries.containsKey(SCRIPT_ENTRY)) { "Archive is missing main.js" }
        val manifest = json.decodeFromString(PluginManifest.serializer(), manifestBytes.decodeToString())
        require(manifest.entry == SCRIPT_ENTRY) { "Manifest entry does not match package layout" }
        return InspectedPluginPackage(manifest, entries.mapValues { it.value.copyOf() })
    }

    private fun readEntry(archive: ZipInputStream, remaining: Long): ByteArray {
        require(remaining >= 0) { "Archive exceeds expanded byte budget" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = archive.read(buffer)
            if (read == -1) break
            total += read
            require(total <= remaining) { "Archive exceeds expanded byte budget" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun normalizedEntryName(value: String): String {
        require(value.isNotBlank() && !value.startsWith('/') && !value.startsWith('\\')) {
            "Archive path is absolute or blank"
        }
        require(!value.contains('\\') && !value.contains('\u0000')) { "Archive path is invalid" }
        val segments = value.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) { "Archive path traverses" }
        return segments.joinToString("/")
    }

    private fun isAllowedEntry(name: String): Boolean =
        name == MANIFEST_ENTRY || name == SCRIPT_ENTRY || name.startsWith(ASSET_PREFIX)

    private fun requireNoSymbolicLinks(bytes: ByteArray) {
        val end = findEndOfCentralDirectory(bytes)
        val entryCount = bytes.readUnsignedShort(end + END_ENTRY_COUNT_OFFSET)
        var offset = bytes.readUnsignedInt(end + END_DIRECTORY_OFFSET).toInt()
        repeat(entryCount) {
            require(bytes.readUnsignedInt(offset) == CENTRAL_DIRECTORY_SIGNATURE) {
                "Archive central directory is invalid"
            }
            val madeBy = bytes.readUnsignedShort(offset + CENTRAL_VERSION_OFFSET)
            val platform = madeBy ushr PLATFORM_SHIFT
            val attributes = bytes.readUnsignedInt(offset + CENTRAL_ATTRIBUTES_OFFSET)
            val unixMode = (attributes ushr UNIX_MODE_SHIFT).toInt()
            require(platform != UNIX_PLATFORM || unixMode and FILE_TYPE_MASK != SYMBOLIC_LINK_TYPE) {
                "Archive symbolic links are not allowed"
            }
            val nameLength = bytes.readUnsignedShort(offset + CENTRAL_NAME_LENGTH_OFFSET)
            val extraLength = bytes.readUnsignedShort(offset + CENTRAL_EXTRA_LENGTH_OFFSET)
            val commentLength = bytes.readUnsignedShort(offset + CENTRAL_COMMENT_LENGTH_OFFSET)
            offset += CENTRAL_HEADER_LENGTH + nameLength + extraLength + commentLength
        }
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        val minimum = (bytes.size - MAX_END_RECORD_LENGTH).coerceAtLeast(0)
        for (offset in bytes.size - END_HEADER_LENGTH downTo minimum) {
            if (bytes.readUnsignedInt(offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) return offset
        }
        throw IllegalArgumentException("Archive central directory is missing")
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val SCRIPT_ENTRY = "main.js"
        const val LEGACY_SELECTOR_ENTRY = "selector.json"
        const val ASSET_PREFIX = "assets/"
        const val END_HEADER_LENGTH = 22
        const val MAX_END_RECORD_LENGTH = END_HEADER_LENGTH + 65_535
        const val END_ENTRY_COUNT_OFFSET = 10
        const val END_DIRECTORY_OFFSET = 16
        const val CENTRAL_HEADER_LENGTH = 46
        const val CENTRAL_VERSION_OFFSET = 4
        const val CENTRAL_NAME_LENGTH_OFFSET = 28
        const val CENTRAL_EXTRA_LENGTH_OFFSET = 30
        const val CENTRAL_COMMENT_LENGTH_OFFSET = 32
        const val CENTRAL_ATTRIBUTES_OFFSET = 38
        const val PLATFORM_SHIFT = 8
        const val UNIX_MODE_SHIFT = 16
        const val UNIX_PLATFORM = 3
        const val FILE_TYPE_MASK = 0xF000
        const val SYMBOLIC_LINK_TYPE = 0xA000
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    }
}

private fun ByteArray.readUnsignedShort(offset: Int): Int {
    require(offset >= 0 && offset + SHORT_BYTES <= size) { "Archive structure is truncated" }
    return this[offset].toInt() and BYTE_MASK or
        ((this[offset + 1].toInt() and BYTE_MASK) shl SHORT_SHIFT)
}

private fun ByteArray.readUnsignedInt(offset: Int): Long {
    require(offset >= 0 && offset + INT_BYTES <= size) { "Archive structure is truncated" }
    return (readUnsignedShort(offset).toLong() or
        (readUnsignedShort(offset + SHORT_BYTES).toLong() shl INT_HIGH_SHIFT)) and UNSIGNED_INT_MASK
}
