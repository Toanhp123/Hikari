package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.packageformat.PackageArchiveEntry
import app.openstory.plugin.api.packageformat.PackageInstallProvenance
import app.openstory.plugin.api.packageformat.PackageInstallSource
import app.openstory.plugin.api.packageformat.PackageSignatureState
import app.openstory.plugin.api.packageformat.PluginPackageMetadata
import app.openstory.plugin.host.registry.ActivatedPlugin
import app.openstory.plugin.host.registry.MutablePluginRegistry
import app.openstory.plugin.host.registry.PluginActivation
import app.openstory.plugin.host.registry.PluginRegistration
import app.openstory.plugin.host.update.AvailablePluginUpdate
import app.openstory.plugin.host.update.PluginContractSmokeTester
import app.openstory.plugin.host.update.PluginUpdateTrust
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class BundledPluginBootstrapperTest {
    @Test
    fun bootstrapIsIdempotentAndPreservesDisabledState() = runTest {
        val fixture = bundledBootstrapFixture(version = "1.0.0")

        assertIs<AppResult.Success<Unit>>(fixture.bootstrapper.ensureInstalled())
        assertEquals(1, fixture.storage.stageCalls)
        assertEquals(true, fixture.registry.find(DEFAULT_PLUGIN_ID)!!.enabled)

        assertIs<AppResult.Success<Unit>>(
            fixture.registry.setEnabled(DEFAULT_PLUGIN_ID, false),
        )
        assertIs<AppResult.Success<Unit>>(fixture.bootstrapper.ensureInstalled())

        assertFalse(fixture.registry.find(DEFAULT_PLUGIN_ID)!!.enabled)
        assertEquals(1, fixture.storage.stageCalls)
        assertEquals(0, fixture.updates.calls)
    }

    @Test
    fun newerBundledVersionDelegatesToUpdateCoordinatorInsteadOfDirectInstall() = runTest {
        val fixture = bundledBootstrapFixture(
            version = "2.0.0",
            initialRegistration = PluginRegistration(
                pluginId = DEFAULT_PLUGIN_ID,
                enabled = false,
                activeVersion = "1.0.0",
                previousVersion = null,
            ),
        )

        assertIs<AppResult.Success<Unit>>(fixture.bootstrapper.ensureInstalled())

        assertEquals(1, fixture.updates.calls)
        assertEquals("2.0.0", fixture.updates.lastCandidateVersion)
        assertEquals(0, fixture.storage.stageCalls)
        assertFalse(fixture.registry.find(DEFAULT_PLUGIN_ID)!!.enabled)
    }

    @Test
    fun normalUpdatePathPreservesDisabledStateAfterBundledUpgrade() = runTest {
        val initial = PluginRegistration(
            pluginId = DEFAULT_PLUGIN_ID,
            enabled = false,
            activeVersion = "1.0.0",
            previousVersion = null,
        )
        val registry = RecordingBundledRegistry(initial)
        val storage = RecordingBundledStorage()
        val installer = installerFor(
            version = "2.0.0",
            storage = storage,
            registry = registry,
        )
        val candidate = bundledPackage(version = "2.0.0")
        val smokeTester = RecordingSmokeTester()
        val coordinator = normalUpdateCoordinator(
            installer = installer,
            currentVersion = "1.0.0",
            candidate = candidate,
            expandedAccess = false,
            smokeTester = smokeTester,
        )
        val bootstrapper = BundledPluginBootstrapper(
            assets = BundledPluginAssets { listOf(candidate) },
            registry = registry,
            installer = installer,
            updateCoordinator = coordinator,
        )

        assertIs<AppResult.Success<Unit>>(bootstrapper.ensureInstalled())

        val registration = registry.find(DEFAULT_PLUGIN_ID)!!
        assertEquals("2.0.0", registration.activeVersion)
        assertEquals("1.0.0", registration.previousVersion)
        assertFalse(registration.enabled)
        assertEquals(1, storage.stageCalls)
        assertEquals(1, smokeTester.calls)
    }

    @Test
    fun expandedAccessOnBundledUpgradeRequiresReviewAndDoesNotStage() = runTest {
        val initial = PluginRegistration(
            pluginId = DEFAULT_PLUGIN_ID,
            enabled = true,
            activeVersion = "1.0.0",
            previousVersion = null,
        )
        val registry = RecordingBundledRegistry(initial)
        val storage = RecordingBundledStorage()
        val installer = installerFor(
            version = "2.0.0",
            storage = storage,
            registry = registry,
        )
        val candidate = bundledPackage(version = "2.0.0")
        val smokeTester = RecordingSmokeTester()
        val coordinator = normalUpdateCoordinator(
            installer = installer,
            currentVersion = "1.0.0",
            candidate = candidate,
            expandedAccess = true,
            smokeTester = smokeTester,
        )
        val bootstrapper = BundledPluginBootstrapper(
            assets = BundledPluginAssets { listOf(candidate) },
            registry = registry,
            installer = installer,
            updateCoordinator = coordinator,
        )

        assertIs<AppResult.Success<Unit>>(bootstrapper.ensureInstalled())

        assertEquals("1.0.0", registry.find(DEFAULT_PLUGIN_ID)!!.activeVersion)
        assertEquals(0, storage.stageCalls)
        assertEquals(0, smokeTester.calls)
    }

    @Test
    fun olderBundledVersionNeverDowngradesNewerInstalledVersion() = runTest {
        val fixture = bundledBootstrapFixture(
            version = "1.0.0",
            initialRegistration = PluginRegistration(
                pluginId = DEFAULT_PLUGIN_ID,
                enabled = true,
                activeVersion = "2.0.0",
                previousVersion = "1.0.0",
            ),
        )

        assertIs<AppResult.Success<Unit>>(fixture.bootstrapper.ensureInstalled())

        assertEquals(0, fixture.storage.stageCalls)
        assertEquals(0, fixture.updates.calls)
        assertEquals("2.0.0", fixture.registry.find(DEFAULT_PLUGIN_ID)!!.activeVersion)
    }
}

