package app.openstory.plugins.runtime.install

import app.openstory.plugins.api.manifest.NetworkCapability
import app.openstory.plugins.api.manifest.PluginCapabilities
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginProtocolVersion
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PackageVerifierTest {
    @Test
    fun checksumMismatchIsRejectedBeforeArchiveParsing() {
        val bytes = packageBytes()
        val result = PackageVerifier().verify(bytes, artifact(sha = "0".repeat(64)))
        assertEquals("plugin.package_checksum_mismatch", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun selectorEntryIsRejected() {
        val bytes = packageBytes(mapOf("selector.json" to "{}".encodeToByteArray()))
        val result = PackageVerifier().verify(bytes, artifact(sha = sha256(bytes)))
        assertEquals("plugin.package_layout_invalid", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun traversalEntryIsRejected() {
        val bytes = packageBytes(mapOf("assets/../secret" to byteArrayOf(1)))
        val result = PackageVerifier().verify(bytes, artifact(sha = sha256(bytes)))
        assertEquals("plugin.package_layout_invalid", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun symbolicLinkEntryIsRejected() {
        val bytes = packageBytes(mapOf("assets/link" to byteArrayOf(1))).markAsSymbolicLink("assets/link")
        val result = PackageVerifier().verify(bytes, artifact(sha = sha256(bytes)))
        assertEquals("plugin.package_layout_invalid", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun duplicateEntryNameIsRejected() {
        val bytes = packageBytes(
            mapOf("assets/x" to byteArrayOf(1), "assets/y" to byteArrayOf(2)),
        ).replaceAscii("assets/y", "assets/x")
        val result = PackageVerifier().verify(bytes, artifact(sha = sha256(bytes)))
        assertEquals("plugin.package_layout_invalid", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun excessiveEntryCountIsRejected() {
        val bytes = packageBytes(mapOf("assets/value" to byteArrayOf(1)))
        val verifier = PackageVerifier(PackageArchiveInspector(maxEntries = 2))
        val result = verifier.verify(bytes, artifact(sha = sha256(bytes)))
        assertEquals("plugin.package_layout_invalid", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun excessiveExpandedBytesAreRejected() {
        val bytes = packageBytes()
        val verifier = PackageVerifier(PackageArchiveInspector(maxExpandedBytes = 1))
        val result = verifier.verify(bytes, artifact(sha = sha256(bytes)))
        assertEquals("plugin.package_layout_invalid", assertIs<PluginCallResult.Failure>(result).code)
    }

    private fun artifact(sha: String) = PluginArtifact(
        "org.example.plugin",
        "1.0.0",
        "https://plugins.example/plugin.osp",
        sha,
    )
}

private fun ByteArray.markAsSymbolicLink(entryName: String): ByteArray = copyOf().also { bytes ->
    var offset = 0
    while (offset <= bytes.size - 46) {
        if (bytes.readLittleEndianInt(offset) == 0x02014B50L) {
            val nameLength = bytes.readLittleEndianShort(offset + 28)
            val extraLength = bytes.readLittleEndianShort(offset + 30)
            val commentLength = bytes.readLittleEndianShort(offset + 32)
            val name = bytes.decodeToString(offset + 46, offset + 46 + nameLength)
            if (name == entryName) {
                bytes[offset + 5] = 3
                bytes[offset + 40] = 0
                bytes[offset + 41] = 0xA0.toByte()
                return@also
            }
            offset += 46 + nameLength + extraLength + commentLength
        } else {
            offset++
        }
    }
}

private fun ByteArray.replaceAscii(source: String, replacement: String): ByteArray {
    require(source.length == replacement.length)
    val result = copyOf()
    val sourceBytes = source.encodeToByteArray()
    val replacementBytes = replacement.encodeToByteArray()
    for (offset in 0..result.size - sourceBytes.size) {
        if (result.copyOfRange(offset, offset + sourceBytes.size).contentEquals(sourceBytes)) {
            replacementBytes.copyInto(result, offset)
        }
    }
    return result
}

private fun ByteArray.readLittleEndianShort(offset: Int): Int =
    this[offset].toInt() and 0xff or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.readLittleEndianInt(offset: Int): Long =
    readLittleEndianShort(offset).toLong() or (readLittleEndianShort(offset + 2).toLong() shl 16)

internal fun packageBytes(extra: Map<String, ByteArray> = emptyMap(), version: String = "1.0.0"): ByteArray {
    val manifest = PluginManifest(
        id = "org.example.plugin",
        name = "Example",
        version = version,
        protocol = PluginProtocolVersion(1),
        provides = setOf(PluginService.CATALOG),
        capabilities = PluginCapabilities(NetworkCapability(setOf("api.example.com"))),
    )
    val entries = linkedMapOf(
        "manifest.json" to Json.encodeToString(manifest).encodeToByteArray(),
        "main.js" to "globalThis.openstoryPlugin = {};".encodeToByteArray(),
    ) + extra
    return ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }
}
