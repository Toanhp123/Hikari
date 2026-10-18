package io.github.toanhp123.hikari.plugins

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class PluginChannel(private val context: Context, messenger: BinaryMessenger) : AutoCloseable {
    private val channel = MethodChannel(messenger, "hikari/plugins")
    private val inspector = ApkInspector(context.applicationContext)
    private val trust = ApkTrustStore.get(context.applicationContext)
    private val transport = PluginTransport()
    private val stages = ApkStages(File(context.filesDir, "plugins/staging"), inspector, transport)
    private val main = Handler(Looper.getMainLooper())
    private val worker = object : ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(4)) {
        override fun terminated() { stages.close(); novelTokens.forEach { token -> runCatching { novel?.discard(token) } } }
    }
    // Initialized only on management worker; singleton owns process-local stages.
    private val novelTokens = mutableSetOf<String>()
    private var novel: NovelArtifacts? = null
    private fun novelArtifacts(): NovelArtifacts = novel ?: NovelArtifacts.get(context.applicationContext).also { novel = it }
    @Volatile private var closed = false
    @Volatile private var foreground = false
    private var foregroundEpoch = 0L
    fun onResume() { foreground = true }
    fun onPause() { foreground = false; foregroundEpoch++ }
    private fun foregroundActivity(): android.app.Activity {
        check(foreground) { "Foreground Activity required" }
        return (context as? android.app.Activity)?.also {
            check(!it.isFinishing && !it.isDestroyed)
        } ?: error("Activity unavailable")
    }
    init { channel.setMethodCallHandler(::handle) }
    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        var request: PluginTransport.AcceptedRequest? = null
        try {
            check(!closed) { "Plugin manager closed" }
            if (call.method == "openInstallSettings") {
                ApkInstaller.openSettings(foregroundActivity())
                result.success(null); return
            }
            if (call.method in setOf("resumeInstallConfirmation", "uninstall")) {
                check(foreground) { "Foreground Activity required" }
            }
            if (call.method == "cancel") {
                transport.cancel(string(call, "requestId")); result.success(null); return
            }
            if (call.method in setOf("metadata", "stage", "novelStage")) {
                // Register before enqueueing so queued requests can be cancelled.
                request = transport.accept(string(call, "requestId"))
            }
            val accepted = request
            val launchEpoch = foregroundEpoch
            worker.execute {
                val outcome = runCatching {
                    check(!closed) { "Plugin manager closed" }
                    when (call.method) {
                        "novelRuntimeSnapshot" -> io.github.toanhp123.hikari.extensions.lnreader.LnReaderRuntime.shared.evidence()
                        "novelAdmit" -> novelArtifacts().admit(requireNotNull(call.argument<Map<*, *>>("artifact")))
                        "novelInventory" -> novelArtifacts().inventory()
                        "novelStage" -> {
                            val active = requireNotNull(accepted)
                            val staged = novelArtifacts().stage(requireNotNull(call.argument<Map<*, *>>("metadata")),
                                { transport.checkActive(active) },
                                { transport.transfer(string(call, "url"), NovelArtifacts.BUNDLE_LIMIT, active, it) })
                            try {
                                check(!closed); transport.checkActive(active)
                                novelTokens.add(staged.getValue("token") as String)
                                staged
                            } catch (error: Throwable) {
                                novelArtifacts().discard(staged.getValue("token") as String)
                                throw error
                            }
                        }
                        "novelInstall" -> {
                            val token = string(call, "token")
                            novelArtifacts().install(token, requireNotNull(call.argument<Map<*, *>>("artifact"))).also { novelTokens.remove(token) }
                        }
                        "novelRollback" -> novelArtifacts().rollback(requireNotNull(call.argument<Map<*, *>>("artifact")), requireNotNull(call.argument<Map<*, *>>("previous")))
                        "novelUninstall" -> novelArtifacts().uninstall(requireNotNull(call.argument<Map<*, *>>("artifact")))
                        "novelDiscard" -> { val token = string(call, "token"); novelArtifacts().discard(token); novelTokens.remove(token); null }
                        "inventory" -> {
                            ApkInstaller.get(context).operations()
                            inspector.inventory().map { it.toMap(trust.isTrusted(it)) }
                        }
                        "uninstall" -> {
                            val installer = ApkInstaller.get(context)
                            val op = installer.uninstall(string(call, "family"), string(call, "packageName"),
                                requireNotNull(call.argument<Number>("versionCode")).toLong(), string(call, "expectedSigner"))
                            if (op.status.terminal) op.toMap() else installer.removalConfirmation(op.id)
                        }
                        "canInstall" -> ApkInstaller.get(context).canInstall()
                        "install" -> ApkInstaller.get(context).start(stages, string(call, "token"), string(call, "kind")).toMap()
                        "installOperations" -> ApkInstaller.get(context).operations().map { it.toMap() }
                        "cancelInstall" -> ApkInstaller.get(context).cancel(string(call, "operationId")).toMap()
                        "retireInstall" -> ApkInstaller.get(context).retire(string(call, "operationId"),
                            requireNotNull(call.argument<Boolean>("abandonOwnedSession")),
                            call.argument<Boolean>("acknowledgeUnrevokedRemoval") == true).toMap()
                        "resumeInstallConfirmation" -> ApkInstaller.get(context).confirmation(string(call, "operationId"))
                        "metadata" -> transport.read(string(call, "url"), PluginTransport.METADATA_LIMIT, requireNotNull(accepted))
                        "stage" -> {
                            val code = call.argument<Number>("versionCode")?.toLong() ?: error("Missing version code")
                            val (token, identity) = stages.stage(string(call, "url"), string(call, "family"),
                                string(call, "packageName"), code, string(call, "expectedSigner"), requireNotNull(accepted))
                            mapOf("token" to token, "identity" to identity.toMap(trust.isTrusted(identity)))
                        }
                        "discard" -> { stages.discard(string(call, "token")); null }
                        "approve" -> {
                            // Signer consent from platform archive reinspection, not installability.
                            // Future OS installation must reconcile installed identity before loading.
                            val identity = stages.inspected(string(call, "token"))
                            trust.approve(identity)
                            identity.toMap(trust.isTrusted(identity))
                        }
                        "approveInstalled" -> {
                            // Reinspect OS-owned package and compare exact displayed identity.
                            val identity = inspector.inspectInstalled(string(call, "packageName")) ?: error("Installed extension absent")
                            identity.matchExpected(string(call, "family"), identity.packageName,
                                call.argument<Number>("versionCode")?.toLong() ?: error("Missing version code"), string(call, "expectedSigner"), identity)
                            trust.approve(identity)
                            identity.toMap(trust.isTrusted(identity))
                        }
                        else -> error("Unsupported plugin operation")
                    }
                }.also { accepted?.let(transport::finish) }
                main.post {
                    outcome.fold({ value ->
                        try {
                            if (value is ApkInstaller.Confirmation) {
                                dispatchConfirmation(value, launchEpoch)
                                result.success(value.operation.toMap()) // Launch is not completion.
                            } else result.success(value)
                        } catch (error: RuntimeException) { operationError(result, error) }
                    }) { operationError(result, it) }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            request?.let(transport::finish)
            result.error("plugin_manager_busy", "Plugin manager queue is full or closed", null)
        } catch (error: RuntimeException) {
            request?.let(transport::finish)
            operationError(result, error)
        }
    }
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode !in 0x4000..0x7fff) return false
        try { worker.execute { runCatching { ApkInstaller.get(context).uninstallResult(requestCode, resultCode) } } }
        catch (_: java.util.concurrent.RejectedExecutionException) { /* Unknown result remains durable, never inferred success. */ }
        return true
    }
    internal fun dispatchConfirmation(value: ApkInstaller.Confirmation, launchEpoch: Long) {
        check(!closed && launchEpoch == foregroundEpoch) { "Confirmation request expired" }
        value.launch(foregroundActivity())
    }
    private fun operationError(result: MethodChannel.Result, error: Throwable) {
        if (error is PluginTransferFailure) {
            result.error(error.code, "Plugin acquisition failed", error.status?.let { mapOf("status" to it) })
            return
        }
        if (error is io.github.toanhp123.hikari.extensions.lnreader.NovelAdmission.Rejected) {
            result.error("novel_admission_rejected", "Novel admission rejected", mapOf("reason" to error.reason))
            return
        }
        if (error is NovelArtifacts.PublicationUnknown) {
            result.error("novel_publication_unknown", "Novel state publication unknown; reopen required", error.evidence)
            return
        }
        if (error is NovelArtifacts.PointerSyncFailure) {
            result.error("novel_pointer_sync_failed", "Novel state committed; durability unknown; reopen required", error.evidence)
            return
        }
        result.error("plugin_operation_failed", "Plugin operation failed: ${error.javaClass.simpleName}", null)
    }
    private fun string(call: MethodCall, key: String): String = requireNotNull(call.argument<String>(key)) {
        "Missing $key"
    }.also { require(it.length in 1..4096) }
    override fun close() {
        closed = true; channel.setMethodCallHandler(null); transport.close()
        // Queued calls still complete with closed error, rather than losing replies.
        worker.shutdown()
    }
}
