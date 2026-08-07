package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.registry.ActivatedPlugin
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginActivation

data class InstallRequest(
    val packageBytes: ByteArray,
    val metadata: PluginPackageMetadata,
    val provenance: PackageInstallProvenance,
    val acceptedCapabilities: Set<PluginCapability> = emptySet(),
)

data class VerifiedPluginPackage(
    val packageBytes: ByteArray,
    val packageSha256: String,
    val pluginId: String,
    val version: String,
    val signatureDecision: PackageSignatureDecision,
    val provenance: PackageInstallProvenance,
    val acceptedCapabilities: Set<PluginCapability> = emptySet(),
)

data class StagedPluginPackage(
    val pluginId: String,
    val version: String,
    val location: String,
    val packageSha256: String,
    val signatureDecision: PackageSignatureDecision,
    val provenance: PackageInstallProvenance,
    val acceptedCapabilities: Set<PluginCapability> = emptySet(),
)

data class InstalledPlugin(
    val pluginId: String,
    val version: String,
    val location: String,
    val enabled: Boolean,
)

interface PluginPackageStorage {
    suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage>

    suspend fun remove(location: String)
}

class PluginInstaller(
    private val verifier: PackageVerifier,
    private val storage: PluginPackageStorage,
    private val registry: MutablePluginRegistry,
    private val versionPolicy: PluginVersionPolicy = PluginVersionPolicy(),
) {
    suspend fun install(request: InstallRequest): AppResult<InstalledPlugin> =
        when (val verificationResult = verifier.verify(request)) {
            is AppResult.Failure -> verificationResult
            is AppResult.Success -> installVerified(verificationResult.value)
        }

    private suspend fun installVerified(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<InstalledPlugin> {
        val registration = registry.find(verifiedPackage.pluginId)

        return when (
            val versionResult = versionPolicy.validateInstall(
                candidateVersion = verifiedPackage.version,
                activeVersion = registration?.activeVersion,
            )
        ) {
            is AppResult.Failure -> versionResult
            is AppResult.Success -> stageAndActivate(verifiedPackage)
        }
    }

    private suspend fun stageAndActivate(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<InstalledPlugin> =
        when (val stagingResult = storage.stage(verifiedPackage)) {
            is AppResult.Failure -> stagingResult
            is AppResult.Success -> activateOrRemove(stagingResult.value)
        }

    private suspend fun activateOrRemove(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin> =
        when (val activationResult = registry.activate(stagedPackage.toActivation())) {
            is AppResult.Success -> AppResult.Success(
                activationResult.value.toInstalledPlugin(),
            )

            is AppResult.Failure -> {
                storage.remove(stagedPackage.location)
                activationResult
            }
        }
}

internal fun StagedPluginPackage.toActivation(): PluginActivation =
    PluginActivation(
        pluginId = pluginId,
        version = version,
        packageSha256 = packageSha256,
        location = location,
        signatureState = signatureDecision.signatureState.name,
        signerKeyId = signatureDecision.signerKeyId,
        signerFingerprintSha256 = signatureDecision.signerFingerprintSha256,
        installSource = provenance.source.name,
        sourceReference = provenance.sourceReference,
        unsignedWarningAcknowledged = provenance.unsignedWarningAcknowledged,
        acceptedCapabilities = acceptedCapabilities.map { it.name }.toSet(),
    )

internal fun ActivatedPlugin.toInstalledPlugin(): InstalledPlugin =
    InstalledPlugin(
        pluginId = pluginId,
        version = version,
        location = location,
        enabled = enabled,
    )
