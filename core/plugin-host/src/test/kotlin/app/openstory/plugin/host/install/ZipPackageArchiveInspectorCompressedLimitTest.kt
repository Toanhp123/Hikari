package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageArchiveLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZipPackageArchiveInspectorCompressedLimitTest {

    @Test
    fun oversizedPackageBytesAreRejectedBeforeZipParsing() {
        val inspector =
            ZipPackageArchiveInspector(
                limits =
                    PackageArchiveLimits(
                        maximumEntryCount =
                            8,
                        maximumCompressedBytes =
                            MAXIMUM_PACKAGE_BYTES,
                        maximumUncompressedBytes =
                            4_096L,
                        maximumCompressionRatio =
                            100.0,
                    ),
            )

        val failure =
            assertIs<AppResult.Failure>(
                inspector.inspect(
                    oversizedInvalidPackage(),
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

private fun oversizedInvalidPackage():
    ByteArray =
    ByteArray(
        OVERSIZED_PACKAGE_BYTES,
    ) {
        INVALID_ZIP_BYTE
    }

private const val MAXIMUM_PACKAGE_BYTES =
    32L

private const val OVERSIZED_PACKAGE_BYTES =
    64

private const val INVALID_ZIP_BYTE:
    Byte =
    0x41