private data class BundledBootstrapFixture(
    val bootstrapper: BundledPluginBootstrapper,
    val registry: RecordingBundledRegistry,
    val storage: RecordingBundledStorage,
    val updates: RecordingBundledUpdateCoordinator,
)

private fun bundledBootstrapFixture(
    version: String,
    initialRegistration: PluginRegistration? = null,
): BundledBootstrapFixture {
    val registry = RecordingBundledRegistry(initialRegistration)
    val storage = RecordingBundledStorage()
    val installer = installerFor(
        version = version,
        storage = storage,
        registry = registry,
    )
    val updates = RecordingBundledUpdateCoordinator()
    val candidate = bundledPackage(version)
    val assets = BundledPluginAssets { listOf(candidate) }

    return BundledBootstrapFixture(
        bootstrapper = BundledPluginBootstrapper(
            assets = assets,
            registry = registry,
            installer = installer,
            updateCoordinator = updates,
        ),
        registry = registry,
        storage = storage,
        updates = updates,
    )
}

private fun installerFor(
    version: String,
    storage: RecordingBundledStorage,
    registry: RecordingBundledRegistry,
): PluginInstaller = PluginInstaller(
    verifier = PackageVerifier(
        archiveInspector = PackageArchiveInspector {
            AppResult.Success(
                PluginPackageInspection(
                    pluginId = DEFAULT_PLUGIN_ID,
                    version = version,
                    entries = listOf(
                        PackageArchiveEntry(
                            path = "manifest.json",
                            compressedSizeBytes = 1,
                            uncompressedSizeBytes = 1,
                            isSymbolicLink = false,
                            isExecutable = false,
                        ),
                        PackageArchiveEntry(
                            path = "selector.json",
                            compressedSizeBytes = 1,
                            uncompressedSizeBytes = 1,
                            isSymbolicLink = false,
                            isExecutable = false,
                        ),
                    ),
                    declaredExecutableEntries = setOf("selector.json"),
                    declaredCapabilities = setOf(PluginCapability.NETWORK),
                ),
            )
        },
    ),
    storage = storage,
    registry = registry,
)

private fun bundledPackage(version: String): BundledPluginPackage =
    BundledPluginPackage(
        pluginId = DEFAULT_PLUGIN_ID,
        version = version,
        installRequest = installRequest(version),
    )

private fun normalUpdateCoordinator(
    installer: PluginInstaller,
    currentVersion: String,
    candidate: BundledPluginPackage,
    expandedAccess: Boolean,
    smokeTester: PluginContractSmokeTester,
): BundledPluginUpdateCoordinator = PluginUpdateServiceBundledCoordinator(
    availableUpdateFactory = BundledAvailableUpdateFactory { _, _ ->
        AppResult.Success(
            AvailablePluginUpdate(
                currentManifest = manifest(
                    version = currentVersion,
                    expandedAccess = false,
                ),
                candidateManifest = manifest(
                    version = candidate.version,
                    expandedAccess = expandedAccess,
                ),
                currentTrust = updateTrust(),
                candidateTrust = updateTrust(),
                changelogUrl = null,
                installRequest = candidate.installRequest,
            ),
        )
    },
    installer = installer,
    smokeTester = smokeTester,
)

