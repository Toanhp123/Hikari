package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** Only APK-packaged, reviewed bundles are executable; no downloaded JS entry point. */
class LnReaderChannel(private val context: Context, messenger: BinaryMessenger) : AutoCloseable {
    private val channel = MethodChannel(messenger, "hikari/lnreader")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val capacity = Semaphore(4)
    private val plugins: List<JSONObject> = context.assets.list("lnreader/plugins").orEmpty()
        .filter { it.endsWith(".json") }.mapNotNull { file ->
            runCatching {
                JSONObject(context.assets.open("lnreader/plugins/$file").bufferedReader().use { it.readText() }).also {
                    require(it.getString("id").matches(Regex("[A-Za-z0-9_-]{1,128}")))
                    it.getString("name"); it.getString("site"); it.getJSONArray("origins")
                }
            }.getOrNull()
        }
    init { channel.setMethodCallHandler(::handle) }
    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        if (!capacity.tryAcquire()) { result.error("busy", "Runtime queue full", null); return }
        worker.execute {
            try {
                val output: Any = when (call.method) {
                    "listSources" -> plugins.map { mapOf("id" to it.getString("id"), "name" to it.getString("name"), "site" to it.getString("site")) }
                    "readResource" -> {
                        val id = requireNotNull(call.argument<String>("id"))
                        val plugin = plugins.single { it.getString("id") == id }
                        val origins = plugin.getJSONArray("origins")
                        val host = LnReaderHost(context, id, (0 until origins.length()).map { origins.getString(it) }.toSet())
                        val request = JSONObject().put("op", "resource").put("url", call.argument<String>("url"))
                            .put("init", JSONObject(execute(plugin, "Promise.resolve((module.exports.default||module.exports).imageRequestInit||{})", host)))
                        val encoded = org.json.JSONTokener(host.call(request.toString())).nextValue() as String
                        android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                    }
                    "invoke" -> {
                        val id = requireNotNull(call.argument<String>("id"))
                        val plugin = plugins.single { it.getString("id") == id }
                        val method = requireNotNull(call.argument<String>("method"))
                        require(method in setOf("searchNovels", "parseNovel", "parsePage", "parseChapter"))
                        val args = JSONArray(requireNotNull(call.argument<String>("args")))
                        val origins = plugin.getJSONArray("origins")
                        val hostBridge = LnReaderHost(context, id, (0 until origins.length()).map { origins.getString(it) }.toSet())
                        execute(plugin, "Promise.resolve((module.exports.default||module.exports)[${JSONObject.quote(method)}](...$args))", hostBridge)
                    }
                    else -> error("Unsupported method")
                }
                main.post { result.success(output) }
            } catch (failure: Exception) {
                main.post { result.error("lnreader_failure", failure.message, null) }
            } finally { capacity.release() }
        }
    }
    private fun execute(plugin: JSONObject, expression: String, bridge: LnReaderHost): String {
        val filename = plugin.getString("bundle")
        require(filename.matches(Regex("[A-Za-z0-9_-]+\\.js")))
        val host = context.assets.open("lnreader/host.js").bufferedReader().use { it.readText() }
        val bundle = context.assets.open("lnreader/plugins/$filename").bufferedReader().use { it.readText() }
        val script = "$host\nvar module={exports:{}};var exports=module.exports;\n$bundle\n$expression"
        return BoundedQuickJs().execute(script, bridge) ?: error("Execution rejected or resource limit exceeded")
    }
    override fun close() { channel.setMethodCallHandler(null); worker.shutdown() }
}
