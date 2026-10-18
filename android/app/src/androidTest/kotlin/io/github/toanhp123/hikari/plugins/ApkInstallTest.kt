package io.github.toanhp123.hikari.plugins

import android.test.InstrumentationTestCase
import java.io.File

@Suppress("DEPRECATION")
class ApkInstallTest : InstrumentationTestCase() {
    private lateinit var directory: File
    override fun setUp() {
        super.setUp()
        check(instrumentation.targetContext.packageName.endsWith(".localmediatest"))
        directory = File(instrumentation.targetContext.cacheDir, "install-${System.nanoTime()}").apply { mkdirs() }
    }
    override fun tearDown() { directory.deleteRecursively(); super.tearDown() }

    fun testJournalRestartAndAtomicFailure() {
        val file = File(directory, "operations.json")
        val journal = ApkInstallJournal(file)
        val first = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
        assertEquals(first, ApkInstallJournal(file).all().single())
        rejects { journal.create(identity(), "update", java.util.UUID.randomUUID().toString()) }
        File(directory, "operations.json.new").mkdir()
        rejects { journal.save(first.copy(status = InstallStatus.FAILED, reason = "fixture")) }
        assertEquals(InstallStatus.PREPARING, journal.all().single().status)
        assertEquals(InstallStatus.PREPARING, ApkInstallJournal(file).all().single().status)
    }

    fun testCorruptJournalFailsClosed() {
        val file = File(directory, "operations.json").apply { writeText("not JSON") }
        rejects { ApkInstallJournal(file) }
        assertEquals("not JSON", file.readText())
    }

    fun testCallbackBindingPendingAndInstalledReconciliation() {
        val journal = ApkInstallJournal(File(directory, "operations.json"))
        val operation = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
            .copy(sessionId = 42, status = InstallStatus.AWAITING_RESULT)
        journal.save(operation)
        assertFalse(journal.acceptsCallback(operation.id, 43, operation.packageName))
        assertFalse(journal.acceptsCallback(operation.id, 42, "org.other"))
        assertFalse(journal.acceptsCallback(java.util.UUID.randomUUID().toString(), 42, operation.packageName))
        assertTrue(journal.acceptsCallback(operation.id, 42, operation.packageName))
        journal.save(operation.copy(status = InstallStatus.PENDING_USER_ACTION))
        assertFalse(journal.all().single().status.terminal)
        assertFalse(operation.matches(identity(code = 11)))
        assertFalse(operation.matches(identity(signer = "b".repeat(64))))
        assertFalse(operation.matches(identity(family = "anime")))
        assertTrue(operation.matches(identity()))
        val done = operation.copy(status = InstallStatus.SUCCEEDED)
        journal.save(done)
        assertFalse(journal.acceptsCallback(operation.id, 42, operation.packageName))
        assertEquals(done, ApkInstallJournal(File(directory, "operations.json")).all().single())
    }

    fun testHistoryBoundAndInterruptedBlocksReplacement() {
        val journal = ApkInstallJournal(File(directory, "operations.json"))
        repeat(105) {
            val op = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
            journal.save(op.copy(status = InstallStatus.CANCELLED))
        }
        assertEquals(100, journal.all().size)
        val pending = journal.create(identity(), "update", java.util.UUID.randomUUID().toString())
        journal.save(pending.copy(status = InstallStatus.INTERRUPTED))
        rejects { journal.create(identity(), "update", java.util.UUID.randomUUID().toString()) }
    }

