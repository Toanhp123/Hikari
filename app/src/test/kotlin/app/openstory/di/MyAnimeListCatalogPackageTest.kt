package app.openstory.di

import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.PackageVerifier
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MyAnimeListCatalogPackageTest {
    @Test
    fun bundledMyAnimeListAssetPassesVnextPackageValidation() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val descriptor = BundledPlugins.descriptors.single { it.pluginId == MYANIMELIST_PLUGIN_ID }
        val actualSha256 = packageBytes.sha256()

        assertEquals(descriptor.sha256, actualSha256)
        val result = PackageVerifier().verify(
            bytes = packageBytes,
            artifactProvenance = PluginArtifact(
                pluginId = descriptor.pluginId,
                version = descriptor.version,
                downloadUrl = "https://bundled.openstory.app/myanimelist-catalog.osp",
                sha256 = actualSha256,
            ),
        )

        assertIs<PluginCallResult.Success<*>>(result)
    }

    @Test
    fun packageContainsOnlyManifestAndScriptWithoutCredentials() {
        val entries = packageEntries(Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH)))
        val manifestSource = entries.getValue("manifest.json")
        val mainSource = entries.getValue("main.js")
        val manifest = Json.decodeFromString<PluginManifest>(manifestSource)

        assertEquals(setOf("manifest.json", "main.js"), entries.keys)
        assertEquals(MYANIMELIST_PLUGIN_ID, manifest.id)
        assertEquals(MYANIMELIST_PLUGIN_VERSION, manifest.version)
        assertEquals(setOf(PluginService.CATALOG), manifest.provides)
        assertEquals(
            setOf("api.myanimelist.net", "cdn.myanimelist.net", "myanimelist.net"),
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
        assertTrue(mainSource.contains("/v2/manga/ranking"))
        assertFalse(mainSource.contains("X-MAL-CLIENT-ID"))
        assertFalse(mainSource.contains("client_secret"))
        assertFalse("packageChecksumSha256" in manifestSource)
        assertFalse("\"runtime\"" in manifestSource)
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

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.normalizedLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

private const val ASSET_RELATIVE_PATH = "app/src/main/assets/plugins/myanimelist-catalog.osp"
private const val MANIFEST_RELATIVE_PATH = "bundled-plugins/myanimelist-catalog/manifest.json"
private const val MAIN_JS_RELATIVE_PATH = "bundled-plugins/myanimelist-catalog/main.js"
private const val MYANIMELIST_PLUGIN_ID = "org.openstory.catalog.myanimelist"
private const val MYANIMELIST_PLUGIN_VERSION = "2.0.0"
