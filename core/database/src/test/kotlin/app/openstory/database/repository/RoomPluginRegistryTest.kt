package app.openstory.database.repository

import app.openstory.common.AppResult
import app.openstory.common.Clock
import app.openstory.database.dao.PluginStateDao
import app.openstory.database.entity.PluginStateEntity
import app.openstory.database.entity.PluginVersionEntity
import app.openstory.plugin.host.registry.PluginActivation
import app.openstory.plugin.host.registry.PluginRegistration
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoomPluginRegistryTest {

    @Test
    fun activationCancellationIsPropagated() =
        runTest {
            val repository =
                RoomPluginRegistry(
                    dao =
                        RecordingPluginStateDao(
                            initial =
                                existingPluginState(),
                            activationFailure =
                                CancellationException(
                                    "Fixture database cancellation.",
                                ),
                        ),
                    clock =
                        Clock { 2_000L },
                )

            assertFailsWith<CancellationException> {
                repository.activate(
                    pluginActivation(),
                )
            }
        }

    @Test
    fun activationMovesCurrentVersionToPreviousAndPreservesEnabledState() =
        runTest {
            val dao =
                RecordingPluginStateDao(
                    initial =
                        existingPluginState(),
                )

            val repository =
                RoomPluginRegistry(
                    dao =
                        dao,
                    clock =
                        Clock { 2_000L },
                )

            val result =
                repository.activate(
                    pluginActivation(),
                )

            assertTrue(
                result is AppResult.Success<*>,
            )

            assertEquals(
                expected =
                    expectedRegistration(),
                actual =
                    repository.find(
                        pluginId =
                            FIXTURE_PLUGIN_ID,
                    ),
            )

            assertEquals(
                expected =
                    2_000L,
                actual =
                    dao.current
                        ?.updatedAtEpochMillis,
            )
        }

    @Test
    fun activationPersistsImmutableVersionMetadata() =
        runTest {
            val dao =
                RecordingPluginStateDao(
                    initial =
                        existingPluginState(),
                )

            val repository =
                RoomPluginRegistry(
                    dao =
                        dao,
                    clock =
                        Clock { 2_000L },
                )

            val result =
                repository.activate(
                    verifiedPluginActivation(),
                )

            assertTrue(
                result is AppResult.Success<*>,
            )

            assertEquals(
                expected =
                    expectedPluginVersion(),
                actual =
                    dao.recordedVersion,
            )
        }

    @Test
    fun reactivatingInstalledVersionPreservesOriginalImmutableMetadata() =
        runTest {
            val installedVersion =
                expectedPluginVersion(
                    installedAtEpochMillis =
                        1_000L,
                )

            val dao =
                RecordingPluginStateDao(
                    initial =
                        existingPluginState()
                            .copy(
                                activeVersion =
                                    "3.0.0",
                                previousVersion =
                                    "2.0.0",
                            ),
                    initialVersion =
                        installedVersion,
                )

            val repository =
                RoomPluginRegistry(
                    dao =
                        dao,
                    clock =
                        Clock { 2_000L },
                )

            val result =
                repository.activate(
                    verifiedPluginActivation(),
                )

            assertTrue(
                result is AppResult.Success<*>,
            )

            assertEquals(
                expected =
                    installedVersion,
                actual =
                    dao.recordedVersion,
            )

            assertEquals(
                expected =
                    "2.0.0",
                actual =
                    dao.current
                        ?.activeVersion,
            )

            assertEquals(
                expected =
                    "3.0.0",
                actual =
                    dao.current
                        ?.previousVersion,
            )
        }

    private fun existingPluginState():
        PluginStateEntity =
        PluginStateEntity(
            pluginId =
                FIXTURE_PLUGIN_ID,
            enabled =
                false,
            activeVersion =
                "1.0.0",
            previousVersion =
                null,
            updatedAtEpochMillis =
                1_000L,
        )

    private fun pluginActivation():
        PluginActivation =
        PluginActivation(
            pluginId =
                FIXTURE_PLUGIN_ID,
            version =
                "2.0.0",
            location =
                "active/community.fixture/2.0.0",
            packageSha256 =
                "0".repeat(64),
            signatureState = "UNSIGNED",
            signerKeyId = null,
            signerFingerprintSha256 = null,
            installSource = "LOCAL_FILE",
            sourceReference = "fixture-2.0.0.osp",
            unsignedWarningAcknowledged = true,
            acceptedCapabilities = emptySet(),
        )

    private fun verifiedPluginActivation():
        PluginActivation =
        PluginActivation(
            pluginId =
                FIXTURE_PLUGIN_ID,
            version =
                "2.0.0",
            location =
                "active/community.fixture/2.0.0",
            packageSha256 =
                "a".repeat(64),
            signatureState = "VERIFIED",
            signerKeyId = "fixture-author-main",
            signerFingerprintSha256 = "ab".repeat(32),
            installSource = "REPOSITORY",
            sourceReference = "fixture-repository",
            unsignedWarningAcknowledged = false,
            acceptedCapabilities =
                setOf(
                    "NETWORK",
                ),
        )

    private fun expectedPluginVersion(
        installedAtEpochMillis: Long =
            2_000L,
    ): PluginVersionEntity =
        PluginVersionEntity(
            pluginId =
                FIXTURE_PLUGIN_ID,
            version =
                "2.0.0",
            packageSha256 =
                "a".repeat(64),
            location =
                "active/community.fixture/2.0.0",
            trustSignatureState =
                "VERIFIED",
            signerKeyId =
                "fixture-author-main",
            signerFingerprintSha256 =
                "ab".repeat(32),
            installSource =
                "REPOSITORY",
            sourceReference =
                "fixture-repository",
            unsignedWarningAcknowledged =
                false,
            acceptedCapabilities =
                "NETWORK",
            installedAtEpochMillis =
                installedAtEpochMillis,
        )

    private fun expectedRegistration():
        PluginRegistration =
        PluginRegistration(
            pluginId =
                FIXTURE_PLUGIN_ID,
            enabled =
                false,
            activeVersion =
                "2.0.0",
            previousVersion =
                "1.0.0",
        )

    private companion object {
        const val FIXTURE_PLUGIN_ID =
            "community.fixture"
    }
}

