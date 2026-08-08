package app.openstory.plugin.host.update

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.install.InstallRequest
import app.openstory.plugin.host.install.InstalledPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PluginUpdateServiceTest {
    @Test
    fun automaticModeStopsOnNewDomain() = runTest {
        val installer = RecordingUpdateInstaller()
        val service = PluginUpdateService(
            update = availableUpdate(
                oldHosts = setOf("a.example"),
                newHosts = setOf("a.example", "b.example"),
            ),
            installer = installer,
            smokeTester = PluginContractSmokeTester { AppResult.Success(Unit) },
        )

        val result = service.applyAvailableUpdate(UpdateMode.AUTOMATIC).value()

        assertEquals(UpdateDecision.NEEDS_REVIEW, result.decision)
        assertEquals(setOf("b.example"), result.capabilityDiff.addedHosts)
        assertEquals(0, installer.stageCalls)
    }

    @Test
    fun automaticModeStagesSmokeTestsThenActivatesSafeUpdate() = runTest {
        val events = mutableListOf<String>()
        val installer = RecordingUpdateInstaller(events)
        val service = PluginUpdateService(
            update = availableUpdate(
                oldHosts = setOf("a.example"),
                newHosts = setOf("a.example"),
            ),
            installer = installer,
            smokeTester = PluginContractSmokeTester { prepared ->
                events += "smoke:${prepared.version}"
                AppResult.Success(Unit)
            },
        )

        val result = service.applyAvailableUpdate(UpdateMode.AUTOMATIC).value()

        assertEquals(UpdateDecision.APPLIED, result.decision)
        assertEquals(listOf("stage:2.0.0", "smoke:2.0.0", "activate:2.0.0"), events)
        assertEquals("2.0.0", assertIs<InstalledPlugin>(result.installedPlugin).version)
    }

    @Test
    fun smokeTestCancellationDiscardsPreparedVersionAndPropagates() = runTest {
        val events = mutableListOf<String>()
        val installer = RecordingUpdateInstaller(events)
        val service = PluginUpdateService(
            update = availableUpdate(
                oldHosts = setOf("a.example"),
                newHosts = setOf("a.example"),
            ),
            installer = installer,
            smokeTester = PluginContractSmokeTester {
                events += "smoke:${it.version}"
                throw CancellationException("fixture cancellation")
            },
        )

        assertFailsWith<CancellationException> {
            service.applyAvailableUpdate(UpdateMode.AUTOMATIC)
        }

        assertEquals(listOf("stage:2.0.0", "smoke:2.0.0", "discard:2.0.0"), events)
    }

    @Test
    fun automaticModeStopsWhenSignerFingerprintChanges() = runTest {
        val installer = RecordingUpdateInstaller()
        val update = availableUpdate(
            oldHosts = setOf("a.example"),
            newHosts = setOf("a.example"),
        )
        val service = PluginUpdateService(
            update = update.copy(
                candidateTrust = update.candidateTrust.copy(
                    signerFingerprintSha256 = "b".repeat(64),
                ),
            ),
            installer = installer,
            smokeTester = PluginContractSmokeTester { AppResult.Success(Unit) },
        )

        val result = service.applyAvailableUpdate(UpdateMode.AUTOMATIC).value()

        assertEquals(UpdateDecision.NEEDS_REVIEW, result.decision)
        assertEquals(true, result.capabilityDiff.signerChanged)
        assertEquals(0, installer.stageCalls)
    }

    @Test
    fun askModeReturnsCompleteReviewWithoutStaging() = runTest {
        val installer = RecordingUpdateInstaller()
        val update = availableUpdate(
            oldHosts = setOf("a.example"),
            newHosts = setOf("a.example", "b.example"),
        )
        val service = PluginUpdateService(
            update = update.copy(
                currentManifest = manifest("1.0.0", emptySet(), network = false),
                candidateManifest = manifest("2.0.0", setOf("b.example")),
                candidateTrust = update.candidateTrust.copy(
                    signerKeyId = "new-author",
                    signerFingerprintSha256 = "b".repeat(64),
                    origin = "new-repository",
                ),
            ),
            installer = installer,
            smokeTester = PluginContractSmokeTester { AppResult.Success(Unit) },
        )

        val result = service.applyAvailableUpdate(UpdateMode.ASK).value()

        assertEquals(UpdateDecision.NEEDS_REVIEW, result.decision)
        assertEquals("https://updates.example/changelog/2.0.0", result.changelogUrl)
        assertEquals(setOf("b.example"), result.capabilityDiff.addedHosts)
        assertEquals(setOf("NETWORK"), result.capabilityDiff.addedCapabilities)
        assertEquals(true, result.capabilityDiff.signerChanged)
        assertEquals(true, result.originChanged)
        assertEquals(0, installer.stageCalls)
    }

    @Test
    fun automaticUnsignedUpdateFromDifferentOriginNeedsReview() = runTest {
        val installer = RecordingUpdateInstaller()
        val update = availableUpdate(setOf("a.example"), setOf("a.example"))
        val service = PluginUpdateService(
            update = update.copy(
                currentTrust = unsignedTrust("old-origin"),
                candidateTrust = unsignedTrust("new-origin"),
            ),
            installer = installer,
            smokeTester = PluginContractSmokeTester { AppResult.Success(Unit) },
        )

        val result = service.applyAvailableUpdate(UpdateMode.AUTOMATIC).value()

        assertEquals(UpdateDecision.NEEDS_REVIEW, result.decision)
        assertEquals(false, result.capabilityDiff.signerChanged)
        assertEquals(true, result.originChanged)
        assertEquals(0, installer.stageCalls)
    }

    @Test
    fun failedSmokeTestDiscardsPreparedVersionWithoutActivation() = runTest {
        val events = mutableListOf<String>()
        val installer = RecordingUpdateInstaller(events)
        val service = PluginUpdateService(
            update = availableUpdate(setOf("a.example"), setOf("a.example")),
            installer = installer,
            smokeTester = PluginContractSmokeTester { prepared ->
                events += "smoke:${prepared.version}"
                AppResult.Failure(
                    app.openstory.common.AppError.Plugin(
                        code = "plugin.contract_smoke_failed",
                        retryable = false,
                    ),
                )
            },
        )

        val result = assertIs<AppResult.Failure>(
            service.applyAvailableUpdate(UpdateMode.AUTOMATIC),
        )

        assertEquals("plugin.contract_smoke_failed", result.error.code)
        assertEquals(listOf("stage:2.0.0", "smoke:2.0.0", "discard:2.0.0"), events)
    }

    @Test
    fun stagedPackageTrustMustMatchCandidateMetadata() = runTest {
        val events = mutableListOf<String>()
        val installer = RecordingUpdateInstaller(
            events = events,
            stagedSignerFingerprintSha256 = "b".repeat(64),
        )
        val service = PluginUpdateService(
            update = availableUpdate(setOf("a.example"), setOf("a.example")),
            installer = installer,
            smokeTester = PluginContractSmokeTester { AppResult.Success(Unit) },
        )

        val result = assertIs<AppResult.Failure>(
            service.applyAvailableUpdate(UpdateMode.AUTOMATIC),
        )

        assertEquals("plugin.update_trust_mismatch", result.error.code)
        assertEquals(listOf("stage:2.0.0", "discard:2.0.0"), events)
    }

    @Test
    fun unexpectedSmokeExceptionIsRedactedAndDiscardsPreparedVersion() = runTest {
        val events = mutableListOf<String>()
        val installer = RecordingUpdateInstaller(events)
        val service = PluginUpdateService(
            update = availableUpdate(setOf("a.example"), setOf("a.example")),
            installer = installer,
            smokeTester = PluginContractSmokeTester { prepared ->
                events += "smoke:${prepared.version}"
                error("private plugin output")
            },
        )

        val result = assertIs<AppResult.Failure>(
            service.applyAvailableUpdate(UpdateMode.AUTOMATIC),
        )

        assertEquals("plugin.contract_smoke_failed", result.error.code)
        assertEquals(app.openstory.common.AppError.Diagnostic.empty(), result.error.diagnostic)
        assertEquals(listOf("stage:2.0.0", "smoke:2.0.0", "discard:2.0.0"), events)
    }

    @Test
    fun invalidSignatureStateCannotFormUpdateTrust() {
        assertFailsWith<IllegalArgumentException> {
            PluginUpdateTrust(
                signatureState = PackageSignatureState.INVALID,
                signerKeyId = null,
                signerFingerprintSha256 = null,
                origin = "fixture-repository",
            )
        }
    }
}