private fun manifest(
    version: String,
    expandedAccess: Boolean,
): PluginManifest = PluginManifest(
    id = DEFAULT_PLUGIN_ID,
    name = "Default Catalog",
    version = version,
    packageChecksumSha256 = "0".repeat(64),
    minimumHostVersion = "1.0.0",
    updateUrl = "https://catalog.openstory.example/plugin.json",
    api = PluginApiVersion(1, 0),
    kinds = setOf(PluginKind.CATALOG),
    languages = setOf("en"),
    allowedHosts = if (expandedAccess) {
        setOf("catalog.openstory.example", "images.openstory.example")
    } else {
        setOf("catalog.openstory.example")
    },
    capabilities = setOf(PluginCapability.NETWORK),
    runtime = PluginRuntime.DECLARATIVE,
    entry = "selector.json",
    declarativeOrigin = "https://catalog.openstory.example/",
)

private fun updateTrust(): PluginUpdateTrust = PluginUpdateTrust(
    signatureState = PackageSignatureState.UNSIGNED,
    signerKeyId = null,
    signerFingerprintSha256 = null,
    origin = "asset://plugins/default-catalog.osp",
)

private class RecordingSmokeTester : PluginContractSmokeTester {
    var calls: Int = 0
        private set

    override suspend fun verify(
        prepared: app.openstory.plugin.host.update.PreparedPluginUpdate,
    ): AppResult<Unit> {
        calls += 1
        return AppResult.Success(Unit)
    }
}

private class RecordingBundledStorage : PluginPackageStorage {
    var stageCalls: Int = 0
        private set

    override suspend fun stage(
        verifiedPackage: VerifiedPluginPackage,
    ): AppResult<StagedPluginPackage> {
        stageCalls += 1
        return AppResult.Success(
            StagedPluginPackage(
                pluginId = verifiedPackage.pluginId,
                version = verifiedPackage.version,
                location = "plugins/${verifiedPackage.pluginId}/${verifiedPackage.version}",
                packageSha256 = verifiedPackage.packageSha256,
                signatureDecision = verifiedPackage.signatureDecision,
                provenance = verifiedPackage.provenance,
                acceptedCapabilities = verifiedPackage.acceptedCapabilities,
            ),
        )
    }

    override suspend fun remove(location: String) = Unit
}

private class RecordingBundledRegistry(
    initialRegistration: PluginRegistration?,
) : MutablePluginRegistry {
    private var registration = initialRegistration

    override suspend fun find(pluginId: String): PluginRegistration? =
        registration?.takeIf { it.pluginId == pluginId }

    override suspend fun activate(
        activation: PluginActivation,
    ): AppResult<ActivatedPlugin> {
        val previous = registration
        val enabled = previous?.enabled ?: true
        registration = PluginRegistration(
            pluginId = activation.pluginId,
            enabled = enabled,
            activeVersion = activation.version,
            previousVersion = previous?.activeVersion,
        )
        return AppResult.Success(
            ActivatedPlugin(
                pluginId = activation.pluginId,
                version = activation.version,
                location = activation.location,
                enabled = enabled,
            ),
        )
    }

    override suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ): AppResult<Unit> {
        val current = checkNotNull(registration)
        registration = current.copy(enabled = enabled)
        return AppResult.Success(Unit)
    }
}

private class RecordingBundledUpdateCoordinator : BundledPluginUpdateCoordinator {
    var calls: Int = 0
        private set
    var lastCandidateVersion: String? = null
        private set

    override suspend fun apply(
        current: PluginRegistration,
        candidate: BundledPluginPackage,
    ): AppResult<Unit> {
        calls += 1
        lastCandidateVersion = candidate.version
        return AppResult.Success(Unit)
    }
}

private fun installRequest(version: String): InstallRequest {
    val packageBytes = "bundled-$version".encodeToByteArray()
    return InstallRequest(
        packageBytes = packageBytes,
        metadata = PluginPackageMetadata(
            pluginId = DEFAULT_PLUGIN_ID,
            version = version,
            exactPackageSha256 = sha256(packageBytes),
            signature = null,
        ),
        provenance = PackageInstallProvenance(
            source = PackageInstallSource.LOCAL_FILE,
            sourceReference = "asset://plugins/default-catalog.osp",
            signatureState = PackageSignatureState.UNSIGNED,
            unsignedWarningAcknowledged = true,
        ),
        acceptedCapabilities = setOf(PluginCapability.NETWORK),
    )
}

private fun sha256(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private const val DEFAULT_PLUGIN_ID = "org.openstory.catalog.default"
