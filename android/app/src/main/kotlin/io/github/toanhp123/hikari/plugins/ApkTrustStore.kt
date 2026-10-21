package io.github.toanhp123.hikari.plugins

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Sole APK trust authority, available before Flutter and before class loading. */
internal class ApkTrustStore(private val file: File) {
    private val atomic = AtomicFile(file)
    private var approvals = read()
    private fun read(): Set<String> {
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptySet()
        val bytes = atomic.openRead().use { input -> input.readBytesBounded(1024 * 1024) }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        check(root.getInt("schema") == 1) { "Unknown APK trust schema" }
        val rows = root.getJSONArray("approvals")
        require(rows.length() <= 5000)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val family = row.getString("family")
            val pkg = row.getString("packageName")
            val signer = row.getString("signer")
            require(family in setOf("manga", "anime") && pkg.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) && signer.matches(Regex("[a-f0-9]{64}")))
            "$family|$pkg|$signer"
        }.toSet()
    }
    @Synchronized
    fun isTrusted(identity: ApkIdentity): Boolean = identity.compatibility == "compatible" &&
        identity.signers.size == 1 && ((identity.family == "manga" && identity.signers.single() == OFFICIAL_SIGNER) ||
        key(identity) in approvals)

    /** Caller must re-inspect installed/staged identity before invoking. */
    @Synchronized
    fun approve(identity: ApkIdentity) {
        require(identity.compatibility == "compatible" && identity.signers.size == 1)
        val next = approvals + key(identity)
        require(next.size <= 5000)
        val rows = JSONArray()
        next.sorted().forEach { value ->
            val parts = value.split('|')
            rows.put(JSONObject().put("family", parts[0]).put("packageName", parts[1]).put("signer", parts[2]))
        }
        file.parentFile!!.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(JSONObject().put("schema", 1).put("approvals", rows).toString().toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        approvals = next
    }
    private fun key(identity: ApkIdentity) = "${identity.family}|${identity.packageName}|${identity.signers.single()}"
    companion object {
        const val OFFICIAL_SIGNER = "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
        private var instance: ApkTrustStore? = null
        @Synchronized fun get(context: Context): ApkTrustStore = instance ?: ApkTrustStore(
            File(context.filesDir, "plugins/apk-trust.json")).also { instance = it }
    }
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "Response exceeds byte limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
