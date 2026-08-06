package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipPluginPackageExtractor :
    PluginPackageExtractor {

    override suspend fun extract(
        packageBytes: ByteArray,
        destination: Path,
    ): AppResult<Unit> =
        runCatching {
            extractArchive(
                packageBytes =
                    packageBytes,
                destination =
                    destination
                        .toAbsolutePath()
                        .normalize(),
            )
        }.getOrElse { throwable ->
            if (
                throwable is
                java.util.concurrent.CancellationException
            ) {
                throw throwable
            }

            zipExtractionFailure()
        }
    private fun extractArchive(
        packageBytes: ByteArray,
        destination: Path,
    ): AppResult<Unit> {
        Files.createDirectories(
            destination,
        )

        ZipInputStream(
            ByteArrayInputStream(
                packageBytes,
            ),
        ).use { archive ->
            var entry =
                archive.nextEntry

            while (entry != null) {
                when (
                    val extractionResult =
                        extractEntry(
                            archive =
                                archive,
                            entry =
                                entry,
                            destination =
                                destination,
                        )
                ) {
                    is AppResult.Failure ->
                        return extractionResult

                    is AppResult.Success ->
                        Unit
                }

                archive.closeEntry()
                entry = archive.nextEntry
            }
        }

        return AppResult.Success(
            Unit,
        )
    }

    private fun extractEntry(
        archive: ZipInputStream,
        entry: ZipEntry,
        destination: Path,
    ): AppResult<Unit> =
        when (
            val pathResult =
                resolveEntryPath(
                    entryName =
                        entry.name,
                    destination =
                        destination,
                )
        ) {
            is AppResult.Failure ->
                pathResult

            is AppResult.Success -> {
                writeEntry(
                    archive =
                        archive,
                    entry =
                        entry,
                    entryPath =
                        pathResult.value,
                )

                AppResult.Success(
                    Unit,
                )
            }
        }

    private fun writeEntry(
        archive: ZipInputStream,
        entry: ZipEntry,
        entryPath: Path,
    ) {
        if (entry.isDirectory) {
            Files.createDirectories(
                entryPath,
            )
        } else {
            Files.createDirectories(
                requireNotNull(
                    entryPath.parent,
                ),
            )

            Files.newOutputStream(
                entryPath,
            ).use { output ->
                archive.copyTo(
                    output,
                )
            }
        }
    }

    private fun resolveEntryPath(
        entryName: String,
        destination: Path,
    ): AppResult<Path> {
        val candidate =
            if (isValidZipEntryName(entryName)) {
                runCatching {
                    destination
                        .resolve(entryName)
                        .normalize()
                }.getOrNull()
            } else {
                null
            }

        return if (
            candidate != null &&
            candidate != destination &&
            candidate.startsWith(destination)
        ) {
            AppResult.Success(
                candidate,
            )
        } else {
            invalidZipEntryPath()
        }
    }
}

private fun isValidZipEntryName(
    entryName: String,
): Boolean =
    entryName.isNotBlank() &&
        !entryName.startsWith("/") &&
        !entryName.contains('\\') &&
        !entryName.contains('\u0000')

private fun invalidZipEntryPath():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_path_invalid",
            retryable =
                false,
        ),
    )

private fun zipExtractionFailure():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_extraction_failed",
            retryable =
                false,
        ),
    )
