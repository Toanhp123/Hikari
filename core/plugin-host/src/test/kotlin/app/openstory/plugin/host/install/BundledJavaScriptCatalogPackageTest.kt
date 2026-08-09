package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledJavaScriptCatalogPackageTest {
    @Test
    fun bundledJavaScriptAssetPassesPackageValidation() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val actualSha256 = packageBytes.sha256()

        assertEquals(JavaScriptCatalogBundledPlugin.PACKAGE_SHA_256, actualSha256)

        val result = PackageVerifier(
            archiveInspector = ZipPackageArchiveInspector(),
        ).verify(
            InstallRequest(
                packageBytes = packageBytes,
                metadata = PluginPackageMetadata(
                    pluginId = JavaScriptCatalogBundledPlugin.PLUGIN_ID,
                    version = JavaScriptCatalogBundledPlugin.VERSION,
                    exactPackageSha256 = actualSha256,
                    signature = null,
                ),
                provenance = PackageInstallProvenance(
                    source = PackageInstallSource.LOCAL_FILE,
                    sourceReference = "asset://${JavaScriptCatalogBundledPlugin.ASSET_PATH}",
                    signatureState = PackageSignatureState.UNSIGNED,
                    unsignedWarningAcknowledged = true,
                ),
                acceptedCapabilities = setOf(PluginCapability.NETWORK),
            ),
        )

        assertIs<AppResult.Success<VerifiedPluginPackage>>(result)
    }

    @Test
    fun bundledAssetContainsExactManifestAndJavaScriptSource() {
        val packageBytes = Files.readAllBytes(repositoryFile(ASSET_RELATIVE_PATH))
        val entries = mutableMapOf<String, String>()

        ZipInputStream(packageBytes.inputStream()).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json" || entry.name == "main.js") {
                    entries[entry.name] = archive.readBytes().decodeToString()
                }
                archive.closeEntry()
                entry = archive.nextEntry
            }
        }

        assertEquals(
            Files.readString(repositoryFile(MANIFEST_RELATIVE_PATH)).normalizedLineEndings(),
            entries["manifest.json"]?.normalizedLineEndings(),
        )
        assertEquals(
            Files.readString(repositoryFile(MAIN_JS_RELATIVE_PATH)).normalizedLineEndings(),
            entries["main.js"]?.normalizedLineEndings(),
        )
        assertTrue(entries.getValue("main.js").contains("globalThis.openstoryPlugin"))
    }
}

private fun repositoryFile(relativePath: String): Path {
    val relative = Path.of(relativePath)
    val userDir = Path.of(System.getProperty("user.dir"))
    val candidates = listOf(
        userDir.resolve(relative),
        userDir.resolve("../..").resolve(relative).normalize(),
    )
    return checkNotNull(candidates.firstOrNull(Files::isRegularFile)) {
        "Missing repository fixture: $relativePath"
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private const val ASSET_RELATIVE_PATH = "app/src/main/assets/plugins/javascript-catalog.osp"
private const val MANIFEST_RELATIVE_PATH = "bundled-plugins/javascript-catalog/manifest.json"
private const val MAIN_JS_RELATIVE_PATH = "bundled-plugins/javascript-catalog/main.js"

private fun String.normalizedLineEndings(): String =
    replace("\r\n", "\n").replace('\r', '\n')
