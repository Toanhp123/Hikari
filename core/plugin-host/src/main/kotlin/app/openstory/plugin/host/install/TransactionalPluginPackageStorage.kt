package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID

fun interface PluginPackageExtractor {

    suspend fun extract(
        packageBytes: ByteArray,
        destination: Path,
    ): AppResult<Unit>
}

class TransactionalPluginPackageStorage(
    rootDirectory: Path,
    private val extractor: PluginPackageExtractor,
    private val stagingDirectoryName: () -> String = {
        UUID.randomUUID().toString()
    },
) : PluginPackageStorage,
    InstalledPluginPackageLookup {

    private val normalizedRoot =
        rootDirectory.toAbsolutePath()
            .normalize()

    private val pluginsRoot =
        normalizedRoot.resolve("plugins")
            .normalize()

    override suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage> =
        when (
            val pathResult =
                installedPath(
                    pluginId =
                        verifiedPackage.pluginId,
                    version =
                        verifiedPackage.version,
                )
        ) {
            is AppResult.Failure ->
                pathResult

            is AppResult.Success ->
                stageAtPath(
                    verifiedPackage =
                        verifiedPackage,
                    activePath =
                        pathResult.value,
                )
        }

    private suspend fun stageAtPath(
        verifiedPackage: VerifiedPluginPackage,
        activePath: Path,
    ): AppResult<StagedPluginPackage> {
        val stagingPath =
            normalizedRoot
                .resolve("staging")
                .resolve(
                    stagingDirectoryName(),
                )
                .normalize()

        return runCatching {
            stageVerifiedPackage(
                verifiedPackage =
                    verifiedPackage,
                stagingPath =
                    stagingPath,
                activePath =
                    activePath,
            )
        }.getOrElse { throwable ->
            deleteQuietly(
                stagingPath,
            )

            if (
                throwable is
                java.util.concurrent.CancellationException
            ) {
                throw throwable
            }

            storageFailure()
        }
    }
    override suspend fun findInstalled(
        pluginId: String,
        version: String,
    ): AppResult<StagedPluginPackage?> =
        when (
            val pathResult =
                installedPath(
                    pluginId =
                        pluginId,
                    version =
                        version,
                )
        ) {
            is AppResult.Failure ->
                pathResult

            is AppResult.Success ->
                findInstalledAtPath(
                    pluginId =
                        pluginId,
                    version =
                        version,
                    activePath =
                        pathResult.value,
                )
        }

    private fun findInstalledAtPath(
        pluginId: String,
        version: String,
        activePath: Path,
    ): AppResult<StagedPluginPackage?> =
        if (!Files.isDirectory(activePath)) {
            AppResult.Success(
                null,
            )
        } else {
            runCatching {
                val installMetadata =
                    PluginInstallMetadataSidecar
                        .read(
                            activePath,
                        )

                AppResult.Success(
                    StagedPluginPackage(
                        pluginId =
                            pluginId,
                        version =
                            version,
                        location =
                            activePath.toString(),
                        packageSha256 =
                            installMetadata
                                .packageSha256,
                        signatureDecision =
                            installMetadata
                                .signatureDecision,
                        provenance =
                            installMetadata.provenance,
                        acceptedCapabilities =
                            installMetadata
                                .acceptedCapabilities,
                    ),
                )
            }.getOrElse {
                metadataFailure()
            }
        }

    override suspend fun remove(
        location: String,
    ) {
        val path =
            java.nio.file.Paths.get(location)
                .toAbsolutePath()
                .normalize()

        if (path.startsWith(normalizedRoot)) {
            deleteQuietly(
                path,
            )
        }
    }

    private suspend fun stageVerifiedPackage(
        verifiedPackage: VerifiedPluginPackage,
        stagingPath: Path,
        activePath: Path,
    ): AppResult<StagedPluginPackage> {
        deleteQuietly(
            stagingPath,
        )

        Files.createDirectories(
            stagingPath,
        )

        return when (
            val extractionResult =
                extractor.extract(
                    packageBytes =
                        verifiedPackage.packageBytes,
                    destination =
                        stagingPath,
                )
        ) {
            is AppResult.Failure -> {
                deleteQuietly(
                    stagingPath,
                )

                extractionResult
            }

            is AppResult.Success -> {
                PluginInstallMetadataSidecar
                    .write(
                        directory =
                            stagingPath,
                        packageSha256 =
                            verifiedPackage
                                .packageSha256,
                        provenance =
                            verifiedPackage.provenance,
                        signatureDecision =
                            verifiedPackage
                                .signatureDecision,
                        acceptedCapabilities =
                            verifiedPackage
                                .acceptedCapabilities,
                    )

                publish(
                    verifiedPackage =
                        verifiedPackage,
                    stagingPath =
                        stagingPath,
                    activePath =
                        activePath,
                )
            }
        }
    }

    private fun publish(
        verifiedPackage: VerifiedPluginPackage,
        stagingPath: Path,
        activePath: Path,
    ): AppResult<StagedPluginPackage> {
        if (Files.exists(activePath)) {
            deleteQuietly(
                stagingPath,
            )

            return immutableVersionConflict()
        }

        Files.createDirectories(
            activePath.parent,
        )

        Files.move(
            stagingPath,
            activePath,
            StandardCopyOption.ATOMIC_MOVE,
        )

        makeReadOnly(
            activePath,
        )

        return AppResult.Success(
            StagedPluginPackage(
                pluginId =
                    verifiedPackage.pluginId,
                version =
                    verifiedPackage.version,
                location =
                    activePath.toString(),
                packageSha256 =
                    verifiedPackage
                        .packageSha256,
                signatureDecision =
                    verifiedPackage
                        .signatureDecision,
                provenance =
                    verifiedPackage.provenance,
                acceptedCapabilities =
                    verifiedPackage
                        .acceptedCapabilities,
            ),
        )
    }

    private fun installedPath(
        pluginId: String,
        version: String,
    ): AppResult<Path> {
        val candidate =
            if (
                isValidPathSegment(pluginId) &&
                isValidPathSegment(version)
            ) {
                runCatching {
                    pluginsRoot
                        .resolve(pluginId)
                        .resolve(version)
                        .normalize()
                }.getOrNull()
            } else {
                null
            }

        return if (
            candidate != null &&
            candidate.startsWith(pluginsRoot)
        ) {
            AppResult.Success(
                candidate,
            )
        } else {
            invalidPackagePath()
        }
    }
}

