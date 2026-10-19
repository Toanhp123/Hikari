package io.github.toanhp123.hikari.plugins

import android.content.Context
import io.github.toanhp123.hikari.extensions.lnreader.NovelAdmission
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Artifact custody with exact consent and bounded export admission; no source activation. */
internal class NovelArtifacts(private val directory: File,
    private val beforePublicationSync: () -> Unit = {}, private val beforePointerSync: () -> Unit = {},
    private val finishWrite: (AtomicFile, FileOutputStream) -> Unit = { file, output -> file.finishWrite(output) },
    private val inspect: (Map<String, Any>, String) -> Unit = { _, _ -> throw NovelAdmission.Rejected() },
    private val beforeCommit: () -> Unit = {}) {
    internal class PublicationUnknown(val evidence: Map<String, Any?>) : IllegalStateException("Novel state publication unknown; reopen required")
    internal class PointerSyncFailure(val evidence: List<Map<String, Any?>>) : IllegalStateException("Novel state committed; pointer directory sync failed")
    private val artifacts = File(directory, "artifacts")
    private val staging = File(directory, "staging")
    private val atomic = AtomicFile(File(directory, "state.json"))
    private data class Entry(val current: Map<String, Any>, val previous: Map<String, Any>?,
        val currentPolicy: Int? = null, val previousPolicy: Int? = null)
    private data class Stage(val file: File, val artifact: Map<String, Any>, val revision: String?)
    private val stages = mutableMapOf<String, Stage>()
    private var pointersDurable = true
    private var generation = 0L
    private var state = read()
    init {
        // Persist plugins/novel and its children before any pointer can reference code.
        durableDirectory(requireNotNull(directory.parentFile))
        durableDirectory(directory)
        durableDirectory(artifacts)
        durableDirectory(staging)
        cleanFiles(staging, emptySet())
        collect()
    }
    private fun syncDirectory(dir: File) {
        val descriptor = Os.open(dir.path, OsConstants.O_RDONLY, 0)
        try {
            check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) { "Expected Novel directory" }
            Os.fsync(descriptor)
        } finally { Os.close(descriptor) }
    }
    private fun durableDirectory(dir: File) {
        if (!dir.exists()) {
            val parent = requireNotNull(dir.parentFile)
            if (!parent.exists()) durableDirectory(parent)
            check(dir.mkdir()) { "Cannot create Novel directory" }
        }
        check(dir.isDirectory)
        syncDirectory(dir)
        syncDirectory(requireNotNull(dir.parentFile))
    }
    private fun fields(map: Map<*, *>, expected: Set<String>) {
        require(map.keys == expected) { "Unknown or missing Novel fields" }
    }
    private fun text(value: Any?, max: Int): String = (value as? String ?: error("Expected text")).also {
        require(it.isNotBlank() && it.length <= max && '\u0000' !in it)
    }
    private fun url(value: Any?): String {
        val parsed = PublicHttps.url(text(value, 4096))
        return parsed.newBuilder().host(parsed.host.trimEnd('.')).build().toString()
    }
    private fun origin(value: Any?): String {
        val parsed = PublicHttps.url(text(value, 4096))
        require(parsed.encodedPath == "/" && parsed.query == null)
        return "https://${parsed.host.trimEnd('.')}"
    }
    private fun metadata(input: Map<*, *>, digest: Boolean): Map<String, Any> {
        fields(input, META + (if (digest) setOf("digest") else emptySet()) +
            (if (input.containsKey("language")) setOf("language") else emptySet()))
        val id = text(input["id"], 128).also { require(it.matches(NovelPluginIds.VALID)) }
        val version = text(input["version"], 128).also { require(VERSION.matches(it)) }
        val origins = (input["origins"] as? List<*> ?: error("Expected origins")).also { require(it.size in 1..32) }.map(::origin)
        require(origins.toSet().size == origins.size)
        val site = url(input["site"])
        require(origin(PublicHttps.url(site).newBuilder().encodedPath("/").query(null).build().toString()) in origins)
        return linkedMapOf<String, Any>("id" to id, "name" to text(input["name"], 256), "version" to version,
            "repository" to url(input["repository"]), "site" to site, "origins" to origins.sorted()).apply {
            if (input.containsKey("language")) put("language", text(input["language"], 128))
            if (digest) put("digest", text(input["digest"], 64).also { require(it.matches(Regex("[a-f0-9]{64}"))) })
        }
    }
    private fun json(map: Map<String, Any>) = JSONObject(map)
    private fun decode(obj: JSONObject): Map<String, Any> = metadata(obj.keys().asSequence().associateWith { key ->
        if (key == "origins") obj.getJSONArray(key).let { a -> (0 until a.length()).map { a.get(it) } } else obj.get(key)
    }, true)
    private fun strict(bytes: ByteArray): String {
        require(bytes.size in 1..BUNDLE_LIMIT && bytes.none { it == 0.toByte() })
        return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            catch (_: java.nio.charset.CharacterCodingException) { error("Invalid Novel UTF-8") }
    }
    private fun hash(file: File): String {
        check(file.isFile && file.canonicalFile.parentFile == requireNotNull(file.parentFile).canonicalFile)
        val bytes = file.inputStream().use { it.readBytesBounded(BUNDLE_LIMIT) }
        strict(bytes)
        return digest(bytes)
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun file(artifact: Map<String, Any>) = File(artifacts, "${artifact.getValue("digest")}.js")
    private fun status(artifact: Map<String, Any>): String = if (!file(artifact).exists()) "missing" else
        if (runCatching { hash(file(artifact)) == artifact["digest"] }.getOrDefault(false)) "ready" else "corrupt"
    // Conservative global generation also rejects ABA remove/reinstall and unrelated concurrent mutations.
    private fun revision(id: String): String = "$generation:$id"
    @Synchronized fun inventory(): List<Map<String, Any?>> = state.toSortedMap().values.map {
        mapOf("current" to it.current, "previous" to it.previous, "status" to status(it.current),
            "previousStatus" to it.previous?.let(::status), "admission" to admission(it.currentPolicy),
            "previousAdmission" to it.previous?.let { _ -> admission(it.previousPolicy) }, "restartRequired" to true)
    }
    internal data class CapturedCode(val artifactJson: String, val code: String)
    internal data class CaptureIssue(val artifactJson: String, val reason: String)
    internal data class Capture(val code: List<CapturedCode>, val issues: List<CaptureIssue>)
    /** Copies under management monitor; never calls runtime or evaluates JS. */
    @Synchronized fun capture(byteBudget: Long): Capture {
        check(pointersDurable) { "Novel pointer durability unknown; reopen required" }
        var remaining = byteBudget
        val code = mutableListOf<CapturedCode>()
        val issues = mutableListOf<CaptureIssue>()
        state.toSortedMap().values.forEach { entry ->
            val artifact = entry.current
            val encoded = json(artifact).toString()
            if (entry.currentPolicy != NovelAdmission.POLICY) {
                issues.add(CaptureIssue(encoded, "admission-unknown"))
            } else if (!file(artifact).exists()) {
                issues.add(CaptureIssue(encoded, "missing"))
            } else {
                val captured = try {
                    val source = file(artifact)
                    check(source.isFile && source.canonicalFile.parentFile == artifacts.canonicalFile)
                    val bytes = source.inputStream().use { it.readBytesBounded(BUNDLE_LIMIT) }
                    val bundle = strict(bytes)
                    check(digest(bytes) == artifact["digest"])
                    bytes.size to bundle
                } catch (_: Exception) { null }
                if (captured == null) issues.add(CaptureIssue(encoded, "corrupt"))
                else {
                    remaining -= captured.first
                    check(remaining >= 0) { "Novel snapshot byte limit" }
                    code.add(CapturedCode(encoded, captured.second))
                }
            }
        }
        return Capture(code.toList(), issues.toList())
    }
    private fun admission(policy: Int?) = if (policy == NovelAdmission.POLICY) "admitted" else "unknown"
    private fun inspectBytes(artifact: Map<String, Any>, source: File) {
        check(pointersDurable) { "Novel pointer durability unknown; reopen required" }
        check(source.isFile && source.canonicalFile.parentFile == requireNotNull(source.parentFile).canonicalFile)
        val bytes = source.inputStream().use { it.readBytesBounded(BUNDLE_LIMIT) }
        val bundle = strict(bytes)
        check(digest(bytes) == artifact["digest"]) { "Novel artifact changed" }
        inspect(artifact, bundle)
    }
    @Synchronized fun admit(displayed: Map<*, *>): List<Map<String, Any?>> {
        val expected = metadata(displayed, true)
        val id = expected.getValue("id") as String
        val entry = state[id] ?: error("Novel artifact absent")
        check(entry.current == expected) { "Novel admission revision changed" }
        inspectBytes(expected, file(expected))
        persist(state + (id to entry.copy(currentPolicy = NovelAdmission.POLICY)))
        return inventory()
    }
    @Synchronized fun stage(input: Map<*, *>, checkActive: () -> Unit, download: (OutputStream) -> Unit): Map<String, Any> {
        check(pointersDurable) { "Novel pointer durability unknown; reopen required" }
        val meta = metadata(input, false)
        require(stages.size < 4) { "Novel stage limit reached" }
        collect()
        require(diskBytes() + BUNDLE_LIMIT + 2L * STATE_LIMIT <= DISK_LIMIT) { "Novel disk limit reached" }
        val token = UUID.randomUUID().toString()
        val temporary = File(staging, "$token.tmp")
        val destination = File(staging, "$token.js")
        val base = revision(meta.getValue("id") as String)
        try {
            FileOutputStream(temporary).use { output ->
                var count = 0L
                val bounded = object : OutputStream() {
                    override fun write(b: Int) { require(++count <= BUNDLE_LIMIT); output.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) { count += len; require(count <= BUNDLE_LIMIT); output.write(b, off, len) }
                }
                download(bounded)
                output.fd.sync()
            }
            val artifact = meta + ("digest" to hash(temporary))
            checkActive()
            check(temporary.renameTo(destination))
            stages[token] = Stage(destination, artifact, base)
            return mapOf("token" to token, "artifact" to artifact)
        } catch (error: Throwable) { temporary.delete(); destination.delete(); throw error }
    }
    @Synchronized fun install(token: String, displayed: Map<*, *>): List<Map<String, Any?>> {
        val stage = stages[token] ?: error("Unknown or consumed Novel stage")
        val expected = metadata(displayed, true)
        check(expected == stage.artifact) { "Displayed consent changed" }
        val id = expected.getValue("id") as String
        check(revision(id) == stage.revision) { "Novel revision changed since staging" }
        state[id]?.let { require(compareVersions(expected.getValue("version") as String, it.current.getValue("version") as String) >= 0) { "Novel downgrade denied; use rollback" } }
        inspectBytes(expected, stage.file)
        collect()
        val target = file(expected)
        if (target.exists()) check(hash(target) == expected["digest"]) else check(stage.file.renameTo(target))
        // File contents were synced while staging; persist destination entry before pointers.
        // Failure leaves an inert orphan and a moved/unretryable stage, never new consent.
        beforePublicationSync()
        syncDirectory(artifacts)
        syncDirectory(staging)
        val next = state + (id to Entry(expected, state[id]?.current, NovelAdmission.POLICY, state[id]?.currentPolicy))
        persist(next)
        stages.remove(token)
        stage.file.delete()
        collect()
        return inventory()
    }
    @Synchronized fun rollback(displayed: Map<*, *>, previous: Map<*, *>): List<Map<String, Any?>> {
        val current = metadata(displayed, true)
        val old = metadata(previous, true)
        val id = current.getValue("id") as String
        val entry = state[id] ?: error("Novel artifact absent")
        check(current == entry.current && old == entry.previous && status(old) == "ready") { "Rollback consent or artifact changed" }
        if (entry.previousPolicy != NovelAdmission.POLICY) inspectBytes(old, file(old))
        persist(state + (id to Entry(old, current, NovelAdmission.POLICY, entry.currentPolicy)))
        return inventory()
    }
    @Synchronized fun uninstall(displayed: Map<*, *>): List<Map<String, Any?>> {
        val expected = metadata(displayed, true)
        val id = expected.getValue("id") as String
        check(state[id]?.current == expected) { "Novel removal revision changed" }
        persist(state - id)
        collect()
        return inventory()
    }
    @Synchronized fun discard(token: String) {
        val stage = stages[token] ?: error("Unknown Novel stage")
        check(!stage.file.exists() || stage.file.delete())
        stages.remove(token)
    }
    private fun marker(artifact: Map<String, Any>?, policy: Int?): Any =
        if (artifact == null || policy == null) JSONObject.NULL else
            JSONObject().put("policy", policy).put("artifact", json(artifact))
    private fun policy(row: JSONObject, key: String, artifact: Map<String, Any>?): Int? {
        if (row.isNull(key)) return null
        require(artifact != null)
        val marker = row.getJSONObject(key)
        require(marker.keys().asSequence().toSet() == setOf("policy", "artifact"))
        require(decode(marker.getJSONObject("artifact")) == artifact)
        val policy = marker.getInt("policy")
        require(policy > 0 && marker.get("policy").toString() == policy.toString())
        return policy
    }
    private fun persist(next: Map<String, Entry>) {
        check(pointersDurable) { "Novel pointer durability unknown; reopen required" }
        require(next.size <= 500)
        val rows = JSONArray()
        next.toSortedMap().values.forEach { rows.put(JSONObject().put("current", json(it.current))
            .put("previous", it.previous?.let(::json) ?: JSONObject.NULL)
            .put("currentAdmission", marker(it.current, it.currentPolicy))
            .put("previousAdmission", marker(it.previous, it.previousPolicy))) }
        check(generation < Long.MAX_VALUE)
        val nextGeneration = generation + 1
        val bytes = JSONObject().put("schema", 2).put("generation", nextGeneration).put("entries", rows).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= STATE_LIMIT && diskBytes() + bytes.size <= DISK_LIMIT)
        directory.mkdirs()
        val output = atomic.startWrite()
        try { output.write(bytes); output.fd.sync(); beforeCommit() }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
        // finishWrite can log a failed rename and return normally. Read the base file
        // directly: openRead performs recovery and is not publication confirmation.
        pointersDurable = false
        try {
            finishWrite(atomic, output)
            output.close()
            check(File(directory, "state.json").inputStream().use { it.readBytesBounded(STATE_LIMIT) }.contentEquals(bytes))
        } catch (_: Exception) {
            runCatching { output.close() }
            // Never failWrite after publication was attempted, or collect either revision.
            throw PublicationUnknown(mapOf("publication" to "unknown", "lastConfirmedInventory" to inventory(),
                "attemptedPointers" to next.toSortedMap().values.map { mapOf("current" to it.current, "previous" to it.previous) }))
        }
        state = next
        generation = nextGeneration
        try { beforePointerSync(); syncDirectory(directory); pointersDurable = true }
        catch (_: Exception) { throw PointerSyncFailure(inventory()) }
    }
    private fun read(): Map<String, Entry> {
        if (!File(directory, "state.json").exists() && !File(directory, "state.json.bak").exists()) return emptyMap()
        val root = JSONObject(atomic.openRead().use { it.readBytesBounded(STATE_LIMIT) }.let {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(it)).toString()
        })
        require(root.keys().asSequence().toSet() == setOf("schema", "generation", "entries"))
        val schema = root.get("schema")
        require(schema == 1 || schema == 2)
        generation = root.getLong("generation").also { require(it >= 0 && root.get("generation").toString() == it.toString()) }
        val rows = root.getJSONArray("entries")
        require(rows.length() <= 500)
        val entries = mutableMapOf<String, Entry>()
        repeat(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            require(row.keys().asSequence().toSet() == if (schema == 1) setOf("current", "previous")
                else setOf("current", "previous", "currentAdmission", "previousAdmission"))
            val current = decode(row.getJSONObject("current"))
            val previous = if (row.isNull("previous")) null else decode(row.getJSONObject("previous"))
            val id = current.getValue("id") as String
            require(id !in entries && (previous == null || previous["id"] == id))
            entries[id] = Entry(current, previous,
                if (schema == 1) null else policy(row, "currentAdmission", current),
                if (schema == 1) null else policy(row, "previousAdmission", previous))
        }
        return entries
    }
    private fun cleanFiles(dir: File, retained: Set<String>) {
        dir.listFiles().orEmpty().forEach { child ->
            check(child.isFile && child.canonicalFile.parentFile == dir.canonicalFile) { "Unexpected Novel storage entry" }
            if (child.name !in retained) check(child.delete()) { "Cannot remove Novel orphan" }
        }
    }
    private fun collect() {
        check(pointersDurable) { "Novel pointer durability unknown; collection refused" }
        val retained = state.values.flatMap { listOfNotNull(it.current, it.previous) }.map { "${it.getValue("digest")}.js" }.toSet()
        cleanFiles(artifacts, retained)
        require(diskBytes() <= DISK_LIMIT)
    }
    private fun diskBytes(): Long = listOf(artifacts, staging).sumOf { dir -> dir.listFiles().orEmpty().sumOf { it.length() } } +
        listOf("state.json", "state.json.bak", "state.json.new").sumOf { File(directory, it).length() }
    companion object {
        const val BUNDLE_LIMIT = 1024 * 1024
        private const val STATE_LIMIT = 1024 * 1024
        private const val DISK_LIMIT = 64L * 1024 * 1024
        private val META = setOf("id", "name", "version", "repository", "site", "origins")
        private val VERSION = Regex("^\\d+(?:\\.\\d+)*(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        fun compareVersions(a: String, b: String): Int {
            require(a.length <= 128 && b.length <= 128 && VERSION.matches(a) && VERSION.matches(b))
            val aa = a.substringBefore('+').split('-'); val bb = b.substringBefore('+').split('-')
            val ac = aa.first().split('.').map(::BigInteger); val bc = bb.first().split('.').map(::BigInteger)
            for (i in 0 until maxOf(ac.size, bc.size)) {
                val c = ac.getOrElse(i) { BigInteger.ZERO }.compareTo(bc.getOrElse(i) { BigInteger.ZERO }); if (c != 0) return c
            }
            if (aa.size == 1 || bb.size == 1) return (if (aa.size == 1) 1 else 0).compareTo(if (bb.size == 1) 1 else 0)
            val ap = aa.drop(1).joinToString("-").split('.'); val bp = bb.drop(1).joinToString("-").split('.')
            for (i in 0 until minOf(ap.size, bp.size)) {
                val ai = ap[i].toBigIntegerOrNull(); val bi = bp[i].toBigIntegerOrNull()
                val c = if (ai != null && bi != null) ai.compareTo(bi) else if (ai != null) -1 else if (bi != null) 1 else ap[i].compareTo(bp[i])
                if (c != 0) return c
            }
            return ap.size.compareTo(bp.size)
        }
        private var instance: NovelArtifacts? = null
        @Synchronized fun get(context: Context): NovelArtifacts = instance ?: NovelArtifacts(File(context.filesDir, "plugins/novel"), inspect = NovelAdmission(context.applicationContext)::inspect).also { instance = it }
    }
}
