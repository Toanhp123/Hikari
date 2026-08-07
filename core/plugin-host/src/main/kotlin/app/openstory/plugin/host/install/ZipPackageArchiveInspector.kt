package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageArchiveLimits
import app.openstory.plugin.api.selector.SelectorDefinitionDecoder
import app.openstory.plugin.api.selector.SelectorValidation
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

        validateSelectorDefinition(
            manifest = manifest,
            selectorSource = scannedArchive.selectorSource,
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
    ): ScannedArchive =
        ZipInputStream(ByteArrayInputStream(packageBytes)).use { archive ->
            scanEntries(archive)
        }

    private fun scanEntries(archive: ZipInputStream): ScannedArchive {
        val entries = mutableListOf<PackageArchiveEntry>()
        var manifestSource: String? = null
        var selectorSource: String? = null
        var uncompressedBytes = 0L
        var entry = archive.nextEntry

        while (entry != null) {
            ensureEntryLimit(entries)
            val entryBytes = readEntryBytes(archive, uncompressedBytes)
            uncompressedBytes += entryBytes.size.toLong()
            entries += entry.toArchiveEntry(entryBytes = entryBytes)

            when (entry.name) {
                MANIFEST_ENTRY -> manifestSource = entryBytes.decodeToString()
                SELECTOR_ENTRY -> selectorSource = entryBytes.decodeToString()
            }

            archive.closeEntry()
            entry = archive.nextEntry
        }

        return scannedArchive(entries, manifestSource, selectorSource)
    }

    private fun ensureEntryLimit(entries: List<PackageArchiveEntry>) {
        if (entries.size >= limits.maximumEntryCount) {
            throw ArchiveLimitExceededException()
        }
    }

    private fun readEntryBytes(
        archive: ZipInputStream,
        uncompressedBytes: Long,
    ): ByteArray = archive.readCurrentEntry(
        maximumBytes = limits.maximumUncompressedBytes - uncompressedBytes,
    )

    private fun scannedArchive(
        entries: List<PackageArchiveEntry>,
        manifestSource: String?,
        selectorSource: String?,
    ): ScannedArchive = ScannedArchive(
        entries = entries,
        manifestSource = requireNotNull(manifestSource) {
            "Plugin package is missing manifest.json."
        },
        selectorSource = selectorSource,
    )
}

private data class ScannedArchive(
    val entries:
        List<PackageArchiveEntry>,
    val manifestSource:
        String,
    val selectorSource:
        String?,
)

private fun validateSelectorDefinition(
    manifest: PluginManifest,
    selectorSource: String?,
) {
    if (manifest.runtime != PluginRuntime.DECLARATIVE) {
        return
    }

    val definition = SelectorDefinitionDecoder()
        .decode(
            requireNotNull(selectorSource) {
                "Declarative package is missing selector.json."
            },
        )
        .getOrThrow()

    SelectorValidation.validate(
        definition = definition,
        manifest = manifest,
    ).getOrThrow()
}

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

private const val SELECTOR_ENTRY =
    "selector.json"
