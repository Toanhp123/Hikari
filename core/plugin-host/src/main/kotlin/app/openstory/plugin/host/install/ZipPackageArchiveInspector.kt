package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageArchiveLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

class ZipPackageArchiveInspector(
    private val json: Json =
        Json {
            ignoreUnknownKeys =
                true
        },
    private val limits:
        PackageArchiveLimits =
        PackageArchiveLimits(),
) : PackageArchiveInspector {

    override fun inspect(
        packageBytes: ByteArray,
    ): AppResult<PluginPackageInspection> =
        if (
            packageBytes.size.toLong() >
            limits.maximumCompressedBytes
        ) {
            archiveLimitExceeded()
        } else {
            inspectWithinLimits(
                packageBytes,
            )
        }

    private fun inspectWithinLimits(
        packageBytes: ByteArray,
    ): AppResult<PluginPackageInspection> =
        runCatching {
            inspectArchive(
                packageBytes,
            )
        }.fold(
            onSuccess = {
                AppResult.Success(
                    it,
                )
            },
            onFailure = { failure ->
                when (failure) {
                    is ArchiveLimitExceededException ->
                        archiveLimitExceeded()

                    else ->
                        archiveInspectionFailure()
                }
            },
        )

    private fun inspectArchive(
        packageBytes: ByteArray,
    ): PluginPackageInspection {
        val scannedArchive =
            scanArchive(
                packageBytes,
            )

        val manifest =
            json.decodeFromString(
                deserializer =
                    PluginManifest.serializer(),
                string =
                    scannedArchive.manifestSource,
            )

        return PluginPackageInspection(
            pluginId =
                manifest.id,
            version =
                manifest.version,
            entries =
                scannedArchive.entries,
            declaredExecutableEntries =
                setOf(
                    manifest.entry,
                ),
            declaredCapabilities =
                manifest.capabilities,
        )
    }

    private fun scanArchive(
        packageBytes: ByteArray,
    ): ScannedArchive {
        val entries =
            mutableListOf<PackageArchiveEntry>()

        var manifestSource:
            String? =
            null

        var uncompressedBytes =
            0L

        ZipInputStream(
            ByteArrayInputStream(
                packageBytes,
            ),
        ).use { archive ->
            var entry =
                archive.nextEntry

            while (entry != null) {
                if (
                    entries.size >=
                    limits.maximumEntryCount
                ) {
                    throw ArchiveLimitExceededException()
                }

                val remainingBytes =
                    limits.maximumUncompressedBytes -
                        uncompressedBytes

                val entryBytes =
                    archive.readCurrentEntry(
                        maximumBytes =
                            remainingBytes,
                    )

                uncompressedBytes +=
                    entryBytes.size.toLong()

                entries +=
                    entry.toArchiveEntry(
                        entryBytes =
                            entryBytes,
                    )

                if (
                    entry.name ==
                    MANIFEST_ENTRY
                ) {
                    manifestSource =
                        entryBytes.decodeToString()
                }

                archive.closeEntry()

                entry =
                    archive.nextEntry
            }
        }

        return ScannedArchive(
            entries =
                entries,
            manifestSource =
                requireNotNull(
                    manifestSource,
                ) {
                    "Plugin package is missing manifest.json."
                },
        )
    }
}

private data class ScannedArchive(
    val entries:
        List<PackageArchiveEntry>,
    val manifestSource:
        String,
)

private fun ZipInputStream.readCurrentEntry(
    maximumBytes: Long,
): ByteArray {
    if (maximumBytes < 0L) {
        throw ArchiveLimitExceededException()
    }

    val output =
        ByteArrayOutputStream()

    val buffer =
        ByteArray(
            DEFAULT_BUFFER_SIZE,
        )

    var bytesRead =
        0L

    while (true) {
        val count =
            read(
                buffer,
            )

        if (count < 0) {
            break
        }

        if (count > 0) {
            bytesRead +=
                count.toLong()

            if (bytesRead > maximumBytes) {
                throw ArchiveLimitExceededException()
            }

            output.write(
                buffer,
                0,
                count,
            )
        }
    }

    return output.toByteArray()
}

private fun ZipEntry.toArchiveEntry(
    entryBytes: ByteArray,
): PackageArchiveEntry =
    PackageArchiveEntry(
        path =
            name,
        compressedSizeBytes =
            compressedSize
                .takeIf {
                    it >= 0L
                }
                ?: entryBytes.size
                    .toLong(),
        uncompressedSizeBytes =
            size
                .takeIf {
                    it >= 0L
                }
                ?: entryBytes.size
                    .toLong(),
        isSymbolicLink =
            false,
        isExecutable =
            false,
    )

private class ArchiveLimitExceededException :
    RuntimeException()

private fun archiveLimitExceeded():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_archive_limit_exceeded",
            retryable =
                false,
        ),
    )

private fun archiveInspectionFailure():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_inspection_failed",
            retryable =
                false,
        ),
    )

private const val MANIFEST_ENTRY =
    "manifest.json"