    fun testCoordinatorPendingRestartMismatchAndExplicitAbandon() {
        val context = instrumentation.targetContext
        val file = File(directory, "callback-journal.json")
        val journal = ApkInstallJournal(file)
        val sessions = context.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(10)
            setAppPackageName("org.example.plugin")
        }
        val session = sessions.createSession(params) // Own isolated test session only; never commit/install.
        try {
            val operation = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
                .copy(sessionId = session, status = InstallStatus.AWAITING_RESULT, stageTransferred = true)
            journal.save(operation)
            val coordinator = ApkInstaller(context, file)
            fun callback(status: Int, sessionId: Int = session, pkg: String = operation.packageName) = android.content.Intent().apply {
                action = ApkInstaller.CALLBACK_ACTION
                data = android.net.Uri.Builder().scheme("hikari-plugin-install").authority(operation.id)
                    .appendPath(session.toString()).appendPath(operation.packageName).build()
                putExtra(android.content.pm.PackageInstaller.EXTRA_SESSION_ID, sessionId)
                putExtra(android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME, pkg)
                putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, status)
            }
            coordinator.callback(callback(android.content.pm.PackageInstaller.STATUS_SUCCESS, session + 1))
            coordinator.callback(callback(android.content.pm.PackageInstaller.STATUS_SUCCESS, pkg = "org.wrong"))
            assertEquals(InstallStatus.AWAITING_RESULT, ApkInstallJournal(file).all().single().status)
            coordinator.callback(callback(android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION).apply {
                putExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent("inert.fixture.never.launched"))
            })
            assertEquals(InstallStatus.PENDING_USER_ACTION, coordinator.operations().single().status)
            val queuedTicket = coordinator.confirmation(operation.id)
            val reopened = ApkInstaller(context, file)
            assertEquals(InstallStatus.INTERRUPTED, reopened.operations().single().status)
            assertEquals("confirmation_lost", reopened.operations().single().reason)
            assertNotNull(sessions.getSessionInfo(session)) // Restart never auto-abandons or recommits.
            // Claimed callback success cannot pass absent installed OS identity.
            reopened.callback(callback(android.content.pm.PackageInstaller.STATUS_SUCCESS))
            assertEquals(InstallStatus.FAILED, reopened.operations().single().status)
            reopened.callback(callback(android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION))
            assertEquals(InstallStatus.FAILED, reopened.operations().single().status) // Late duplicate ignored.
            coordinator.callback(callback(android.content.pm.PackageInstaller.STATUS_SUCCESS))
            instrumentation.runOnMainSync {
                rejects { queuedTicket.launch(android.app.Activity()) } // Terminal callback invalidates queued dispatch.
            }
            sessions.abandonSession(session)

            val secondSession = sessions.createSession(params)
            try {
                val second = ApkInstallJournal(file).create(identity(), "install", java.util.UUID.randomUUID().toString())
                    .copy(sessionId = secondSession, status = InstallStatus.AWAITING_RESULT)
                ApkInstallJournal(file).save(second)
                val cancel = ApkInstaller(context, file)
                val expected = if (android.os.Build.VERSION.SDK_INT >= 31) InstallStatus.CANCELLED else InstallStatus.INTERRUPTED
                assertEquals(expected, cancel.cancel(second.id).status)
                assertNull(sessions.getSessionInfo(secondSession))
                assertEquals(expected, cancel.cancel(second.id).status)
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    assertEquals(InstallStatus.RETIRED_UNKNOWN, cancel.retire(second.id, abandonOwnedSession = false).status)
                }
            } finally { runCatching { sessions.abandonSession(secondSession) } }
        } finally { runCatching { sessions.abandonSession(session) } }
    }

    fun testSuccessEvidenceSurvivesTransientInspectionFailure() {
        val real = instrumentation.targetContext
        val file = File(directory, "success-evidence.json")
        val journal = ApkInstallJournal(file)
        val op = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
            .copy(sessionId = 99123, status = InstallStatus.AWAITING_RESULT)
        journal.save(op)
        val manager = object : android.test.mock.MockPackageManager() {
            override fun getPackageInstaller() = real.packageManager.packageInstaller
            override fun getPackageInfo(pkg: String, flags: Int): android.content.pm.PackageInfo {
                throw IllegalStateException("transient inspection failure")
            }
        }
        val context = object : android.content.ContextWrapper(real) {
            override fun getPackageManager(): android.content.pm.PackageManager = manager
        }
        val callback = android.content.Intent().apply {
            action = ApkInstaller.CALLBACK_ACTION
            data = android.net.Uri.Builder().scheme("hikari-plugin-install").authority(op.id)
                .appendPath(op.sessionId.toString()).appendPath(op.packageName).build()
            putExtra(android.content.pm.PackageInstaller.EXTRA_SESSION_ID, op.sessionId!!)
            putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, android.content.pm.PackageInstaller.STATUS_SUCCESS)
        }
        rejects { ApkInstaller(context, file).callback(callback) }
        val saved = ApkInstallJournal(file).all().single()
        assertTrue(saved.successCallbackReceived)
        assertEquals(InstallStatus.INTERRUPTED, saved.status)
        // Restart retries inspection only, never install/commit; absent identity fails exact match.
        assertEquals(InstallStatus.FAILED, ApkInstaller(real, file).operations().single().status)
        assertTrue(ApkInstallJournal(file).all().single().successCallbackReceived)
    }

    fun testMissingSessionCancellationRemainsUnknown() {
        val real = instrumentation.targetContext
        val file = File(directory, "missing-session.json")
        val journal = ApkInstallJournal(file)
        val op = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
            .copy(sessionId = 99124, status = InstallStatus.AWAITING_RESULT)
        journal.save(op)
        val result = ApkInstaller(real, file).cancel(op.id)
        assertEquals(InstallStatus.INTERRUPTED, result.status)
        assertEquals("cancellation_outcome_unknown", result.reason)
        assertFalse(result.status.terminal)
    }

    fun testExplicitRetirementPrecreateAndLostCallbackAllowsReplacement() {
        val real = instrumentation.targetContext
        for (session in listOf<Int?>(null, 99125)) {
            val file = File(directory, "retire-$session.json")
            val journal = ApkInstallJournal(file)
            val op = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
                .copy(sessionId = session, status = if (session == null) InstallStatus.PREPARING else InstallStatus.AWAITING_RESULT)
            journal.save(op)
            val coordinator = ApkInstaller(real, file)
            assertEquals(InstallStatus.INTERRUPTED, coordinator.operations().single().status)
            val retired = coordinator.retire(op.id, abandonOwnedSession = false)
            assertEquals(InstallStatus.RETIRED_UNKNOWN, retired.status)
            assertEquals(op.sessionId, retired.sessionId)
            assertEquals(op.signer, retired.signer)
            assertTrue(retired.status.terminal)
            val reopened = ApkInstallJournal(file)
            val replacement = reopened.create(identity(), "install", java.util.UUID.randomUUID().toString())
            val later = ApkInstaller(real, file)
            later.callback(android.content.Intent().apply {
                action = ApkInstaller.CALLBACK_ACTION
                data = android.net.Uri.Builder().scheme("hikari-plugin-install").authority(op.id)
                    .appendPath(op.sessionId.toString()).appendPath(op.packageName).build()
                putExtra(android.content.pm.PackageInstaller.EXTRA_SESSION_ID, session ?: -1)
                putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, android.content.pm.PackageInstaller.STATUS_SUCCESS)
            })
            assertEquals(retired, ApkInstallJournal(file).find(op.id))
            assertEquals(replacement, ApkInstallJournal(file).find(replacement.id))
        }
    }

    fun testRetirementRequiresAllOwnedPackageSessionsGone() {
        val real = instrumentation.targetContext
        val file = File(directory, "retire-owned.json")
        val journal = ApkInstallJournal(file)
        val op = journal.create(identity(), "install", java.util.UUID.randomUUID().toString())
        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(10); setAppPackageName(op.packageName); setAppLabel("Hikari plugin ${op.id}")
        }
        val sessions = real.packageManager.packageInstaller
        val first = sessions.createSession(params)
        val second = sessions.createSession(params)
        try {
            val coordinator = ApkInstaller(real, file)
            rejects { coordinator.retire(op.id, abandonOwnedSession = false) }
            assertFalse(ApkInstallJournal(file).find(op.id).status.terminal)
            assertNotNull(sessions.getSessionInfo(first))
            assertNotNull(sessions.getSessionInfo(second))
            // Same policy on API24–30: explicit abandonment followed by successful empty query.
            assertEquals(InstallStatus.RETIRED_UNKNOWN, coordinator.retire(op.id, abandonOwnedSession = true).status)
            assertNull(sessions.getSessionInfo(first))
            assertNull(sessions.getSessionInfo(second))
        } finally {
            runCatching { sessions.abandonSession(first) }; runCatching { sessions.abandonSession(second) }
        }
    }

    fun testExternalRevisionRetirementAndOwnershipQueryFailure() {
        val real = instrumentation.targetContext
        val info = real.packageManager.getPackageInfo(real.packageName, ApkInspector.flags()).apply {
            packageName = "org.example.plugin"; versionName = "1.6.10"; versionCode = 10
            if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode = 10
            reqFeatures = arrayOf(android.content.pm.FeatureInfo().apply { name = "tachiyomi.extension" })
            applicationInfo!!.metaData = android.os.Bundle().apply {
                putString("tachiyomi.extension.class", ".NeverLoaded"); putString("tachiyomix.extensionLib", "1.6")
            }
        }
        val manager = object : android.test.mock.MockPackageManager() {
            override fun getPackageInstaller() = real.packageManager.packageInstaller
            override fun getPackageInfo(pkg: String, flags: Int) = info
        }
        val context = object : android.content.ContextWrapper(real) {
            override fun getPackageManager(): android.content.pm.PackageManager = manager
        }
        val file = File(directory, "retire-external.json")
        val journal = ApkInstallJournal(file)
        val expected = requireNotNull(ApkInspector(context).identity(info))
        val op = journal.create(expected, "install", java.util.UUID.randomUUID().toString())
        val coordinator = ApkInstaller(context, file)
        assertEquals(InstallStatus.EXTERNALLY_INSTALLED, coordinator.operations().single().status)
        val retired = coordinator.retire(op.id, abandonOwnedSession = false)
        assertEquals(InstallStatus.RETIRED_UNKNOWN, retired.status)
        assertEquals("retired_matching_revision_unknown_outcome", retired.reason)
        assertFalse(retired.successCallbackReceived)
        val next = ApkInstallJournal(file).create(expected, "update", java.util.UUID.randomUUID().toString())
        // Failed ownership lookup cannot retire/release package lock.
        val broken = object : android.content.ContextWrapper(real) {
            override fun getPackageManager(): android.content.pm.PackageManager = object : android.test.mock.MockPackageManager() {
                override fun getPackageInstaller(): android.content.pm.PackageInstaller = throw IllegalStateException("ownership unavailable")
            }
        }
        rejects { ApkInstaller(broken, file).retire(next.id, abandonOwnedSession = true) }
        assertFalse(ApkInstallJournal(file).find(next.id).status.terminal)
    }

    fun testConfirmationTicketInvalidationDoesNotNeedInstallerLock() {
        val operation = ApkInstallOperation(java.util.UUID.randomUUID().toString(), "manga", "org.example.plugin", 10,
            "a".repeat(64), "install", java.util.UUID.randomUUID().toString(), status = InstallStatus.PENDING_USER_ACTION)
        val ticket = ApkInstaller.Confirmation(operation, android.content.Intent("inert.never.launched"))
        ticket.invalidate()
        instrumentation.runOnMainSync { rejects { ticket.launch(android.app.Activity()) } }
    }

    fun testFailedPreparationAbandonsCreatedSession() {
        val real = instrumentation.targetContext
        val info = real.packageManager.getPackageInfo(real.packageName, ApkInspector.flags()).apply {
            packageName = "org.example.inertfixture"
            versionName = "1.6.10"
            versionCode = 10
            if (android.os.Build.VERSION.SDK_INT >= 28) longVersionCode = 10
            reqFeatures = arrayOf(android.content.pm.FeatureInfo().apply { name = "tachiyomi.extension" })
            applicationInfo!!.metaData = android.os.Bundle().apply {
                putString("tachiyomi.extension.class", ".NeverLoaded")
                putString("tachiyomix.extensionLib", "1.6")
            }
        }
        val manager = object : android.test.mock.MockPackageManager() {
            override fun getPackageInstaller() = real.packageManager.packageInstaller
            override fun canRequestPackageInstalls() = true
            override fun getPackageArchiveInfo(path: String, flags: Int) = info
            override fun getPackageInfo(pkg: String, flags: Int): android.content.pm.PackageInfo {
                throw android.content.pm.PackageManager.NameNotFoundException(pkg)
            }
        }
        val context = object : android.content.ContextWrapper(real) {
            override fun getPackageManager(): android.content.pm.PackageManager = manager
        }
        val inspector = ApkInspector(context)
        val identity = requireNotNull(inspector.identity(info))
        ApkTrustStore.get(real).approve(identity) // Isolated test app only.
        val transport = PluginTransport()
        val stages = ApkStages(File(directory, "failure-stages"), inspector, transport)
        val token = java.util.UUID.randomUUID().toString()
        // Directory cannot be copied as APK bytes; failure occurs after real session creation.
        val invalid = File(directory, "unreadable-apk-directory").apply { mkdirs() }
        @Suppress("UNCHECKED_CAST")
        val entries = ApkStages::class.java.getDeclaredField("stages").apply { isAccessible = true }
            .get(stages) as MutableMap<String, Pair<File, ApkIdentity>>
        entries[token] = invalid to identity
        val file = File(directory, "preparation-journal.json")
        val coordinator = ApkInstaller(context, file)
        try {
            rejects { coordinator.start(stages, token, "install") }
            val operation = ApkInstallJournal(file).all().single()
            assertEquals(InstallStatus.FAILED, operation.status)
            assertEquals("preparation_failed", operation.reason)
            assertNotNull(operation.sessionId)
            assertNull(real.packageManager.packageInstaller.getSessionInfo(operation.sessionId!!))
            assertFalse(invalid.exists())
            assertTrue(entries.isEmpty())
        } finally { stages.close(); transport.close() }
    }

    fun testMissingPermissionDoesNotConsumeStageOrCreateOperation() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val real = instrumentation.targetContext
        val manager = object : android.test.mock.MockPackageManager() {
            override fun getPackageInstaller() = real.packageManager.packageInstaller
            override fun canRequestPackageInstalls() = false
        }
        val context = object : android.content.ContextWrapper(real) {
            override fun getPackageManager(): android.content.pm.PackageManager = manager
        }
        val file = File(directory, "permission-journal.json")
        val coordinator = ApkInstaller(context, file)
        val transport = PluginTransport()
        val stages = ApkStages(File(directory, "permission-stages"), ApkInspector(real), transport)
        assertFalse(coordinator.canInstall())
        rejects { coordinator.start(stages, "unused", "install") }
        assertTrue(coordinator.operations().isEmpty())
        assertFalse(file.exists())
        stages.close(); transport.close()
    }

    fun testUninstallAbsentBeforeRequestAndLateResultCannotBecomeSuccess() {
        val file = File(directory, "uninstall-absent.json")
        val coordinator = ApkInstaller(instrumentation.targetContext, file)
        val op = coordinator.uninstall("manga", "org.example.absent", 10, "a".repeat(64))
        assertEquals(InstallStatus.ABSENT_BEFORE_REQUEST, op.status)
        assertEquals("uninstall", op.kind)
        coordinator.uninstallResult(requireNotNull(op.requestCode), android.app.Activity.RESULT_OK)
        assertEquals(op, ApkInstallJournal(file).find(op.id))
    }

    fun testUninstallJournalConflictsWithInstallAndKeepsRequestBinding() {
        val file = File(directory, "uninstall-conflict.json")
        val journal = ApkInstallJournal(file)
        val op = journal.create(identity(), "uninstall", java.util.UUID.randomUUID().toString())
        rejects { journal.create(identity(), "install", java.util.UUID.randomUUID().toString()) }
        assertNotNull(op.requestCode)
        assertEquals(op, ApkInstallJournal(file).find(op.id))
        journal.save(op.copy(status = InstallStatus.RETIRED_UNKNOWN))
        val next = journal.create(identity(), "uninstall", java.util.UUID.randomUUID().toString())
        assertTrue(requireNotNull(next.requestCode) > requireNotNull(op.requestCode))
    }

    private fun identity(code: Long = 10, signer: String = "a".repeat(64), family: String = "manga") =
        ApkIdentity(family, "org.example.plugin", code, "1.6.10", "1.6", ".Plugin", listOf(signer), emptyList())
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected rejection") }
        catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
        catch (_: java.io.IOException) {} catch (_: org.json.JSONException) {}
    }
}
