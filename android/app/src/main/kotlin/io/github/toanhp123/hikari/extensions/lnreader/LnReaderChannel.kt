package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** All content calls share one immutable, admitted process snapshot. */
class LnReaderChannel(private val context: Context, messenger: BinaryMessenger) : AutoCloseable {
    private val channel = MethodChannel(messenger, "hikari/lnreader")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val capacity = Semaphore(4)
    init { channel.setMethodCallHandler(::handle) }
    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        if (!capacity.tryAcquire()) { result.error("busy", "Runtime queue full", null); return }
        try {
            worker.execute {
                try {
                    require(call.method in setOf("listSources", "invoke", "readResource"))
                    val runtime = LnReaderRuntime.shared
                    runtime.initialize(context.applicationContext)
                    val output: Any = when (call.method) {
                        "listSources" -> runtime.sources()
                        "invoke" -> runtime.invoke(context, requireNotNull(call.argument<String>("id")),
                            requireNotNull(call.argument<String>("method")), requireNotNull(call.argument<String>("args")))
                        else -> runtime.resource(context, requireNotNull(call.argument<String>("id")),
                            requireNotNull(call.argument<String>("url")))
                    }
                    main.post { result.success(output) }
                } catch (failure: Exception) {
                    val reason = (failure as? LnReaderRuntime.Failure)?.reason ?: "invalid-request"
                    main.post { result.error("lnreader_failure", "Novel source operation rejected", mapOf("reason" to reason)) }
                } finally { capacity.release() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            capacity.release()
            result.error("busy", "Runtime queue closed", null)
        }
    }
    override fun close() { channel.setMethodCallHandler(null); worker.shutdown() }
}