private class RecordingUpdateInstaller(
    private val events: MutableList<String> = mutableListOf(),
    private val stagedSignerFingerprintSha256: String = "a".repeat(64),
) : PluginUpdateInstaller {
    var stageCalls = 0
        private set

    override suspend fun stage(request: InstallRequest): AppResult<PreparedPluginUpdate> {
        stageCalls += 1
        events += "stage:${request.metadata.version}"
        return AppResult.Success(
            PreparedPluginUpdate(
                pluginId = request.metadata.pluginId,
                version = request.metadata.version,
                location = "plugins/${request.metadata.pluginId}/${request.metadata.version}",
                signatureState = PackageSignatureState.VERIFIED,
                signerKeyId = "fixture-author",
                signerFingerprintSha256 = stagedSignerFingerprintSha256,
            ),
        )
    }

    override suspend fun activate(
        prepared: PreparedPluginUpdate,
    ): AppResult<InstalledPlugin> {
        events += "activate:${prepared.version}"
        return AppResult.Success(
            InstalledPlugin(
                pluginId = prepared.pluginId,
                version = prepared.version,
                location = prepared.location,
                enabled = true,
            ),
        )
    }

    override suspend fun discard(prepared: PreparedPluginUpdate) {
        events += "discard:${prepared.version}"
    }
}

