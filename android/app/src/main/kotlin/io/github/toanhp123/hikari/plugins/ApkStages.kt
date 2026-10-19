package io.github.toanhp123.hikari.plugins

import java.io.File
import java.io.FileOutputStream

/** Staging publishes only fsynced, platform-inspected bytes. Tokens never contain user paths. */
internal class ApkStages(private val directory: File, private val inspector: ApkInspector,
    private val transport: PluginTransport) {
    init {
        directory.mkdirs()
        // No installer sessions exist yet. Stages are deliberately process-local;
        // startup removes abandoned bytes instead of claiming durable success.
        directory.listFiles().orEmpty().forEach { check(it.delete()) { "Cannot clean abandoned APK stage" } }
    }
    private val stages = mutableMapOf<String, Pair<File, ApkIdentity>>()
    private val reserved = mutableSetOf<String>()
    @Synchronized fun stage(url: String, family: String, pkg: String, code: Long, signer: String, request: PluginTransport.AcceptedRequest): Pair<String, ApkIdentity> {
        require(family in setOf("manga", "anime") && signer.matches(Regex("[a-f0-9]{64}")))
        require(stages.size < 4) { "APK staging limit reached" }
        val token = java.util.UUID.randomUUID().toString()
        val temporary = File(directory, "$token.tmp")
        val final = File(directory, "$token.apk")
        try {
            FileOutputStream(temporary).use { output ->
                transport.transfer(url, PluginTransport.APK_LIMIT, request, output)
                output.fd.sync()
            }
            val identity = try { inspector.inspectArchive(temporary) }
            catch (_: Exception) { throw PluginTransferFailure("plugin_apk_invalid") }
            try { identity.matchExpected(family, pkg, code, signer, inspector.installedForUpdate(pkg)) }
            catch (_: IllegalArgumentException) { throw PluginTransferFailure("plugin_apk_identity_mismatch") }
            transport.checkActive(request)
            check(temporary.renameTo(final)) { "Cannot publish APK stage" }
            stages[token] = final to identity
            return token to identity
        } catch (error: Throwable) { temporary.delete(); final.delete(); throw error }
    }
    @Synchronized fun inspected(token: String): ApkIdentity {
        val (file, expected) = stages[token] ?: error("Unknown or expired APK stage")
        val actual = inspector.inspectArchive(file)
        actual.matchExpected(expected.family, expected.packageName, expected.versionCode, expected.signers.single(), inspector.installedForUpdate(expected.packageName))
        check(actual == expected) { "Staged identity changed" }
        return actual
    }
    @Synchronized fun reserve(token: String): Pair<File, ApkIdentity> {
        check(token !in reserved) { "APK stage already in use" }
        val identity = inspected(token)
        reserved.add(token)
        return stages.getValue(token).first to identity
    }
    @Synchronized fun release(token: String, discard: Boolean) {
        reserved.remove(token)
        if (discard) discard(token)
    }
    @Synchronized fun discard(token: String) {
        check(token !in reserved) { "APK stage in use" }
        val (file, _) = stages[token] ?: error("Unknown or expired APK stage")
        check(!file.exists() || file.delete()) { "Cannot discard APK stage" }
        stages.remove(token)
    }
    fun close() { stages.values.forEach { it.first.delete() }; stages.clear() }
}
