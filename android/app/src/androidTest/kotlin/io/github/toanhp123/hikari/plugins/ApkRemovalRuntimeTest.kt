package io.github.toanhp123.hikari.plugins

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.test.InstrumentationTestCase
import android.test.mock.MockPackageManager
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import io.github.toanhp123.hikari.extensions.mihon.MihonExtensionRuntime
import java.io.File
import java.util.UUID

/** Authored inert sources; no network, third-party code, install or uninstall. */
class InertManagementSource : HttpSource() {
    override val name = "Inert management contract"
    override val lang = "en"
    override val baseUrl = "https://example.invalid"
    override val supportsLatest = false
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        error("Inert search failure")
}

class InertFactorySource : HttpSource() {
    override val name = "Inert factory contract"
    override val lang = "en"
    override val baseUrl = "https://example.invalid"
    override val supportsLatest = false
}

class InertManagementFactory : SourceFactory {
    override fun createSources() = listOf(InertManagementSource(), InertFactorySource())
}

class InertFailingSource : HttpSource() {
    init { error("Inert initialization failure") }
    override val name = "Inert failing contract"
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
    private fun fixture(pkg: String): PackageInfo {
        val real = instrumentation.targetContext
        val original = real.packageManager.getPackageInfo(real.packageName, ApkInspector.flags())
        // PackageManager may return a cached object; each authored package needs independent metadata.
        val parcel = android.os.Parcel.obtain()
        val copy = try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            PackageInfo.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
        return copy.apply {
            packageName = pkg; versionName = "1.6.10"; versionCode = 10
            if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode = 10
            reqFeatures = arrayOf(android.content.pm.FeatureInfo().apply { name = "tachiyomi.extension" })
            applicationInfo!!.sourceDir = this@ApkRemovalRuntimeTest.instrumentation.context.applicationInfo.sourceDir
            applicationInfo!!.metaData = android.os.Bundle().apply {
                putString("tachiyomi.extension.class", InertManagementSource::class.java.name)
                putString("tachiyomix.extensionLib", "1.6")
            }
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
        val incompatible = fixture("org.example.incompatiblecontract").apply { versionName = "9.0.1"; applicationInfo!!.metaData!!.putString("tachiyomix.extensionLib", "9.0") }
        val failed = fixture("org.example.failurecontract").apply { applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", "missing.InertClass") }
        val initialization = fixture("org.example.initfailurecontract").apply {
            applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", InertFailingSource::class.java.name)
        }
        var packages = emptyList<PackageInfo>()
        val manager = object : MockPackageManager() {
            override fun getInstalledPackages(flags: Int) = packages
            override fun getInstalledPackages(flags: PackageManager.PackageInfoFlags) = packages
            override fun getPackageInfo(pkg: String, flags: Int) = packages.firstOrNull { it.packageName == pkg }
                ?: throw PackageManager.NameNotFoundException(pkg)
        }
        val context = object : ContextWrapper(real) {
            override fun getPackageManager(): PackageManager = manager
            override fun getClassLoader() = InertManagementSource::class.java.classLoader!!
        }
        val empty = MihonExtensionRuntime(context)
        assertEquals(emptyList<Any>(), empty.listSources())
        assertEquals(true, empty.runtimeSnapshot()["initialized"])
        packages = listOf(first, incompatible, failed, initialization)
        listOf(first, failed, initialization).forEach {
            ApkTrustStore.get(real).approve(requireNotNull(ApkInspector(context).identity(it)))
        }
        val runtime = MihonExtensionRuntime(context)
        assertEquals(false, runtime.runtimeSnapshot()["initialized"])
        assertEquals(emptyList<Any>(), runtime.runtimeSnapshot()["sources"])
        val listed = runtime.listSources()
        assertEquals("Runtime snapshot: ${runtime.runtimeSnapshot()}", 1, listed.size)
        val loaded = listed.single()
        assertEquals(10L, loaded["versionCode"])
        assertEquals(false, loaded["preferencesSupported"])
        val snapshot = runtime.runtimeSnapshot()
        val errors = snapshot["errors"] as List<*>
        assertTrue(errors.any { (it as Map<*, *>)["reason"] == "incompatible" })
        assertTrue(errors.any { (it as Map<*, *>)["reason"] == "load_failure" })
        assertTrue(errors.any { (it as Map<*, *>)["packageName"] == initialization.packageName && it["reason"] == "load_failure" })
        runBlocking {
            try {
                runtime.search(loaded["sourceKey"] as String, "one", 1)
                fail("Expected source search failure")
            } catch (error: IllegalStateException) {
                assertEquals("Inert search failure", error.message)
            }
            try {
                runtime.search("stale-source", "one", 1)
                fail("Expected missing source failure")
            } catch (error: IllegalStateException) {
                assertEquals("Extension source stale-source is not available.", error.message)
            }
        }
        packages = emptyList()
        assertEquals(listOf(loaded), runtime.listSources())
        assertEquals(snapshot, runtime.runtimeSnapshot())
        val restarted = MihonExtensionRuntime(context)
        assertEquals(false, restarted.runtimeSnapshot()["initialized"])
        assertEquals(emptyList<Any>(), restarted.listSources())
        assertEquals(emptyList<Any>(), restarted.runtimeSnapshot()["errors"])
        val factory = fixture("org.example.factorycontract").apply {
            applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", InertManagementFactory::class.java.name)
        }
        packages = listOf(factory)
        ApkTrustStore.get(real).approve(requireNotNull(ApkInspector(context).identity(factory)))
        val factoryRuntime = MihonExtensionRuntime(context)
        assertEquals(2, factoryRuntime.listSources().size)
        assertEquals(emptyList<Any>(), factoryRuntime.runtimeSnapshot()["errors"])
        val second = fixture("org.example.secondcontract").apply {
            applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", InertFactorySource::class.java.name)
        }
        packages = listOf(first, second)
        ApkTrustStore.get(real).approve(requireNotNull(ApkInspector(context).identity(second)))
        val multiple = MihonExtensionRuntime(context)
        assertEquals(setOf(first.packageName, second.packageName), multiple.listSources().map { it["packageName"] }.toSet())
        assertEquals(emptyList<Any>(), multiple.runtimeSnapshot()["errors"])
        assertEquals(emptyList<Any>(), restarted.listSources()) // Captured empty registry stays empty.
        assertSame(MihonExtensionRuntime.get(real), MihonExtensionRuntime.get(real))
    }

    fun testSourceIdCollisionRejectsEveryPackageRegardlessOfDiscoveryOrder() {
        val real = instrumentation.targetContext
        val first = fixture("org.example.collisionfirst")
        val second = fixture("org.example.collisionsecond")
        val third = fixture("org.example.collisionthird")
        val independent = fixture("org.example.independentsource").apply {
            applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", InertFactorySource::class.java.name)
        }
        var packages = emptyList<PackageInfo>()
        val manager = object : MockPackageManager() {
            override fun getInstalledPackages(flags: Int) = packages
            override fun getInstalledPackages(flags: PackageManager.PackageInfoFlags) = packages
            override fun getPackageInfo(pkg: String, flags: Int) = packages.firstOrNull { it.packageName == pkg }
                ?: throw PackageManager.NameNotFoundException(pkg)
        }
        val context = object : ContextWrapper(real) {
            override fun getPackageManager(): PackageManager = manager
            override fun getClassLoader() = InertManagementSource::class.java.classLoader!!
        }
        val candidates = listOf(first, second, third, independent)
        candidates.forEach {
            ApkTrustStore.get(real).approve(requireNotNull(ApkInspector(context).identity(it)))
        }
        for (order in listOf(candidates, candidates.reversed())) {
            packages = order
            val runtime = MihonExtensionRuntime(context)
            val listed = runtime.listSources()
            assertEquals(listOf(independent.packageName), listed.map { it["packageName"] })
            val errors = runtime.runtimeSnapshot()["errors"] as List<*>
            val rejected = errors.map { it as Map<*, *> }
                .filter { it["reason"] == "collision" }
                .map { it["packageName"] }.toSet()
            assertEquals(setOf(first.packageName, second.packageName, third.packageName), rejected)
            assertEquals(3, errors.size)
        }
    }

    fun testDisplacedMultiSourcePackageCannotLeakOtherSourceIds() {
        val real = instrumentation.targetContext
        val factory = fixture("org.example.factorycollision").apply {
            applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", InertManagementFactory::class.java.name)
        }
        val firstClaim = fixture("org.example.firstclaim")
        val secondClaim = fixture("org.example.secondclaim").apply {
            applicationInfo!!.metaData!!.putString("tachiyomi.extension.class", InertFactorySource::class.java.name)
        }
        var packages = emptyList<PackageInfo>()
        val manager = object : MockPackageManager() {
            override fun getInstalledPackages(flags: Int) = packages
            override fun getInstalledPackages(flags: PackageManager.PackageInfoFlags) = packages
            override fun getPackageInfo(pkg: String, flags: Int) = packages.firstOrNull { it.packageName == pkg }
                ?: throw PackageManager.NameNotFoundException(pkg)
        }
        val context = object : ContextWrapper(real) {
            override fun getPackageManager(): PackageManager = manager
            override fun getClassLoader() = InertManagementSource::class.java.classLoader!!
        }
        val claimants = listOf(factory, firstClaim, secondClaim)
        claimants.forEach {
            ApkTrustStore.get(real).approve(requireNotNull(ApkInspector(context).identity(it)))
        }
        for (order in listOf(claimants, claimants.reversed())) {
            packages = order
            val runtime = MihonExtensionRuntime(context)
            assertEquals(emptyList<Any>(), runtime.listSources())
            val errors = runtime.runtimeSnapshot()["errors"] as List<*>
            val rejected = errors.map { (it as Map<*, *>)["packageName"] }.toSet()
            assertEquals(claimants.map { it.packageName }.toSet(), rejected)
            assertTrue(errors.all { (it as Map<*, *>)["reason"] == "collision" })
        }
    }

    /** Reflection avoids adding shrinker roots to the target app's Zstd JNI ABI. */
    fun testZstdJniDecompressesDeterministicFrame() {
        val loader = instrumentation.targetContext.classLoader
        val type = Class.forName("com.squareup.zstd.JniZstdDecompressor", true, loader)
        val decoder = type.getDeclaredConstructor().newInstance()
        // Standard Zstd frame: single segment, 5-byte content size, final raw block "hello".
        val frame = byteArrayOf(0x28, 0xb5.toByte(), 0x2f, 0xfd.toByte(), 0x20, 5, 0x29, 0, 0,
            'h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte())
        val output = ByteArray(5)
        try {
            val result = type.getMethod("decompressStream", ByteArray::class.java, Integer.TYPE, Integer.TYPE,
                ByteArray::class.java, Integer.TYPE, Integer.TYPE)
                // zstd-kmp takes end before start for both buffers (not offset, length).
                .invoke(decoder, output, output.size, 0, frame, frame.size, 0)
            assertEquals(0L, result)
            assertEquals("hello", String(output, Charsets.UTF_8))
            assertEquals(frame.size, type.getField("inputBytesProcessed").getInt(decoder))
            assertEquals(output.size, type.getField("outputBytesProcessed").getInt(decoder))
        } finally {
            type.getMethod("close").invoke(decoder)
        }
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
}