private fun availableUpdate(
    oldHosts: Set<String>,
    newHosts: Set<String>,
): AvailablePluginUpdate = AvailablePluginUpdate(
    currentManifest = manifest("1.0.0", oldHosts),
    candidateManifest = manifest("2.0.0", newHosts),
    currentTrust = PluginUpdateTrust(
        signatureState = PackageSignatureState.VERIFIED,
        signerKeyId = "fixture-author",
        signerFingerprintSha256 = "a".repeat(64),
        origin = "fixture-repository",
    ),
    candidateTrust = PluginUpdateTrust(
        signatureState = PackageSignatureState.VERIFIED,
        signerKeyId = "fixture-author",
        signerFingerprintSha256 = "a".repeat(64),
        origin = "fixture-repository",
    ),
    changelogUrl = "https://updates.example/changelog/2.0.0",
    installRequest = InstallRequest(
        packageBytes = byteArrayOf(),
        metadata = PluginPackageMetadata(
            pluginId = "community.fixture",
            version = "2.0.0",
            exactPackageSha256 = "0".repeat(64),
            signature = null,
        ),
        provenance = PackageInstallProvenance(
            source = PackageInstallSource.LOCAL_FILE,
            sourceReference = "fixture.osp",
            signatureState = PackageSignatureState.UNSIGNED,
            unsignedWarningAcknowledged = true,
        ),
        acceptedCapabilities = setOf(PluginCapability.NETWORK),
    ),
)

private fun unsignedTrust(origin: String): PluginUpdateTrust = PluginUpdateTrust(
    signatureState = PackageSignatureState.UNSIGNED,
    signerKeyId = null,
    signerFingerprintSha256 = null,
    origin = origin,
)

private fun manifest(
    version: String,
    hosts: Set<String>,
    network: Boolean = true,
): PluginManifest = PluginManifest(
    id = "community.fixture",
    name = "Fixture",
    version = version,
    packageChecksumSha256 = "a".repeat(64),
    minimumHostVersion = "1.0.0",
    updateUrl = "https://updates.example/manifest.json",
    api = PluginApiVersion(1, 0),
    kinds = setOf(PluginKind.CATALOG),
    languages = setOf("en"),
    allowedHosts = hosts,
    capabilities = if (network) setOf(PluginCapability.NETWORK) else emptySet(),
    runtime = PluginRuntime.JAVASCRIPT,
    entry = "main.js",
)

private fun <T> AppResult<T>.value(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> error("Expected success, got ${error.code}.")
}
