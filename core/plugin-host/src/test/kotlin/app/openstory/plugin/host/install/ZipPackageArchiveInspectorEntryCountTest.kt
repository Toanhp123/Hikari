package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageArchiveLimits
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZipPackageArchiveInspectorEntryCountTest {

    @Test
    fun excessiveEntryCountIsRejectedDuringArchiveScan() {
        val inspector =
            ZipPackageArchiveInspector(
                limits =
                    PackageArchiveLimits(
                        maximumEntryCount =
                            1,
                        maximumCompressedBytes =
                            1_024L,
                        maximumUncompressedBytes =
                            4_096L,
                        maximumCompressionRatio =
                            100.0,
                    ),
            )

        val failure =
            assertIs<AppResult.Failure>(
                inspector.inspect(
                    packageWithTwoEntries(),
                ),
            )

        assertEquals(
            expected =
                "plugin.package_archive_limit_exceeded",
            actual =
                failure.error.code,
        )
    }
}

private fun packageWithTwoEntries():
    ByteArray {
    val output =
        ByteArrayOutputStream()

    ZipOutputStream(output).use { archive ->
        archive.writeEntryCountFixture(
            path =
                "manifest.json",
            content =
                VALID_ENTRY_COUNT_MANIFEST,
        )

        archive.writeEntryCountFixture(
            path =
                "main.js",
            content =
                "export default {}",
        )
    }

    return output.toByteArray()
}

private fun ZipOutputStream.writeEntryCountFixture(
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

private val VALID_ENTRY_COUNT_MANIFEST =
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
