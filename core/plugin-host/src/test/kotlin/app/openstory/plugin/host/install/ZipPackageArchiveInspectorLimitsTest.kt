package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageArchiveLimits
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZipPackageArchiveInspectorLimitsTest {

    @Test
    fun oversizedArchiveIsRejectedBeforeManifestParsing() {
        val inspector =
            ZipPackageArchiveInspector(
                limits =
                    PackageArchiveLimits(
                        maximumEntryCount =
                            8,
                        maximumCompressedBytes =
                            1_024L,
                        maximumUncompressedBytes =
                            MAXIMUM_UNCOMPRESSED_BYTES,
                        maximumCompressionRatio =
                            100.0,
                    ),
            )

        val failure =
            assertIs<AppResult.Failure>(
                inspector.inspect(
                    oversizedPackage(),
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

private fun oversizedPackage():
    ByteArray {
    val output =
        ByteArrayOutputStream()

    ZipOutputStream(output).use { archive ->
        archive.putNextEntry(
            ZipEntry(
                "main.js",
            ),
        )

        archive.write(
            "x"
                .repeat(
                    OVERSIZED_ENTRY_BYTES,
                )
                .encodeToByteArray(),
        )

        archive.closeEntry()
    }

    return output.toByteArray()
}

private const val MAXIMUM_UNCOMPRESSED_BYTES =
    32L

private const val OVERSIZED_ENTRY_BYTES =
    64
