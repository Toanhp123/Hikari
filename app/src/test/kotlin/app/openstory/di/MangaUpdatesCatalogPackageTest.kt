package app.openstory.di

import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.PackageVerifier
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MangaUpdatesCatalogPackageTest {
    @Test
    fun bundledMangaUpdatesAssetIsPinnedCanonicalAndPassesPackageValidation() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val descriptor = BundledPlugins.descriptors.single { it.pluginId == MANGAUPDATES_PLUGIN_ID }
        val actualSha256 = packageBytes.sha256()

        assertContentEquals(canonicalPackageBytes(), packageBytes)
        assertEquals(ASSET_PATH, descriptor.assetPath)
        assertEquals(MANGAUPDATES_PLUGIN_VERSION, descriptor.version)
        assertEquals(actualSha256, descriptor.sha256)
        assertIs<PluginCallResult.Success<*>>(
            PackageVerifier().verify(
                bytes = packageBytes,
                artifactProvenance = PluginArtifact(
                    pluginId = descriptor.pluginId,
                    version = descriptor.version,
                    downloadUrl = "https://bundled.openstory.app/mangaupdates-catalog.osp",
                    sha256 = descriptor.sha256,
                ),
            ),
        )
    }

    @Test
    fun packageContainsOnlyManifestAndScriptWithExplicitCatalogOperations() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val entries = packageEntries(packageBytes)
        val manifestSource = entries.getValue("manifest.json")
        val mainSource = entries.getValue("main.js")
        val manifest = Json.decodeFromString<PluginManifest>(manifestSource)

        assertEquals(setOf("manifest.json", "main.js"), entries.keys)
        assertEquals(MANGAUPDATES_PLUGIN_ID, manifest.id)
        assertEquals(MANGAUPDATES_PLUGIN_VERSION, manifest.version)
        assertEquals(setOf(PluginService.CATALOG), manifest.provides)
        assertEquals(
            setOf(
                PluginOperation.CATALOG_HOME,
                PluginOperation.CATALOG_SEARCH,
                PluginOperation.CATALOG_DETAILS,
                PluginOperation.CATALOG_FILTERS,
            ),
            manifest.operations,
        )
        assertEquals(
            setOf(
                "api.mangaupdates.com",
                "cdn.mangaupdates.com",
                "mangaupdates.com",
                "www.mangaupdates.com",
            ),
            manifest.capabilities.network?.hosts,
        )
        assertEquals(
            Files.readString(repositoryFile(MANIFEST_RELATIVE_PATH)).normalizedLineEndings(),
            manifestSource.normalizedLineEndings(),
        )
        assertEquals(
            Files.readString(repositoryFile(MAIN_JS_RELATIVE_PATH)).normalizedLineEndings(),
            mainSource.normalizedLineEndings(),
        )
        assertTrue(mainSource.contains("https://api.mangaupdates.com"))
        assertTrue(mainSource.contains("/v1/series/search"))
        assertTrue(mainSource.contains("catalog: Object.freeze"))
        assertTrue(mainSource.contains("search: async"))
        assertTrue(mainSource.contains("details: async"))
        assertFalse(mainSource.contains("Authorization"))
        assertFalse(mainSource.contains("Cookie"))
        assertFalse("packageChecksumSha256" in manifestSource)
        assertFalse("\"runtime\"" in manifestSource)
    }

    private fun canonicalPackageBytes(): ByteArray {
        val manifest = repositoryFile(MANIFEST_RELATIVE_PATH).canonicalTextBytes()
        val script = repositoryFile(MAIN_JS_RELATIVE_PATH).canonicalTextBytes()
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { archive ->
                listOf("manifest.json" to manifest, "main.js" to script).forEach { (name, bytes) ->
                    archive.putNextEntry(ZipEntry(name).apply { time = 0L })
                    archive.write(bytes)
                    archive.closeEntry()
                }
            }
            output.toByteArray()
        }
    }
}

private fun packageEntries(packageBytes: ByteArray): Map<String, String> {
    val entries = linkedMapOf<String, String>()
    ZipInputStream(packageBytes.inputStream()).use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            entries[entry.name] = archive.readBytes().decodeToString()
            archive.closeEntry()
            entry = archive.nextEntry
        }
    }
    return entries
}

private fun repositoryFile(relativePath: String): Path {
    val relative = Path.of(relativePath)
    val userDir = Path.of(System.getProperty("user.dir"))
    val candidates = listOf(
        userDir.resolve(relative),
        userDir.resolve("..").resolve(relative).normalize(),
        userDir.resolve("../..").resolve(relative).normalize(),
    )
    return checkNotNull(candidates.firstOrNull(Files::isRegularFile)) { "Missing repository fixture: $relativePath" }
}

private fun Path.canonicalTextBytes(): ByteArray = Files.readString(this)
    .normalizedLineEndings()
    .toByteArray(Charsets.UTF_8)

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.normalizedLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

private const val MANGAUPDATES_PLUGIN_ID = "org.openstory.catalog.mangaupdates"
private const val MANGAUPDATES_PLUGIN_VERSION = "1.1.4"
private const val ASSET_PATH = "plugins/mangaupdates-catalog.osp"
private const val ASSET_RELATIVE_PATH = "app/src/main/assets/plugins/mangaupdates-catalog.osp"
private const val MANIFEST_RELATIVE_PATH = "bundled-plugins/mangaupdates-catalog/manifest.json"
private const val MAIN_JS_RELATIVE_PATH = "bundled-plugins/mangaupdates-catalog/main.js"
