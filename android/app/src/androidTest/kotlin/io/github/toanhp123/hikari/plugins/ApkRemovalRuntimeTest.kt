package io.github.toanhp123.hikari.plugins

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.test.InstrumentationTestCase
import android.test.mock.MockPackageManager
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.toanhp123.hikari.extensions.mihon.MihonExtensionRuntime
import java.io.File
import java.util.UUID

/** Authored inert sources; no network, third-party code, install or uninstall. */
class InertManagementSource : HttpSource() {
    override val name = "Inert management contract"
    override val lang = "en"
    override val baseUrl = "https://example.invalid"
    override val supportsLatest = false
}

@Suppress("DEPRECATION")
class ApkRemovalRuntimeTest : InstrumentationTestCase() {
    private lateinit var directory: File
    override fun setUp() {
        super.setUp()
        check(instrumentation.targetContext.packageName.endsWith(".localmediatest"))
        directory = File(instrumentation.targetContext.cacheDir, "removal-runtime-${System.nanoTime()}").apply { mkdirs() }
    }
    override fun tearDown() { directory.deleteRecursively(); super.tearDown() }
    private fun fixture(pkg: String): PackageInfo = instrumentation.targetContext.packageManager
        .getPackageInfo(instrumentation.targetContext.packageName, ApkInspector.flags()).apply {
            packageName = pkg; versionName = "1.6.10"; versionCode = 10
            if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode = 10
            reqFeatures = arrayOf(android.content.pm.FeatureInfo().apply { name = "tachiyomi.extension" })
            applicationInfo!!.metaData = android.os.Bundle().apply {
                putString("tachiyomi.extension.class", InertManagementSource::class.java.name)
                putString("tachiyomix.extensionLib", "1.6")
            }
        }

    fun testRemovalResultFreshInventoryRestartAcknowledgmentAndLateResult() {
        val real = instrumentation.targetContext
        var installed: PackageInfo? = fixture("org.example.removalcontract")
        val manager = object : MockPackageManager() {
            override fun getPackageInstaller() = real.packageManager.packageInstaller
            override fun getPackageInfo(pkg: String, flags: Int) = installed ?: throw PackageManager.NameNotFoundException(pkg)
        }
        val context = object : ContextWrapper(real) { override fun getPackageManager(): PackageManager = manager }
        val identity = requireNotNull(ApkInspector(context).identity(requireNotNull(installed)))
        val file = File(directory, "removal.json")
        val coordinator = ApkInstaller(context, file)
        rejects { coordinator.uninstall(identity.family, identity.packageName, 11, identity.signers.single()) }
        rejects { coordinator.uninstall(identity.family, identity.packageName, 10, "b".repeat(64)) }
        rejects { coordinator.uninstall(identity.family, real.packageName, 10, identity.signers.single()) }
        val first = coordinator.uninstall(identity.family, identity.packageName, 10, identity.signers.single())
        val ticket = coordinator.removalConfirmation(first.id)
        assertEquals(InstallStatus.AWAITING_RESULT, coordinator.operations().single().status) // Poll before main dispatch.
        var launches = 0
        instrumentation.runOnMainSync {
            val activity = object : Activity() {
                override fun startActivityForResult(intent: android.content.Intent, requestCode: Int) { launches++ }
            }
            ticket.launch(activity)
            rejects { ticket.launch(activity) }
        }
        assertEquals(1, launches)
        assertEquals(InstallStatus.AWAITING_RESULT, coordinator.operations().single().status) // Poll while confirmation open.
        instrumentation.runOnMainSync { rejects { ticket.launch(Activity()) } }
        rejects { coordinator.uninstall(identity.family, identity.packageName, 10, identity.signers.single()) }
        val reopened = ApkInstaller(context, file)
        assertEquals(InstallStatus.INTERRUPTED, reopened.operations().single().status)
        rejects { reopened.removalConfirmation(first.id) } // Restart never replays confirmation.
        rejects { reopened.retire(first.id, false) }
        val retired = reopened.retire(first.id, false, acknowledgeUnrevokedRemoval = true)
        assertEquals(InstallStatus.RETIRED_UNKNOWN, retired.status)
        val second = reopened.uninstall(identity.family, identity.packageName, 10, identity.signers.single())
        reopened.uninstallResult(requireNotNull(first.requestCode), Activity.RESULT_OK)
        assertEquals(retired, ApkInstallJournal(file).find(first.id))
        assertEquals(second, ApkInstallJournal(file).find(second.id))
        reopened.removalConfirmation(second.id)
        reopened.uninstallResult(requireNotNull(second.requestCode), Activity.RESULT_OK)
        assertEquals(InstallStatus.FAILED, ApkInstallJournal(file).find(second.id).status) // Result alone insufficient.
        val third = reopened.uninstall(identity.family, identity.packageName, 10, identity.signers.single())
        val thirdTicket = reopened.removalConfirmation(third.id)
        installed = null
        assertEquals(InstallStatus.AWAITING_RESULT, reopened.operations().last().status) // Absence alone never clears pending action.
        reopened.uninstallResult(requireNotNull(third.requestCode), Activity.RESULT_OK)
        assertEquals(InstallStatus.SUCCEEDED, ApkInstallJournal(file).find(third.id).status)
        instrumentation.runOnMainSync { rejects { thirdTicket.launch(Activity()) } }
        installed = fixture(identity.packageName)
        val cancelled = reopened.uninstall(identity.family, identity.packageName, 10, identity.signers.single())
        reopened.removalConfirmation(cancelled.id)
        reopened.uninstallResult(requireNotNull(cancelled.requestCode), Activity.RESULT_CANCELED)
        assertEquals(InstallStatus.CANCELLED, ApkInstallJournal(file).find(cancelled.id).status)
        val external = reopened.uninstall(identity.family, identity.packageName, 10, identity.signers.single())
        reopened.removalConfirmation(external.id)
        installed = null
        reopened.uninstallResult(requireNotNull(external.requestCode), Activity.RESULT_CANCELED)
        assertEquals(InstallStatus.EXTERNALLY_REMOVED, ApkInstallJournal(file).find(external.id).status)
        installed = fixture(identity.packageName).apply { reqFeatures = emptyArray() }
        rejects { reopened.uninstall(identity.family, identity.packageName, 10, identity.signers.single()) }
    }

