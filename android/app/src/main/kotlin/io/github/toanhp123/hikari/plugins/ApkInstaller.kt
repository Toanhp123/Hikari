package io.github.toanhp123.hikari.plugins

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process-wide serialized session authority, independent of Flutter lifetime. Never replays commit. */
internal class ApkInstaller internal constructor(private val context: Context,
    journalFile: File = File(context.filesDir, "plugins/apk-operations.json")) {
    private val sessions = context.packageManager.packageInstaller
    private val inspector = ApkInspector(context)
    private val trust = ApkTrustStore.get(context)
    private val journal = ApkInstallJournal(journalFile)
    internal class Confirmation(val operation: ApkInstallOperation, private val intent: Intent, private val requestCode: Int? = null) {
        private val lock = java.util.concurrent.locks.ReentrantLock()
        private var valid = true
        private var dispatched = false
        fun invalidate() { lock.lock(); try { valid = false } finally { lock.unlock() } }
        fun launch(activity: Activity) {
            check(lock.tryLock()) { "Confirmation changed; retry" }
            try {
                check(valid && !dispatched) { "Confirmation expired" }
                if (requestCode != null) dispatched = true // One-shot removal dispatch, including ambiguous launch errors.
                if (requestCode == null) activity.startActivity(intent)
                else activity.startActivityForResult(intent, requestCode)
            } finally { lock.unlock() }
        }
    }
    private val confirmations = mutableMapOf<String, Confirmation>()
    private fun invalidateConfirmation(id: String) { confirmations.remove(id)?.invalidate() }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    @Synchronized fun start(stages: ApkStages, token: String, kind: String): ApkInstallOperation {
        check(canInstall()) { "Install permission required" }
        require(kind in setOf("install", "update"))
        val (file, identity) = stages.reserve(token)
        var operation: ApkInstallOperation? = null
        var sessionId: Int? = null
        var committed = false
        var transferred = false
        try {
            check(trust.isTrusted(identity)) { "APK signer not approved" }
            val installed = inspector.installedForUpdate(identity.packageName)
            identity.matchExpected(identity.family, identity.packageName, identity.versionCode, identity.signers.single(), installed)
            check((kind == "install") == (installed == null)) { "Install/update inventory changed" }
            // Durable precreate identity permits orphan-session reconciliation after crash.
            operation = journal.create(identity, kind, token)
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(file.length())
                setAppPackageName(identity.packageName)
                setAppLabel("Hikari plugin ${requireNotNull(operation).id}")
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            sessionId = sessions.createSession(params)
            operation = operation.copy(sessionId = sessionId)
            journal.save(operation)
            sessions.openSession(sessionId).use { session ->
                // Reinspection and trust check immediately before copying private stage bytes.
                val inspected = stages.inspected(token)
                check(operation.matches(inspected) && trust.isTrusted(inspected)) { "Staged APK identity changed" }
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                // Journal ownership transfer before releasing process-local stage.
                operation = operation.copy(stageTransferred = true)
                journal.save(operation)
                transferred = true
                stages.release(token, discard = true)
                operation = operation.copy(status = InstallStatus.AWAITING_RESULT)
                journal.save(operation)
                val callback = Intent(context, ApkInstallReceiver::class.java).apply {
                    action = CALLBACK_ACTION
                    data = callbackUri(operation)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val sender = PendingIntent.getBroadcast(context, 0, callback, flags)
                // Crossing commit boundary may succeed even if caller receives an exception.
                committed = true
                session.commit(sender.intentSender)
            }
            return journal.find(requireNotNull(operation).id)
        } catch (error: Throwable) {
            // Never abandon a committed/potentially installing session automatically.
            if (!committed) {
                var abandoned = sessionId == null
                sessionId?.let { id -> abandoned = runCatching { sessions.abandonSession(id) }.isSuccess }
                operation?.let { op ->
                    journal.save(op.copy(status = if (abandoned) InstallStatus.FAILED else InstallStatus.INTERRUPTED,
                        reason = "preparation_failed"))
                }
            } else {
                operation?.let { op -> journal.save(op.copy(status = InstallStatus.INTERRUPTED, reason = "commit_outcome_unknown")) }
            }
            throw error
        } finally {
            if (!transferred) stages.release(token, discard = operation != null)
        }
    }

    /** Public system removal path; caller's displayed revision is never trusted without OS reinspection. */
    @Synchronized fun uninstall(family: String, pkg: String, code: Long, signer: String): ApkInstallOperation {
        require(pkg != context.packageName && family in setOf("manga", "anime"))
        require(code >= 0 && signer.matches(Regex("[a-f0-9]{64}")))
        val installed = inspector.installedForUpdate(pkg)
        val expected = installed ?: ApkIdentity(family, pkg, code, "absent", if (family == "manga") "1.6" else "17", ".Absent", listOf(signer), emptyList())
        installed?.matchExpected(family, pkg, code, signer, installed)
        check(sessions.mySessions.none { it.appPackageName == pkg }) { "Owned install session remains" }
        val op = journal.create(expected, "uninstall", java.util.UUID.randomUUID().toString())
        val pending = op.copy(status = if (installed == null) InstallStatus.ABSENT_BEFORE_REQUEST else InstallStatus.PENDING_USER_ACTION,
            reason = if (installed == null) "absent_before_request" else null)
        journal.save(pending)
        return pending
    }

    @Synchronized fun removalConfirmation(id: String): Confirmation {
        val op = journal.find(id)
        check(op.kind == "uninstall" && op.status == InstallStatus.PENDING_USER_ACTION && !op.removalLaunched)
        check(op.matches(inspector.installedForUpdate(op.packageName))) { "Uninstall identity changed" }
        check(sessions.mySessions.none { it.appPackageName == op.packageName }) { "Owned install session remains" }
        val pending = op.copy(removalLaunched = true, status = InstallStatus.AWAITING_RESULT)
        journal.save(pending) // Persist ambiguous dispatch boundary before leaving worker. Never replay.
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${op.packageName}"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return Confirmation(pending, intent, requireNotNull(op.requestCode)).also { confirmations[id] = it }
    }

    @Synchronized fun uninstallResult(requestCode: Int, resultCode: Int) {
        val op = journal.all().firstOrNull { it.kind == "uninstall" && it.requestCode == requestCode } ?: return
        if (op.status.terminal || !op.removalLaunched || op.removalResult != null) return
        invalidateConfirmation(op.id)
        val received = op.copy(removalResult = resultCode, status = InstallStatus.INTERRUPTED, reason = "removal_reconciliation_pending")
        journal.save(received)
        reconcileRemoval(received)
    }

    private fun reconcileRemoval(op: ApkInstallOperation) {
        val present = inspector.packagePresent(op.packageName)
        if (op.removalResult == null && op.id in confirmations) return // Prepared/open one-shot ticket survives polling.
        val next = when {
            !present && op.removalResult == Activity.RESULT_OK -> op.copy(status = InstallStatus.SUCCEEDED, reason = null)
            !present -> op.copy(status = if (op.removalResult == null) InstallStatus.INTERRUPTED else InstallStatus.EXTERNALLY_REMOVED,
                reason = "absent_without_success_result")
            op.removalResult == Activity.RESULT_CANCELED -> op.copy(status = InstallStatus.CANCELLED, reason = "system_removal_cancelled")
            op.removalResult != null -> op.copy(status = InstallStatus.FAILED, reason = "system_removal_not_confirmed")
            else -> op.copy(status = InstallStatus.INTERRUPTED, reason = "removal_result_unknown")
        }
        invalidateConfirmation(op.id)
        journal.save(next)
    }

    /** Explicit inventory/status open. Matching external install is never manager callback success. */
    @Synchronized fun operations(): List<ApkInstallOperation> {
        val owned = sessions.mySessions
        journal.all().filter { !it.status.terminal }.forEach { previous ->
            var op = previous
            if (op.kind == "uninstall") {
                if (op.status != InstallStatus.PENDING_USER_ACTION || op.removalLaunched) reconcileRemoval(op)
                return@forEach
            }
            val installed = inspector.inspectInstalled(op.packageName)
            if (op.successCallbackReceived) {
                reconcileSuccess(op, installed)
                return@forEach
            }
            if (op.sessionId == null) {
                val tracked = journal.all().mapNotNull { it.sessionId }.toSet()
                val orphan = owned.filter { it.sessionId !in tracked && it.appPackageName == op.packageName &&
                    it.appLabel?.toString() == "Hikari plugin ${op.id}" }
                if (orphan.size == 1) op = op.copy(sessionId = orphan.single().sessionId)
            }
            if (op.matches(installed)) {
                op = op.copy(status = InstallStatus.EXTERNALLY_INSTALLED, reason = "installed_without_success_callback")
            } else {
                val info = op.sessionId?.let(sessions::getSessionInfo)
                val lostConfirmation = op.status == InstallStatus.PENDING_USER_ACTION && op.id !in confirmations
                val stalled = op.status == InstallStatus.AWAITING_RESULT && info != null && !info.isActive
                if (op.status == InstallStatus.PREPARING || lostConfirmation || info == null || stalled) {
                    op = op.copy(status = InstallStatus.INTERRUPTED, reason = when {
                        lostConfirmation -> "confirmation_lost"
                        info == null -> "session_unavailable"
                        stalled -> "session_inactive_without_callback"
                        else -> "preparation_interrupted"
                    })
                }
            }
            if (op != previous) {
                invalidateConfirmation(op.id)
                journal.save(op)
            }
        }
        return journal.all()
    }

    @Synchronized fun callback(intent: Intent) {
        if (intent.action != CALLBACK_ACTION) return
        val id = intent.data?.host ?: return
        val op = runCatching { journal.find(id) }.getOrNull() ?: return
        if (intent.data != callbackUri(op)) return
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        if (!journal.acceptsCallback(id, session, pkg)) return
        if (op.successCallbackReceived) {
            reconcileSuccess(op, inspector.inspectInstalled(op.packageName))
            return
        }
        invalidateConfirmation(id)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                journal.save(op.copy(status = if (confirmation == null) InstallStatus.INTERRUPTED else InstallStatus.PENDING_USER_ACTION,
                    reason = if (confirmation == null) "confirmation_unavailable" else null))
                if (confirmation != null) confirmations[id] = Confirmation(journal.find(id), confirmation)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Preserve callback evidence before potentially failing PackageManager IO.
                val received = op.copy(successCallbackReceived = true, status = InstallStatus.INTERRUPTED,
                    reason = "success_reconciliation_pending")
                journal.save(received)
                invalidateConfirmation(id)
                reconcileSuccess(received, inspector.inspectInstalled(op.packageName))
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                journal.save(op.copy(status = InstallStatus.CANCELLED, reason = "system_aborted"))
                invalidateConfirmation(id)
            }
            PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_STORAGE, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_TIMEOUT -> {
                journal.save(op.copy(status = InstallStatus.FAILED, reason = "system_install_failed"))
                invalidateConfirmation(id)
            }
            else -> journal.save(op.copy(status = InstallStatus.INTERRUPTED, reason = "unknown_system_status"))
        }
    }

    private fun reconcileSuccess(op: ApkInstallOperation, installed: ApkIdentity?) {
        journal.save(op.copy(status = if (op.matches(installed)) InstallStatus.SUCCEEDED else InstallStatus.FAILED,
            reason = if (op.matches(installed)) null else "installed_identity_mismatch"))
    }

    @Synchronized fun cancel(id: String): ApkInstallOperation {
        operations() // Reconcile installed revision and recover orphan session before cancellation.
        val op = journal.find(id)
        if (op.status.terminal) return op
        if (op.kind == "uninstall") {
            check(!op.removalLaunched) { "System removal outcome unknown; cannot cancel external confirmation" }
            return op.copy(status = InstallStatus.CANCELLED, reason = "not_launched").also(journal::save)
        }
        invalidateConfirmation(id)
        val session = op.sessionId
        val info = session?.let(sessions::getSessionInfo)
        if (info != null) {
            check(sessions.mySessions.any { it.sessionId == session && it.appPackageName == op.packageName }) { "Session ownership mismatch" }
            sessions.abandonSession(requireNotNull(session))
        }
        // Abandon is a request, not proof an already committing install was cancelled.
        val installed = inspector.inspectInstalled(op.packageName)
        val next = when {
            op.matches(installed) -> op.copy(status = InstallStatus.EXTERNALLY_INSTALLED,
                reason = "installed_without_success_callback")
            info != null && Build.VERSION.SDK_INT >= 31 && !info.isCommitted ->
                op.copy(status = InstallStatus.CANCELLED, reason = "uncommitted_session_abandoned")
            else -> op.copy(status = InstallStatus.INTERRUPTED, reason = "cancellation_outcome_unknown")
        }
        journal.save(next)
        invalidateConfirmation(id)
        return next
    }

    /** Explicit user retirement, not proof of success/cancellation. No live owned session may escape. */
    @Synchronized fun retire(id: String, abandonOwnedSession: Boolean, acknowledgeUnrevokedRemoval: Boolean = false): ApkInstallOperation {
        operations()
        var op = journal.find(id)
        if (op.status.terminal) return op
        check(op.kind != "uninstall" || !op.removalLaunched || acknowledgeUnrevokedRemoval) {
            "Explicit acknowledgment required: dismiss old system confirmation; app cannot revoke it and it could remove a replacement"
        }
        invalidateConfirmation(id)
        fun related() = sessions.mySessions.filter {
            it.sessionId == op.sessionId || it.appPackageName == op.packageName ||
                it.appLabel?.toString() == "Hikari plugin ${op.id}"
        }
        val owned = related() // Query failure propagates; never releases duplicate lock.
        if (owned.isNotEmpty()) {
            check(abandonOwnedSession) { "Owned session remains; explicit abandonment required" }
            if (op.kind != "uninstall" && op.sessionId == null && owned.size == 1) {
                op = op.copy(sessionId = owned.single().sessionId)
                journal.save(op)
            }
            owned.forEach { sessions.abandonSession(it.sessionId) }
        }
        check(related().isEmpty()) { "Owned session remains; retirement denied" }
        op.sessionId?.let { check(sessions.getSessionInfo(it) == null) { "Session still exists; retirement denied" } }
        val installed = inspector.inspectInstalled(op.packageName)
        val retired = op.copy(status = InstallStatus.RETIRED_UNKNOWN,
            reason = if (op.kind == "uninstall" && op.removalLaunched) "retired_unrevoked_removal_acknowledged"
                else if (op.matches(installed)) "retired_matching_revision_unknown_outcome" else "retired_unknown_outcome")
        journal.save(retired)
        return retired
    }

    /** Worker-only lookup/validation; returned ticket dispatches without global installer IO lock. */
    @Synchronized fun confirmation(id: String): Confirmation {
        val op = journal.find(id)
        check(op.status == InstallStatus.PENDING_USER_ACTION) { "Operation has no pending confirmation" }
        return confirmations[id] ?: error("Confirmation lost; explicitly retire before retry")
    }

    companion object {
        const val CALLBACK_ACTION = "io.github.toanhp123.hikari.plugins.INSTALL_STATUS"
        private var instance: ApkInstaller? = null
        @Synchronized fun get(context: Context): ApkInstaller = instance ?: ApkInstaller(context.applicationContext).also { instance = it }
        private fun callbackUri(op: ApkInstallOperation): Uri = Uri.Builder().scheme("hikari-plugin-install")
            .authority(op.id).appendPath(op.sessionId.toString()).appendPath(op.packageName).build()
        fun openSettings(activity: Activity) {
            if (Build.VERSION.SDK_INT >= 26) activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")))
        }
    }
}

/** Explicit non-exported target; no Activity launch or Flutter dependency. */
class ApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            callbacks.execute {
                try { ApkInstaller.get(context).callback(intent) }
                catch (error: Exception) { android.util.Log.e("HikariPluginInstall", "Cannot persist installer status", error) }
                finally { pending.finish() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { pending.finish() }
    }
    companion object {
        private val callbacks = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(32))
    }
}
