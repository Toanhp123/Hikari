package app.openstory.di

import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.packageformat.PluginArtifact
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.install.PackageVerifier
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MangaDexContentPackageTest {
    @Test
    fun bundledMangaDexAssetIsPinnedAndPassesPackageValidation() {
        val packageBytes = Files.readAllBytes(mangaDexRepositoryFile(MANGADEX_ASSET_RELATIVE_PATH))
        val descriptor = BundledPlugins.descriptors.single { it.pluginId == MANGADEX_PACKAGE_PLUGIN_ID }
        val actualSha256 = packageBytes.mangaDexSha256()

        assertEquals(MANGADEX_ASSET_PATH, descriptor.assetPath)
        assertEquals(MANGADEX_PACKAGE_VERSION, descriptor.version)
        assertEquals(actualSha256, descriptor.sha256)
        assertIs<PluginCallResult.Success<*>>(
            PackageVerifier().verify(
                bytes = packageBytes,
                artifactProvenance = PluginArtifact(
                    pluginId = descriptor.pluginId,
                    version = descriptor.version,
                    downloadUrl = "https://bundled.openstory.app/mangadex-content.osp",
                    sha256 = descriptor.sha256,
                ),
            ),
        )
    }

    @Test
    fun bundledSourcePackagePassesPackageValidation() {
        val packageBytes = mangaDexPackageBytesForUnitTest()
        val sha256 = packageBytes.mangaDexSha256()
        val result = PackageVerifier().verify(
            bytes = packageBytes,
            artifactProvenance = PluginArtifact(
                pluginId = MANGADEX_PACKAGE_PLUGIN_ID,
                version = MANGADEX_PACKAGE_VERSION,
                downloadUrl = "https://bundled.openstory.app/mangadex-content.osp",
                sha256 = sha256,
            ),
        )

        assertIs<PluginCallResult.Success<*>>(result)
    }

    @Test
    fun manifestAndScriptExposeOnlyWave06ContentOperations() {
        val manifestSource = Files.readString(mangaDexRepositoryFile(MANGADEX_MANIFEST_RELATIVE_PATH))
        val mainSource = Files.readString(mangaDexRepositoryFile(MANGADEX_MAIN_JS_RELATIVE_PATH))
        val manifest = Json.decodeFromString<PluginManifest>(manifestSource)

        assertEquals(MANGADEX_PACKAGE_PLUGIN_ID, manifest.id)
        assertEquals(MANGADEX_PACKAGE_VERSION, manifest.version)
        assertEquals(setOf(PluginService.CONTENT), manifest.provides)
        assertEquals(setOf("api.mangadex.org", "mangadex.org"), manifest.capabilities.network?.hosts)
        assertTrue(mainSource.contains("content: Object.freeze"))
        assertTrue(mainSource.contains("search: async"))
        assertTrue(mainSource.contains("resolveUrl: async"))
        assertTrue(mainSource.contains("https://api.mangadex.org"))
        assertFalse(mainSource.contains("chapters: async"))
        assertFalse(mainSource.contains("chapter: async"))
        assertFalse(mainSource.contains("Authorization"))
        assertFalse(mainSource.contains("Cookie"))
    }

    private fun mangaDexPackageBytesForUnitTest(): ByteArray {
        val manifest = Files.readAllBytes(mangaDexRepositoryFile(MANGADEX_MANIFEST_RELATIVE_PATH))
        val script = Files.readAllBytes(mangaDexRepositoryFile(MANGADEX_MAIN_JS_RELATIVE_PATH))
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

private fun mangaDexRepositoryFile(relativePath: String): Path {
    val relative = Path.of(relativePath)
    val userDir = Path.of(System.getProperty("user.dir"))
    val candidates = listOf(
        userDir.resolve(relative),
        userDir.resolve("..").resolve(relative).normalize(),
        userDir.resolve("../..").resolve(relative).normalize(),
    )
    return checkNotNull(candidates.firstOrNull(Files::isRegularFile)) { "Missing repository fixture: $relativePath" }
}

private fun ByteArray.mangaDexSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private const val MANGADEX_PACKAGE_PLUGIN_ID = "org.openstory.content.mangadex"
private const val MANGADEX_PACKAGE_VERSION = "1.0.0"
private const val MANGADEX_ASSET_PATH = "plugins/mangadex-content.osp"
private const val MANGADEX_ASSET_RELATIVE_PATH = "app/src/main/assets/plugins/mangadex-content.osp"
private const val MANGADEX_MANIFEST_RELATIVE_PATH = "bundled-plugins/mangadex-content/manifest.json"
private const val MANGADEX_MAIN_JS_RELATIVE_PATH = "bundled-plugins/mangadex-content/main.js"