    fun testRuntimeSnapshotDoesNotLoadAndLoadedRevisionSurvivesInventoryChange() {
        val real = instrumentation.targetContext
        val first = fixture("org.example.loadedcontract")
        val collision = fixture("org.example.collisioncontract")
        val incompatible = fixture("org.example.incompatiblecontract").apply { versionName = "9.0.1"; applicationInfo!!.metaData!!.putString("tachiyomix.extensionLib", "9.0") }
        val failed = fixture("org.example.failurecontract").apply { applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", "missing.InertClass") }
        var packages = listOf(first, collision, incompatible, failed)
        val manager = object : MockPackageManager() {
            override fun getInstalledPackages(flags: Int) = packages
            override fun getPackageInfo(pkg: String, flags: Int) = packages.firstOrNull { it.packageName == pkg }
                ?: throw PackageManager.NameNotFoundException(pkg)
        }
        val context = object : ContextWrapper(real) {
            override fun getPackageManager(): PackageManager = manager
            override fun getClassLoader() = InertManagementSource::class.java.classLoader!!
        }
        listOf(first, collision, failed).forEach { ApkTrustStore.get(real).approve(requireNotNull(ApkInspector(context).identity(it))) }
        val runtime = MihonExtensionRuntime(context)
        assertEquals(false, runtime.runtimeSnapshot()["initialized"])
        assertEquals(emptyList<Any>(), runtime.runtimeSnapshot()["sources"])
        val loaded = runtime.listSources().single()
        assertEquals(10L, loaded["versionCode"])
        assertEquals(false, loaded["preferencesSupported"])
        val snapshot = runtime.runtimeSnapshot()
        val errors = snapshot["errors"] as List<*>
        assertTrue(errors.any { (it as Map<*, *>)["reason"] == "collision" })
        assertTrue(errors.any { (it as Map<*, *>)["reason"] == "incompatible" })
        assertTrue(errors.any { (it as Map<*, *>)["reason"] == "load_failure" })
        packages = emptyList()
        assertEquals(listOf(loaded), runtime.listSources())
        assertEquals(snapshot, runtime.runtimeSnapshot())
        assertSame(MihonExtensionRuntime.get(real), MihonExtensionRuntime.get(real))
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
}
