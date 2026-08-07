package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertIs

class ZipPackageArchiveInspectorSelectorValidationTest {
    @Test
    fun validVersionTwoSelectorIsAcceptedBeforeInstallation() {
        val result = ZipPackageArchiveInspector().inspect(
            selectorPackage(VALID_SELECTOR_V2),
        )

        assertIs<AppResult.Success<*>>(result)
    }

    @Test
    fun unsupportedSelectorSchemaFailsBeforeInstallation() {
        val result = ZipPackageArchiveInspector().inspect(
            selectorPackage("""{"schemaVersion":99}"""),
        )

        assertIs<AppResult.Failure>(result)
    }

    @Test
    fun selectorOutputShapeMismatchFailsBeforeInstallation() {
        val result = ZipPackageArchiveInspector().inspect(
            selectorPackage(INVALID_SELECTOR_V2),
        )

        assertIs<AppResult.Failure>(result)
    }
}

private fun selectorPackage(selectorSource: String): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { archive ->
        archive.writeSelectorEntry("manifest.json", SELECTOR_MANIFEST)
        archive.writeSelectorEntry("selector.json", selectorSource)
    }
    return output.toByteArray()
}

private fun ZipOutputStream.writeSelectorEntry(
    path: String,
    content: String,
) {
    putNextEntry(ZipEntry(path))
    write(content.encodeToByteArray())
    closeEntry()
}

private val SELECTOR_MANIFEST =
    """
    {
      "id": "community.selector",
      "name": "Selector Plugin",
      "version": "1.0.0",
      "packageChecksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "minimumHostVersion": "1.0.0",
      "updateUrl": "https://allowed.example/plugin.json",
      "api": {"major": 1, "minor": 0},
      "kinds": ["CATALOG"],
      "languages": ["en"],
      "allowedHosts": ["allowed.example"],
      "capabilities": ["NETWORK"],
      "runtime": "DECLARATIVE",
      "entry": "selector.json",
      "declarativeOrigin": "https://allowed.example/"
    }
    """.trimIndent()

private val VALID_SELECTOR_V2 =
    """
    {
      "schemaVersion": 2,
      "catalog": {
        "search": {
          "request": {
            "operations": [
              {"type": "http_get", "urlTemplate": "/search?q={query}"}
            ]
          },
          "items": {
            "type": "list",
            "css": "article",
            "item": {
              "type": "object",
              "fields": {
                "sourceId": {"type": "attribute", "attribute": "data-id"},
                "title": {"type": "text", "css": ".title"}
              }
            }
          }
        }
      }
    }
    """.trimIndent()

private val INVALID_SELECTOR_V2 =
    """
    {
      "schemaVersion": 2,
      "catalog": {
        "search": {
          "request": {
            "operations": [
              {"type": "http_get", "urlTemplate": "/search?q={query}"}
            ]
          },
          "items": {
            "type": "list",
            "css": "article",
            "item": {
              "type": "object",
              "fields": {
                "sourceId": {"type": "attribute", "attribute": "data-id"}
              }
            }
          }
        }
      }
    }
    """.trimIndent()
