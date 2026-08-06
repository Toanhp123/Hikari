package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZipPackageArchiveInspectorTest {

    @Test
    fun validPackageIsInspectedFromManifestAndArchiveEntries() {
        val result =
            ZipPackageArchiveInspector()
                .inspect(
                    validPluginPackage(),
                )

        val success =
            assertIs<AppResult.Success<*>>(
                result,
            )

        val inspection =
            assertIs<PluginPackageInspection>(
                success.value,
            )

        assertEquals(
            expected =
                "community.fixture",
            actual =
                inspection.pluginId,
        )

        assertEquals(
            expected =
                "1.0.0",
            actual =
                inspection.version,
        )

        assertEquals(
            expected =
                listOf(
                    "manifest.json",
                    "main.js",
                ),
            actual =
                inspection.entries.map {
                    it.path
                },
        )

        assertEquals(
            expected =
                setOf(
                    "main.js",
                ),
            actual =
                inspection
                    .declaredExecutableEntries,
        )

        assertEquals(
            expected =
                setOf(
                    PluginCapability.NETWORK,
                ),
            actual =
                inspection
                    .declaredCapabilities,
        )
    }
}

private fun validPluginPackage():
    ByteArray {
    val output =
        ByteArrayOutputStream()

    ZipOutputStream(output).use { archive ->
        archive.writeInspectorEntry(
            path =
                "manifest.json",
            content =
                VALID_MANIFEST,
        )

        archive.writeInspectorEntry(
            path =
                "main.js",
            content =
                "export default {}",
        )
    }

    return output.toByteArray()
}

private fun ZipOutputStream.writeInspectorEntry(
    path: String,
    content: String,
) {
    putNextEntry(
        ZipEntry(
            path,
        ),
    )

    write(
        content.encodeToByteArray(),
    )

    closeEntry()
}

private val VALID_MANIFEST =
    """
    {
      "id": "community.fixture",
      "name": "Fixture Plugin",
      "version": "1.0.0",
      "packageChecksumSha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "minimumHostVersion": "1.0.0",
      "updateUrl": "https://plugins.example.com/community.fixture.json",
      "api": {
        "major": 1,
        "minor": 0
      },
      "kinds": [
        "CONTENT"
      ],
      "languages": [
        "en"
      ],
      "allowedHosts": [
        "example.com"
      ],
      "capabilities": [
        "NETWORK"
      ],
      "runtime": "JAVASCRIPT",
      "entry": "main.js"
    }
    """.trimIndent()
