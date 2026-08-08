package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.registry.ActivatedPlugin
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginActivation
import app.openstory.plugin.host.update.PluginUpdateInstaller
import app.openstory.plugin.host.update.PreparedPluginUpdate

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
) : PluginUpdateInstaller {
    suspend fun install(request: InstallRequest): AppResult<InstalledPlugin> =
        when (val staged = stage(request)) {
            is AppResult.Failure -> staged
            is AppResult.Success -> activate(staged.value)
        }

    override suspend fun stage(request: InstallRequest): AppResult<PreparedPluginUpdate> =
        when (val verificationResult = verifier.verify(request)) {
            is AppResult.Failure -> verificationResult
            is AppResult.Success -> prepareVerified(verificationResult.value)
        }

    override suspend fun activate(
        prepared: PreparedPluginUpdate,
    ): AppResult<InstalledPlugin> {
        val stagedPackage = prepared.stagedPackage
            ?: return invalidPreparedUpdate()
        return activateOrRemove(stagedPackage)
    }

    override suspend fun discard(prepared: PreparedPluginUpdate) {
        storage.remove(prepared.location)
    }

    private suspend fun prepareVerified(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<PreparedPluginUpdate> {
        val registration = registry.find(verifiedPackage.pluginId)

        return when (
            val versionResult = versionPolicy.validateInstall(
                candidateVersion = verifiedPackage.version,
                activeVersion = registration?.activeVersion,
            )
        ) {
            is AppResult.Failure -> versionResult
            is AppResult.Success -> stagePrepared(verifiedPackage)
        }
    }

    private suspend fun stagePrepared(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<PreparedPluginUpdate> =
        when (val stagingResult = storage.stage(verifiedPackage)) {
            is AppResult.Failure -> stagingResult
            is AppResult.Success -> AppResult.Success(stagingResult.value.toPreparedUpdate())
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

private fun StagedPluginPackage.toPreparedUpdate(): PreparedPluginUpdate =
    PreparedPluginUpdate(
        pluginId = pluginId,
        version = version,
        location = location,
        signatureState = signatureDecision.signatureState,
        signerKeyId = signatureDecision.signerKeyId,
        signerFingerprintSha256 = signatureDecision.signerFingerprintSha256,
        stagedPackage = this,
    )

private fun invalidPreparedUpdate(): AppResult.Failure =
    AppResult.Failure(
        app.openstory.common.AppError.Plugin(
            code = "plugin.update_prepared_package_invalid",
            retryable = false,
        ),
    )

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
