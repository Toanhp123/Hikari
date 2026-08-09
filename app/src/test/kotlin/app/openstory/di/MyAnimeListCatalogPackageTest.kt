package app.openstory.di

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.install.InstallRequest
import app.openstory.plugin.host.install.PackageVerifier
import app.openstory.plugin.host.install.VerifiedPluginPackage
import app.openstory.plugin.host.install.ZipPackageArchiveInspector
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class MyAnimeListCatalogPackageTest {
    @Test
    fun bundledMyAnimeListAssetPassesPackageValidation() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val actualSha256 = packageBytes.sha256()

        assertEquals(MyAnimeListCatalogBundledPlugin.PACKAGE_SHA_256, actualSha256)

        val result = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(
            InstallRequest(
                packageBytes = packageBytes,
                metadata = PluginPackageMetadata(
                    pluginId = MyAnimeListCatalogBundledPlugin.PLUGIN_ID,
                    version = MyAnimeListCatalogBundledPlugin.VERSION,
                    exactPackageSha256 = actualSha256,
                    signature = null,
                ),
                provenance = PackageInstallProvenance(
                    source = PackageInstallSource.LOCAL_FILE,
                    sourceReference = "asset://${MyAnimeListCatalogBundledPlugin.ASSET_PATH}",
                    signatureState = PackageSignatureState.UNSIGNED,
                    unsignedWarningAcknowledged = true,
                ),
                acceptedCapabilities = setOf(PluginCapability.NETWORK),
            ),
        )

        assertIs<AppResult.Success<VerifiedPluginPackage>>(result)
    }

    @Test
    fun packageUsesDirectMyAnimeListApiWithoutBundledCredentials() {
        val entries = packageEntries(Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH)))
        val manifestSource = entries.getValue("manifest.json")
        val mainSource = entries.getValue("main.js")
        val manifest = Json.decodeFromString(PluginManifest.serializer(), manifestSource)

        assertEquals(MyAnimeListCatalogBundledPlugin.PLUGIN_ID, manifest.id)
        assertEquals(PluginRuntime.JAVASCRIPT, manifest.runtime)
        assertEquals(
            setOf("api.myanimelist.net", "cdn.myanimelist.net", "myanimelist.net"),
            manifest.allowedHosts,
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
        assertTrue(mainSource.contains("/v2/manga/"))
        assertFalse(mainSource.contains("X-MAL-CLIENT-ID"))
        assertFalse(mainSource.contains("client_secret"))
        assertFalse(mainSource.contains("api.jikan.moe"))
    }
}

private fun packageEntries(packageBytes: ByteArray): Map<String, String> {
    val entries = mutableMapOf<String, String>()
    ZipInputStream(packageBytes.inputStream()).use { archive ->
        var entry = archive.nextEntry
        while (entry != null) {
            if (entry.name in setOf("manifest.json", "main.js")) {
                entries[entry.name] = archive.readBytes().decodeToString()
            }
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
    return checkNotNull(candidates.firstOrNull(Files::isRegularFile)) {
        "Missing repository fixture: $relativePath"
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.normalizedLineEndings(): String =
    replace("\r\n", "\n").replace('\r', '\n')

private const val ASSET_RELATIVE_PATH = "app/src/main/assets/plugins/myanimelist-catalog.osp"
private const val MANIFEST_RELATIVE_PATH = "bundled-plugins/myanimelist-catalog/manifest.json"
private const val MAIN_JS_RELATIVE_PATH = "bundled-plugins/myanimelist-catalog/main.js"
