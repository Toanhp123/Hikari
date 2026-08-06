package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionalPluginPackageStorageTest {

    @Test
    fun extractionFailureLeavesNoStagingOrActiveVersion() =
        runTest {
            val fixture =
                storageFixture(
                    extractor =
                        failingExtractor(),
                )

            try {
                val result =
                    fixture.storage.stage(
                        fixture.verifiedPackage,
                    )

                assertIs<AppResult.Failure>(
                    result,
                )

                assertFalse(
                    fixture.stagingExists(),
                    "Failed extraction must remove partial staging files.",
                )

                assertFalse(
                    fixture.activeVersionExists(),
                    "Failed extraction must not publish an active version.",
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun successfulStagePublishesReadOnlyImmutableVersion() =
        runTest {
            val fixture =
                storageFixture(
                    extractor =
                        successfulExtractor(),
                )

            try {
                val result =
                    fixture.storage.stage(
                        fixture.verifiedPackage,
                    )

                val success =
                    assertIs<AppResult.Success<*>>(
                        result,
                    )

                fixture.assertPublished(
                    assertIs<StagedPluginPackage>(
                        success.value,
                    ),
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun existingImmutableVersionCannotBeOverwritten() =
        runTest {
            val fixture =
                storageFixture(
                    extractor =
                        successfulExtractor(
                            script = ORIGINAL_SCRIPT,
                        ),
                )

            try {
                assertIs<AppResult.Success<*>>(
                    fixture.storage.stage(
                        fixture.verifiedPackage,
                    ),
                )

                val replacementStorage =
                    fixture.storageWith(
                        extractor =
                            successfulExtractor(
                                script =
                                    REPLACEMENT_SCRIPT,
                            ),
                    )

                val failure =
                    assertIs<AppResult.Failure>(
                        replacementStorage.stage(
                            fixture.verifiedPackage,
                        ),
                    )

                assertEquals(
                    expected =
                        "plugin.package_version_conflict",
                    actual =
                        failure.error.code,
                )

                assertEquals(
                    expected =
                        ORIGINAL_SCRIPT,
                    actual =
                        fixture.activeEntryText(),
                    message =
                        "An installed immutable version must retain its original files.",
                )

                assertFalse(
                    fixture.stagingExists(),
                    "Rejected replacement staging must be removed.",
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun cancellationDuringExtractionIsPropagated() =
        runTest {
            val fixture =
                storageFixture(
                    extractor =
                        cancellingExtractor(),
                )

            try {
                assertFailsWith<CancellationException> {
                    fixture.storage.stage(
                        fixture.verifiedPackage,
                    )
                }

                assertFalse(
                    fixture.stagingExists(),
                    "Cancellation must remove partial staging files.",
                )

                assertFalse(
                    fixture.activeVersionExists(),
                    "Cancellation must not publish an active version.",
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun failedRestageDoesNotDeleteInstalledImmutableVersion() =
        runTest {
            val fixture =
                storageFixture(
                    extractor =
                        successfulExtractor(
                            script =
                                ORIGINAL_SCRIPT,
                        ),
                )

            try {
                assertIs<AppResult.Success<*>>(
                    fixture.storage.stage(
                        fixture.verifiedPackage,
                    ),
                )

                val failure =
                    assertIs<AppResult.Failure>(
                        fixture.storageWith(
                            extractor =
                                throwingExtractor(),
                        ).stage(
                            fixture.verifiedPackage,
                        ),
                    )

                assertEquals(
                    expected =
                        "storage.plugin_package_write_failed",
                    actual =
                        failure.error.code,
                )

                assertTrue(
                    fixture.activeVersionExists(),
                    "Failed restaging must preserve the installed immutable version.",
                )

                assertEquals(
                    expected =
                        ORIGINAL_SCRIPT,
                    actual =
                        fixture.activeEntryText(),
                )

                assertFalse(
                    fixture.stagingExists(),
                    "Failed restaging must remove partial staging files.",
                )
            } finally {
                fixture.close()
            }
        }

    @Test
    fun publishedVersionCanBeFoundAfterStorageIsRecreated() =
        runTest {
            val fixture =
                storageFixture(
                    extractor =
                        successfulExtractor(),
                )

            try {
                val staged =
                    assertIs<StagedPluginPackage>(
                        assertIs<AppResult.Success<*>>(
                            fixture.storage.stage(
                                fixture.verifiedPackage,
                            ),
                        ).value,
                    )

                val recreatedStorage =
                    fixture.storageWith(
                        extractor =
                            successfulExtractor(),
                    )

                val found =
                    assertIs<StagedPluginPackage>(
                        assertIs<AppResult.Success<*>>(
                            recreatedStorage.findInstalled(
                                pluginId =
                                    FIXTURE_PLUGIN_ID,
                                version =
                                    FIXTURE_VERSION,
                            ),
                        ).value,
                    )

                assertEquals(
                    expected =
                        staged.pluginId,
                    actual =
                        found.pluginId,
                )

                assertEquals(
                    expected =
                        staged.version,
                    actual =
                        found.version,
                )

                assertEquals(
                    expected =
                        staged.location,
                    actual =
                        found.location,
                )

                assertEquals(
                    expected =
                        staged.provenance,
                    actual =
                        found.provenance,
                )
            } finally {
                fixture.close()
            }
        }
    @Test
    fun traversalPluginIdIsRejectedBeforeExtraction() =
        runTest {
            val rootDirectory =
                Files.createTempDirectory(
                    "openstory-plugin-storage-",
                )

            var extractionCalls = 0

            val storage =
                pluginStorage(
                    rootDirectory =
                        rootDirectory,
                    extractor =
                        PluginPackageExtractor {
                                _,
                                _,
                            ->
                            extractionCalls += 1

                            AppResult.Success(
                                Unit,
                            )
                        },
                )

            try {
                val failure =
                    assertIs<AppResult.Failure>(
                        storage.stage(
                            verifiedPackage().copy(
                                pluginId =
                                    "../escape",
                            ),
                        ),
                    )

                assertEquals(
                    expected =
                        "plugin.package_path_invalid",
                    actual =
                        failure.error.code,
                )

                assertEquals(
                    expected = 0,
                    actual =
                        extractionCalls,
                    message =
                        "Invalid plugin paths must be rejected before extraction.",
                )

                assertFalse(
                    Files.exists(
                        rootDirectory.resolve(
                            "escape",
                        ),
                    ),
                    "Plugin ID traversal must not create files outside plugins/.",
                )
            } finally {
                rootDirectory.deleteRecursively()
            }
        }
}

private fun failingExtractor():
    PluginPackageExtractor =
    PluginPackageExtractor {
            _,
            destination,
        ->
        Files.createDirectories(
            destination,
        )

        Files.writeString(
            destination.resolve(
                "partial.js",
            ),
            "partial",
        )

        AppResult.Failure(
            AppError.Plugin(
                code =
                    "plugin.package_extraction_failed",
                retryable = false,
            ),
        )
    }

private fun cancellingExtractor():
    PluginPackageExtractor =
    PluginPackageExtractor {
            _,
            destination,
        ->
        Files.createDirectories(
            destination,
        )

        Files.writeString(
            destination.resolve(
                "partial.js",
            ),
            "partial",
        )

        throw CancellationException(
            "Fixture extraction cancellation.",
        )
    }

private fun throwingExtractor():
    PluginPackageExtractor =
    PluginPackageExtractor {
            _,
            destination,
        ->
        Files.createDirectories(
            destination,
        )

        Files.writeString(
            destination.resolve(
                "partial.js",
            ),
            "partial",
        )

        error(
            "Fixture extraction crash.",
        )
    }

private fun successfulExtractor(
    script: String =
        "export default {}",
): PluginPackageExtractor =
    PluginPackageExtractor {
            _,
            destination,
        ->
        Files.createDirectories(
            destination,
        )

        Files.writeString(
            destination.resolve(
                "manifest.json",
            ),
            "{}",
        )

        Files.writeString(
            destination.resolve(
                "main.js",
            ),
            script,
        )

        AppResult.Success(
            Unit,
        )
    }

private fun storageFixture(
    extractor: PluginPackageExtractor,
): PluginStorageFixture {
    val rootDirectory =
        Files.createTempDirectory(
            "openstory-plugin-storage-",
        )

    return PluginStorageFixture(
        rootDirectory =
            rootDirectory,
        storage =
            pluginStorage(
                rootDirectory =
                    rootDirectory,
                extractor =
                    extractor,
            ),
        verifiedPackage =
            verifiedPackage(),
    )
}

private fun pluginStorage(
    rootDirectory: Path,
    extractor: PluginPackageExtractor,
): TransactionalPluginPackageStorage =
    TransactionalPluginPackageStorage(
        rootDirectory =
            rootDirectory,
        extractor =
            extractor,
        stagingDirectoryName = {
            FIXTURE_STAGING_DIRECTORY
        },
    )

private class PluginStorageFixture(
    private val rootDirectory: Path,
    val storage:
        TransactionalPluginPackageStorage,
    val verifiedPackage:
        VerifiedPluginPackage,
) : AutoCloseable {

    private val stagingPath =
        rootDirectory
            .resolve("staging")
            .resolve(
                FIXTURE_STAGING_DIRECTORY,
            )

    private val activePath =
        rootDirectory
            .resolve("plugins")
            .resolve(FIXTURE_PLUGIN_ID)
            .resolve(FIXTURE_VERSION)

    fun storageWith(
        extractor: PluginPackageExtractor,
    ): TransactionalPluginPackageStorage =
        pluginStorage(
            rootDirectory =
                rootDirectory,
            extractor =
                extractor,
        )

    fun stagingExists(): Boolean =
        Files.exists(
            stagingPath,
        )

    fun activeVersionExists(): Boolean =
        Files.exists(
            activePath,
        )

    fun activeEntryText(): String =
        Files.readString(
            activePath.resolve(
                "main.js",
            ),
        )

    fun assertPublished(
        stagedPackage: StagedPluginPackage,
    ) {
        val installedEntry =
            activePath.resolve(
                "main.js",
            )

        assertEquals(
            expected =
                activePath.toAbsolutePath()
                    .normalize()
                    .toString(),
            actual =
                Path.of(
                    stagedPackage.location,
                )
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
        )

        assertFalse(
            stagingExists(),
            "Atomic publish must consume the staging directory.",
        )

        assertTrue(
            activeVersionExists(),
            "Verified package must be published under its immutable version.",
        )

        assertTrue(
            Files.exists(
                installedEntry,
            ),
        )

        assertFalse(
            Files.isWritable(
                installedEntry,
            ),
            "Published plugin files must be read-only.",
        )
    }

    override fun close() {
        rootDirectory.deleteRecursively()
    }
}

private fun verifiedPackage():
    VerifiedPluginPackage =
    VerifiedPluginPackage(
        packageBytes =
            "verified-package"
                .encodeToByteArray(),
        packageSha256 =
            "a".repeat(64),
        pluginId =
            FIXTURE_PLUGIN_ID,
        version =
            FIXTURE_VERSION,
        signatureDecision =
            PackageSignatureDecision.unsigned(),
        provenance =
            PackageInstallProvenance(
                source =
                    PackageInstallSource.LOCAL_FILE,
                sourceReference =
                    "fixture.osp",
                signatureState =
                    PackageSignatureState.UNSIGNED,
                unsignedWarningAcknowledged =
                    true,
            ),
    )

private fun Path.deleteRecursively() {
    if (!Files.exists(this)) {
        return
    }

    Files.walk(this).use { paths ->
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

private const val FIXTURE_PLUGIN_ID =
    "community.fixture"

private const val FIXTURE_VERSION =
    "1.0.0"

private const val FIXTURE_STAGING_DIRECTORY =
    "fixture-stage"

private const val ORIGINAL_SCRIPT =
    "original"

private const val REPLACEMENT_SCRIPT =
    "replacement"
