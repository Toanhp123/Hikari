package app.openstory.plugin.host.update

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.host.install.InstallRequest
import app.openstory.plugin.host.install.InstalledPlugin
import app.openstory.plugin.host.install.StagedPluginPackage
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class UpdateMode {
    MANUAL,
    ASK,
    AUTOMATIC,
}

enum class UpdateDecision {
    MANUAL_REQUIRED,
    NEEDS_REVIEW,
    APPLIED,
}

data class PluginUpdateTrust(
    val signatureState: PackageSignatureState,
    val signerKeyId: String?,
    val signerFingerprintSha256: String?,
    val origin: String,
) {
    init {
        require(origin.isNotBlank())
        require(signatureState != PackageSignatureState.INVALID)
        if (signatureState == PackageSignatureState.VERIFIED) {
            require(!signerKeyId.isNullOrBlank())
            require(signerFingerprintSha256?.matches(SHA_256) == true)
        } else {
            require(signerKeyId == null)
            require(signerFingerprintSha256 == null)
        }
    }

    private companion object {
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

data class AvailablePluginUpdate(
    val currentManifest: PluginManifest,
    val candidateManifest: PluginManifest,
    val currentTrust: PluginUpdateTrust,
    val candidateTrust: PluginUpdateTrust,
    val changelogUrl: String?,
    val installRequest: InstallRequest,
) {
    init {
        require(currentManifest.id == candidateManifest.id)
        require(candidateManifest.id == installRequest.metadata.pluginId)
        require(candidateManifest.version == installRequest.metadata.version)
        require(changelogUrl == null || changelogUrl.isHttpsUrl())
    }
}

private fun String.isHttpsUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.isAbsolute &&
        uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null
}.getOrDefault(false)

data class PreparedPluginUpdate(
    val pluginId: String,
    val version: String,
    val location: String,
    val signatureState: PackageSignatureState,
    val signerKeyId: String?,
    val signerFingerprintSha256: String?,
    internal val stagedPackage: StagedPluginPackage? = null,
)

interface PluginUpdateInstaller {
    suspend fun stage(request: InstallRequest): AppResult<PreparedPluginUpdate>

    suspend fun activate(prepared: PreparedPluginUpdate): AppResult<InstalledPlugin>

    suspend fun discard(prepared: PreparedPluginUpdate)
}

fun interface PluginContractSmokeTester {
    suspend fun verify(prepared: PreparedPluginUpdate): AppResult<Unit>
}

data class PluginUpdateResult(
    val decision: UpdateDecision,
    val capabilityDiff: CapabilityDiff,
    val changelogUrl: String?,
    val originChanged: Boolean,
    val installedPlugin: InstalledPlugin? = null,
)

class PluginUpdateService(
    private val update: AvailablePluginUpdate,
    private val installer: PluginUpdateInstaller,
    private val smokeTester: PluginContractSmokeTester,
) {
    suspend fun applyAvailableUpdate(mode: UpdateMode): AppResult<PluginUpdateResult> {
        val signerChanged = update.currentTrust.signatureState != update.candidateTrust.signatureState ||
            update.currentTrust.signerKeyId != update.candidateTrust.signerKeyId ||
            update.currentTrust.signerFingerprintSha256 !=
            update.candidateTrust.signerFingerprintSha256
        val originChanged = update.currentTrust.origin != update.candidateTrust.origin
        val capabilityDiff = CapabilityDiff.between(
            old = update.currentManifest,
            new = update.candidateManifest,
            signerChanged = signerChanged,
        )
        val decision = when {
            mode == UpdateMode.MANUAL -> UpdateDecision.MANUAL_REQUIRED
            mode == UpdateMode.ASK -> UpdateDecision.NEEDS_REVIEW
            capabilityDiff.expandsAccess || originChanged -> UpdateDecision.NEEDS_REVIEW
            else -> UpdateDecision.APPLIED
        }

        return if (decision == UpdateDecision.APPLIED) {
            applyUpdate(capabilityDiff, originChanged)
        } else {
            AppResult.Success(reviewResult(decision, capabilityDiff, originChanged))
        }
    }

    private suspend fun applyUpdate(
        capabilityDiff: CapabilityDiff,
        originChanged: Boolean,
    ): AppResult<PluginUpdateResult> = when (val staged = installer.stage(update.installRequest)) {
        is AppResult.Failure -> staged
        is AppResult.Success -> if (staged.value.matches(update.candidateTrust)) {
            smokeAndActivate(staged.value, capabilityDiff, originChanged)
        } else {
            installer.discard(staged.value)
            trustMismatch()
        }
    }

    private suspend fun smokeAndActivate(
        prepared: PreparedPluginUpdate,
        capabilityDiff: CapabilityDiff,
        originChanged: Boolean,
    ): AppResult<PluginUpdateResult> = try {
        handleSmokeResult(
            prepared = prepared,
            capabilityDiff = capabilityDiff,
            originChanged = originChanged,
            smoke = smokeTester.verify(prepared),
        )
    } catch (failure: CancellationException) {
        withContext(NonCancellable) { installer.discard(prepared) }
        throw failure
    } catch (_: Throwable) {
        withContext(NonCancellable) { installer.discard(prepared) }
        smokeFailure()
    }

    private suspend fun handleSmokeResult(
        prepared: PreparedPluginUpdate,
        capabilityDiff: CapabilityDiff,
        originChanged: Boolean,
        smoke: AppResult<Unit>,
    ): AppResult<PluginUpdateResult> = when (smoke) {
        is AppResult.Failure -> {
            installer.discard(prepared)
            smoke
        }
        is AppResult.Success -> when (val activation = installer.activate(prepared)) {
            is AppResult.Failure -> activation
            is AppResult.Success -> AppResult.Success(
                reviewResult(
                    decision = UpdateDecision.APPLIED,
                    capabilityDiff = capabilityDiff,
                    originChanged = originChanged,
                ).copy(installedPlugin = activation.value),
            )
        }
    }

    private fun reviewResult(
        decision: UpdateDecision,
        capabilityDiff: CapabilityDiff,
        originChanged: Boolean,
    ): PluginUpdateResult = PluginUpdateResult(
        decision = decision,
        capabilityDiff = capabilityDiff,
        changelogUrl = update.changelogUrl,
        originChanged = originChanged,
    )
}

private fun PreparedPluginUpdate.matches(trust: PluginUpdateTrust): Boolean =
    signatureState == trust.signatureState &&
        signerKeyId == trust.signerKeyId &&
        signerFingerprintSha256 == trust.signerFingerprintSha256

private fun trustMismatch(): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(
        code = "plugin.update_trust_mismatch",
        retryable = false,
    ),
)

private fun smokeFailure(): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(
        code = "plugin.contract_smoke_failed",
        retryable = false,
    ),
)
