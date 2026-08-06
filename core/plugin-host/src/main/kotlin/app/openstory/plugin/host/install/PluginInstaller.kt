package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.registry.MutablePluginRegistry
import java.math.BigInteger

data class InstallRequest(
    val packageBytes: ByteArray,
    val metadata: PluginPackageMetadata,
    val provenance: PackageInstallProvenance,
    val acceptedCapabilities:
        Set<PluginCapability> =
        emptySet(),
)

data class VerifiedPluginPackage(
    val packageBytes: ByteArray,
    val packageSha256: String,
    val pluginId: String,
    val version: String,
    val signatureDecision: PackageSignatureDecision,
    val provenance: PackageInstallProvenance,
    val acceptedCapabilities:
        Set<PluginCapability> =
        emptySet(),
)

data class StagedPluginPackage(
    val pluginId: String,
    val version: String,
    val location: String,
    val packageSha256: String,
    val signatureDecision: PackageSignatureDecision,
    val provenance: PackageInstallProvenance,
    val acceptedCapabilities:
        Set<PluginCapability> =
        emptySet(),
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

    suspend fun remove(
        location: String,
    )
}

class PluginInstaller(
    private val verifier: PackageVerifier,
    private val storage: PluginPackageStorage,
    private val registry: MutablePluginRegistry,
    private val versionPolicy:
        PluginVersionPolicy =
        PluginVersionPolicy(),
) {

    suspend fun install(
        request: InstallRequest,
    ): AppResult<InstalledPlugin> =
        when (
            val verificationResult =
                verifier.verify(request)
        ) {
            is AppResult.Failure ->
                verificationResult

            is AppResult.Success ->
                installVerified(
                    verifiedPackage =
                        verificationResult.value,
                )
        }

    private suspend fun installVerified(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<InstalledPlugin> {
        val registration =
            registry.find(
                verifiedPackage.pluginId,
            )

        return when (
            val versionResult =
                versionPolicy.validateInstall(
                    candidateVersion =
                        verifiedPackage.version,
                    activeVersion =
                        registration?.activeVersion,
                )
        ) {
            is AppResult.Failure ->
                versionResult

            is AppResult.Success ->
                stageAndActivate(
                    verifiedPackage =
                        verifiedPackage,
                )
        }
    }

    private suspend fun stageAndActivate(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<InstalledPlugin> =
        when (
            val stagingResult =
                storage.stage(
                    verifiedPackage,
                )
        ) {
            is AppResult.Failure ->
                stagingResult

            is AppResult.Success ->
                activateOrRemove(
                    stagedPackage =
                        stagingResult.value,
                )
        }

    private suspend fun activateOrRemove(
        stagedPackage: StagedPluginPackage,
    ): AppResult<InstalledPlugin> =
        when (
            val activationResult =
                registry.activate(
                    stagedPackage,
                )
        ) {
            is AppResult.Success ->
                activationResult

            is AppResult.Failure -> {
                storage.remove(
                    stagedPackage.location,
                )

                activationResult
            }
        }
}

class PluginVersionPolicy {

    fun validateInstall(
        candidateVersion: String,
        activeVersion: String?,
    ): AppResult<Unit> {
        val candidate =
            SemanticVersion.parse(
                candidateVersion,
            )

        val active =
            activeVersion?.let(
                SemanticVersion::parse,
            )

        return when {
            candidate == null ->
                invalidVersion()

            activeVersion != null &&
                active == null ->
                invalidVersion()

            active != null &&
                candidate < active ->
                downgradeDenied()

            else ->
                AppResult.Success(
                    Unit,
                )
        }
    }
}

private data class SemanticVersion(
    val major: BigInteger,
    val minor: BigInteger,
    val patch: BigInteger,
    val preRelease: List<String>,
) : Comparable<SemanticVersion> {

    override fun compareTo(
        other: SemanticVersion,
    ): Int {
        val coreComparison =
            compareValuesBy(
                this,
                other,
                SemanticVersion::major,
                SemanticVersion::minor,
                SemanticVersion::patch,
            )

        return if (coreComparison != 0) {
            coreComparison
        } else {
            comparePreRelease(
                left = preRelease,
                right = other.preRelease,
            )
        }
    }

    companion object {

        fun parse(
            value: String,
        ): SemanticVersion? {
            val match =
                VERSION_PATTERN.matchEntire(
                    value,
                )

            return match?.let {
                runCatching {
                    SemanticVersion(
                        major =
                            match.groupValues[1]
                                .toBigInteger(),
                        minor =
                            match.groupValues[2]
                                .toBigInteger(),
                        patch =
                            match.groupValues[3]
                                .toBigInteger(),
                        preRelease =
                            match.groupValues[4]
                                .takeIf(
                                    String::isNotEmpty,
                                )
                                ?.split('.')
                                .orEmpty(),
                    )
                }.getOrNull()
            }
        }

        private val VERSION_PATTERN =
            Regex(
                """(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?""",
            )
    }
}

private fun comparePreRelease(
    left: List<String>,
    right: List<String>,
): Int {
    var result = 0

    when {
        left.isEmpty() &&
            right.isNotEmpty() ->
            result = 1

        left.isNotEmpty() &&
            right.isEmpty() ->
            result = -1

        else -> {
            val maximumSize =
                maxOf(
                    left.size,
                    right.size,
                )

            var index = 0

            while (
                index < maximumSize &&
                result == 0
            ) {
                result =
                    comparePreReleaseIdentifier(
                        left =
                            left.getOrNull(index),
                        right =
                            right.getOrNull(index),
                    )

                index += 1
            }
        }
    }

    return result
}

private fun comparePreReleaseIdentifier(
    left: String?,
    right: String?,
): Int =
    when {
        left == null ->
            -1

        right == null ->
            1

        left.all(Char::isDigit) &&
            right.all(Char::isDigit) ->
            left.toBigInteger()
                .compareTo(
                    right.toBigInteger(),
                )

        left.all(Char::isDigit) ->
            -1

        right.all(Char::isDigit) ->
            1

        else ->
            left.compareTo(
                right,
            )
    }

private fun invalidVersion():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_version_invalid",
            retryable = false,
        ),
    )

private fun downgradeDenied():
    AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code =
                "plugin.package_downgrade_denied",
            retryable = false,
        ),
    )