private fun makeReadOnly(
    root: Path,
) {
    Files.walk(root).use { paths ->
        paths.forEach { path ->
            path.toFile()
                .setWritable(
                    false,
                    false,
                )
        }
    }
}

private fun deleteQuietly(
    root: Path,
) {
    if (!Files.exists(root)) {
        return
    }

    runCatching {
        Files.walk(root).use { paths ->
            paths
                .sorted(
                    Comparator.reverseOrder(),
                )
                .forEach { path ->
                    path.toFile()
                        .setWritable(
                            true,
                            false,
                        )

                    Files.deleteIfExists(
                        path,
                    )
                }
        }
    }
}

private fun isValidPathSegment(
    value: String,
): Boolean =
    value.isNotBlank() &&
        value != "." &&
        value != ".." &&
        !value.contains('/') &&
        !value.contains('\\') &&
        !value.contains('\u0000')

private fun invalidPackagePath():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_path_invalid",
            retryable =
                false,
        ),
    )
private fun storageFailure():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Storage(
            code =
                "storage.plugin_package_write_failed",
            retryable =
                true,
        ),
    )

private fun metadataFailure():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Storage(
            code =
                "storage.plugin_package_metadata_invalid",
            retryable =
                false,
        ),
    )

private fun immutableVersionConflict():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_version_conflict",
            retryable =
                false,
        ),
    )
