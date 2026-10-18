package io.github.toanhp123.hikari.extensions.lnreader

import android.content.Context
import io.github.toanhp123.hikari.plugins.NovelArtifacts
import io.github.toanhp123.hikari.plugins.PublicHttps
import io.github.toanhp123.hikari.plugins.readBytesBounded
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** One immutable code snapshot per process. Management never replaces captured bytes. */
internal class LnReaderRuntime(
    private val capture: (Context, Long) -> NovelArtifacts.Capture = { context, budget ->
        NovelArtifacts.get(context.applicationContext).capture(budget)
    }
) {
    private data class Plugin(val id: String, val name: String, val site: String,
        val origins: List<String>, val code: String, val artifactJson: String?)
    private data class Snapshot(val host: String, val plugins: List<Plugin>)
    private var snapshot: Snapshot? = null
    private var failure: String? = null
    private var issues: List<NovelArtifacts.CaptureIssue> = emptyList()
    internal class Failure(val reason: String) : IllegalStateException("Novel runtime rejected")
    private fun text(value: Any?, max: Int): String = (value as? String ?: error("Invalid descriptor")).also {
        require(it.isNotBlank() && it.length <= max && '\u0000' !in it)
    }
    private fun plugin(row: JSONObject, code: String, artifact: String?): Plugin {
        val id = text(row.get("id"), 128).also { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
        val origins = row.getJSONArray("origins")
        require(origins.length() in 1..32)
        val normalized = (0 until origins.length()).map { LnReaderHost.canonicalOrigin(origins.getString(it)) }
        require(normalized.toSet().size == normalized.size)
        val site = PublicHttps.url(text(row.get("site"), 4096)).toString()
        require(LnReaderHost.canonicalOrigin(site) in normalized)
        return Plugin(id, text(row.get("name"), 256), site, normalized.toList(), code, artifact)
    }
    /** Invoked only on content worker; no filesystem reads on channel construction. */
    @Synchronized fun initialize(context: Context) {
        if (snapshot != null) return
        failure?.let { throw Failure(it) }
        var reason = "snapshot-failed"
        try {
            var remaining = BYTE_LIMIT
            fun readAsset(path: String): String {
                val bytes = context.assets.open(path).use { it.readBytesBounded(minOf(remaining, 4L * 1024 * 1024).toInt()) }
                remaining -= bytes.size
                check(remaining >= 0)
                return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    .also { require('\u0000' !in it) }
            }
            val host = readAsset("lnreader/host.js")
            val plugins = context.assets.list("lnreader/plugins").orEmpty().filter { it.endsWith(".json") }.sorted().map { file ->
                val row = JSONObject(readAsset("lnreader/plugins/$file"))
                val bundle = text(row.get("bundle"), 256).also { require(it.matches(Regex("[A-Za-z0-9_-]+\\.js"))) }
                plugin(row, readAsset("lnreader/plugins/$bundle"), null)
            }.toMutableList()
            // Store returns value copies and never calls back into this runtime monitor.
            val capture = capture(context, remaining)
            check(capture.code.sumOf { it.code.toByteArray(Charsets.UTF_8).size.toLong() } <= remaining)
            issues = capture.issues.toList()
            capture.code.forEach { plugins.add(plugin(JSONObject(it.artifactJson), it.code, it.artifactJson)) }
            reason = "collision"
            require(plugins.map { it.id }.toSet().size == plugins.size)
            snapshot = Snapshot(host, plugins.toList())
        } catch (_: Exception) {
            failure = reason
            throw Failure(reason)
        }
    }
    /** No store access, filesystem read, initialization or JS evaluation. Fresh codec copies only. */
    @Synchronized fun evidence(): Map<String, Any?> = mapOf(
        "initialization" to if (snapshot != null) "ready" else if (failure != null) "failed" else "notInitialized",
        "failure" to failure,
        "loaded" to snapshot?.plugins.orEmpty().map { mapOf("id" to it.id, "name" to it.name,
            "site" to it.site, "artifact" to it.artifactJson?.let(::artifactMap)) },
        "issues" to issues.map { mapOf("artifact" to artifactMap(it.artifactJson), "reason" to it.reason) })
    private fun artifactMap(encoded: String): Map<String, Any> {
        val row = JSONObject(encoded)
        return row.keys().asSequence().associateWith { key ->
            if (key == "origins") row.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            else row.get(key)
        }
    }
    @Synchronized fun sources(): List<Map<String, String>> = requireNotNull(snapshot).plugins.map {
        mapOf("id" to it.id, "name" to it.name, "site" to it.site)
    }
    private fun selected(id: String): Pair<Snapshot, Plugin> {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val frozen = requireNotNull(snapshot)
        return frozen to (frozen.plugins.singleOrNull { it.id == id } ?: throw Failure("source-unavailable"))
    }
    private fun execute(frozen: Snapshot, plugin: Plugin, expression: String, host: LnReaderHost): String {
        val script = "${frozen.host}\nvar module={exports:{}};var exports=module.exports;\n${plugin.code}\n$expression"
        return BoundedQuickJs().execute(script, host) ?: throw Failure("execution-rejected")
    }
    fun invoke(context: Context, id: String, method: String, encodedArgs: String): String {
        val (frozen, plugin) = selected(id)
        val args = LnReaderContract.arguments(method, encodedArgs)
        val expression = """
            Promise.resolve().then(async function(){
              const plugin=module.exports.default||module.exports;
              if(typeof plugin[${JSONObject.quote(method)}]!=='function') throw 'Missing method';
              const value=await plugin[${JSONObject.quote(method)}](...$args);
              return {value:value,hasParsePage:typeof plugin.parsePage==='function'};
            })
        """.trimIndent()
        val raw = execute(frozen, plugin, expression, LnReaderHost(context, id, plugin.origins.toSet()))
        try {
            val result = JSONObject(raw)
            require(result.keys().asSequence().toSet() == setOf("value", "hasParsePage") && result.get("hasParsePage") is Boolean)
            val value = result.get("value")
            LnReaderContract.output(method, value, result.getBoolean("hasParsePage"))
            return if (value is String) JSONObject.quote(value) else value.toString()
        } catch (_: Exception) { throw Failure("invalid-output") }
    }
    fun resource(context: Context, id: String, url: String): ByteArray {
        val (frozen, plugin) = selected(id)
        LnReaderContract.path(url)
        val host = LnReaderHost(context, id, plugin.origins.toSet())
        val init = execute(frozen, plugin, "Promise.resolve((module.exports.default||module.exports).imageRequestInit||{})", host)
        val request = JSONObject().put("op", "resource").put("url", url).put("init", JSONObject(init))
        val encoded = JSONTokener(host.call(request.toString())).nextValue() as String
        return android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
    }
    companion object {
        const val BYTE_LIMIT = 64L * 1024 * 1024
        val shared = LnReaderRuntime()
    }
}