private class RecordingPluginStateDao(
    initial: PluginStateEntity?,
    initialVersion:
        PluginVersionEntity? =
        null,
    private val activationFailure:
        Exception? =
        null,
) : PluginStateDao() {

    var recordedVersion:
        PluginVersionEntity? =
        initialVersion
        private set

    var current:
        PluginStateEntity? =
        initial
        private set

    override suspend fun find(
        pluginId: String,
    ): PluginStateEntity? =
        current?.takeIf { state ->
            state.pluginId ==
                pluginId
        }

    override suspend fun findVersion(
        pluginId: String,
        version: String,
    ): PluginVersionEntity? =
        recordedVersion
            ?.takeIf { recorded ->
                recorded.pluginId ==
                    pluginId &&
                    recorded.version ==
                    version
            }

    override suspend fun insertVersionIfMissing(
        version: PluginVersionEntity,
    ): Long {
        activationFailure
            ?.let { failure ->
                throw failure
            }

        val existing =
            findVersion(
                pluginId =
                    version.pluginId,
                version =
                    version.version,
            )

        if (existing != null) {
            return -1L
        }

        recordedVersion =
            version

        return 1L
    }

    override suspend fun upsert(
        state: PluginStateEntity,
    ) {
        current =
            state
    }

    override suspend fun updateEnabled(
        pluginId: String,
        enabled: Boolean,
        updatedAtEpochMillis: Long,
    ): Int {
        val existing =
            current?.takeIf { state ->
                state.pluginId ==
                    pluginId
            } ?: return 0

        current =
            existing.copy(
                enabled =
                    enabled,
                updatedAtEpochMillis =
                    updatedAtEpochMillis,
            )

        return 1
    }
}
