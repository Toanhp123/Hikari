package app.openstory.plugin.host.install

import android.content.Context
import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.registry.PluginRegistration
import app.openstory.plugin.host.registry.PluginRegistry
import app.openstory.plugin.host.update.AvailablePluginUpdate
import app.openstory.plugin.host.update.PluginContractSmokeTester
import app.openstory.plugin.host.update.PluginUpdateInstaller
import app.openstory.plugin.host.update.PluginUpdateService
import app.openstory.plugin.host.update.UpdateMode
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Immutable package metadata for a plugin shipped inside the signed APK.
 *
 * The [installRequest] still goes through [PluginInstaller], so app-bundled packages do
 * not bypass package hashing, archive/layout validation, selector contract validation,
 * version policy, or immutable staging.
 */
data class BundledPluginPackage(
    val pluginId: String,
    val version: String,
    val installRequest: InstallRequest,
) {
    init {
        require(pluginId == installRequest.metadata.pluginId) {
            "Bundled plugin ID must match install metadata."
        }
        require(version == installRequest.metadata.version) {
            "Bundled plugin version must match install metadata."
        }
    }
}

fun interface BundledPluginAssets {
    suspend fun packages(): List<BundledPluginPackage>
}

/**
 * Upgrade boundary for already-installed bundled plugins.
 *
 * A bootstrapper never calls [PluginInstaller.install] directly for a newer version.
 * Production wiring should use [PluginUpdateServiceBundledCoordinator] so capability,
 * signer, and origin changes use the same review policy as community updates.
 */
fun interface BundledPluginUpdateCoordinator {
    suspend fun apply(
        current: PluginRegistration,
        candidate: BundledPluginPackage,
    ): AppResult<Unit>
}

fun interface BundledAvailableUpdateFactory {
    suspend fun create(
        current: PluginRegistration,
        candidate: BundledPluginPackage,
    ): AppResult<AvailablePluginUpdate>
}

class PluginUpdateServiceBundledCoordinator(
    private val availableUpdateFactory: BundledAvailableUpdateFactory,
    private val installer: PluginUpdateInstaller,
    private val smokeTester: PluginContractSmokeTester,
    private val mode: UpdateMode = UpdateMode.AUTOMATIC,
) : BundledPluginUpdateCoordinator {
    override suspend fun apply(
        current: PluginRegistration,
        candidate: BundledPluginPackage,
    ): AppResult<Unit> = when (
        val available = availableUpdateFactory.create(current, candidate)
    ) {
        is AppResult.Failure -> available
        is AppResult.Success -> when (
            val result = PluginUpdateService(
                update = available.value,
                installer = installer,
                smokeTester = smokeTester,
            ).applyAvailableUpdate(mode)
        ) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(Unit)
        }
    }
}

class BundledPluginBootstrapper(
    private val assets: BundledPluginAssets,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
    private val updateCoordinator: BundledPluginUpdateCoordinator,
    private val versionPolicy: PluginVersionPolicy = PluginVersionPolicy(),
) {
    suspend fun ensureInstalled(): AppResult<Unit> {
        for (candidate in assets.packages()) {
            when (val result = ensurePackage(candidate)) {
                is AppResult.Failure -> return result
                is AppResult.Success -> Unit
            }
        }
        return AppResult.Success(Unit)
    }

    private suspend fun ensurePackage(
        candidate: BundledPluginPackage,
    ): AppResult<Unit> = when (val current = registry.find(candidate.pluginId)) {
        null -> installer.install(candidate.installRequest).toUnitResult()
        else -> when (val activeVersion = current.activeVersion) {
            null -> installer.install(candidate.installRequest).toUnitResult()
            candidate.version -> AppResult.Success(Unit)
            else -> when (
                val versionResult = versionPolicy.validateInstall(
                    candidateVersion = candidate.version,
                    activeVersion = activeVersion,
                )
            ) {
                is AppResult.Success -> updateCoordinator.apply(current, candidate)
                is AppResult.Failure -> if (
                    versionResult.error.code == DOWNGRADE_DENIED_CODE
                ) {
                    // A user may already have a newer community/repository release installed.
                    // Never replace it with the older copy shipped by this APK.
                    AppResult.Success(Unit)
                } else {
                    versionResult
                }
            }
        }
    }
}

data class BundledPluginAssetDescriptor(
    val assetPath: String,
    val pluginId: String,
    val version: String,
    val exactPackageSha256: String,
    val acceptedCapabilities: Set<PluginCapability>,
) {
    init {
        require(assetPath.isNotBlank() && !assetPath.startsWith('/'))
        require(exactPackageSha256.matches(SHA_256_PATTERN))
    }
}

/** Reads pinned package bytes from Android assets without trusting bytes discovered at runtime. */
class AndroidBundledPluginAssets(
    context: Context,
    private val descriptors: List<BundledPluginAssetDescriptor>,
    private val ioDispatcher: CoroutineDispatcher,
) : BundledPluginAssets {
    private val assets = context.applicationContext.assets

    override suspend fun packages(): List<BundledPluginPackage> =
        withContext(ioDispatcher) {
            descriptors.map { descriptor ->
                descriptor.readPackage()
            }
        }

    private fun BundledPluginAssetDescriptor.readPackage(): BundledPluginPackage {
        val packageBytes = assets.open(assetPath).use { it.readBytes() }
        val actualSha256 = packageBytes.sha256()

        check(actualSha256 == exactPackageSha256) {
            "Bundled plugin asset checksum mismatch."
        }

        return BundledPluginPackage(
            pluginId = pluginId,
            version = version,
            installRequest = InstallRequest(
                packageBytes = packageBytes,
                metadata = PluginPackageMetadata(
                    pluginId = pluginId,
                    version = version,
                    exactPackageSha256 = exactPackageSha256,
                    signature = null,
                ),
                provenance = PackageInstallProvenance(
                    source = PackageInstallSource.LOCAL_FILE,
                    sourceReference = "asset://$assetPath",
                    signatureState = PackageSignatureState.UNSIGNED,
                    // The package is pinned by SHA-256 and distributed inside the signed APK.
                    // This acknowledges package-format unsigned state without skipping validation.
                    unsignedWarningAcknowledged = true,
                ),
                acceptedCapabilities = acceptedCapabilities,
            ),
        )
    }
}

object DefaultCatalogBundledPlugin {
    const val PLUGIN_ID = "org.openstory.catalog.default"
    const val VERSION = "1.0.0"
    const val ASSET_PATH = "plugins/default-catalog.osp"

    // Pinned digest of the deterministic asset committed by Wave 05 Task 02.
    const val PACKAGE_SHA_256 = "14691eadcbfc72504252d0c68430a99dd3a12198a969f900bbdda99c9791418b"

    val descriptor: BundledPluginAssetDescriptor
        get() = BundledPluginAssetDescriptor(
            assetPath = ASSET_PATH,
            pluginId = PLUGIN_ID,
            version = VERSION,
            exactPackageSha256 = PACKAGE_SHA_256,
            acceptedCapabilities = setOf(PluginCapability.NETWORK),
        )
}

private fun <T> AppResult<T>.toUnitResult(): AppResult<Unit> = when (this) {
    is AppResult.Success -> AppResult.Success(Unit)
    is AppResult.Failure -> this
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK)
        }

private const val DOWNGRADE_DENIED_CODE = "plugin.package_downgrade_denied"
private const val UNSIGNED_BYTE_MASK = 0xff
private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
