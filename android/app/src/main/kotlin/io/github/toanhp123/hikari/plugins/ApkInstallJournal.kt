package io.github.toanhp123.hikari.plugins

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal enum class InstallStatus(val terminal: Boolean = false) {
    PREPARING, AWAITING_RESULT, PENDING_USER_ACTION, INTERRUPTED, EXTERNALLY_INSTALLED,
    SUCCEEDED(true), FAILED(true), CANCELLED(true), RETIRED_UNKNOWN(true), ABSENT_BEFORE_REQUEST(true), EXTERNALLY_REMOVED(true)
}

internal data class ApkInstallOperation(
    val id: String, val family: String, val packageName: String, val versionCode: Long,
    val signer: String, val kind: String, val stageToken: String,
    val sessionId: Int? = null, val status: InstallStatus = InstallStatus.PREPARING,
    val stageTransferred: Boolean = false, val reason: String? = null,
    val successCallbackReceived: Boolean = false,
    val requestCode: Int? = null, val removalLaunched: Boolean = false, val removalResult: Int? = null,
) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(UUID.fromString(stageToken).toString() == stageToken)
        require(family in setOf("manga", "anime") && kind in setOf("install", "update", "uninstall"))
        require(packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")))
        require(versionCode >= 0 && signer.matches(Regex("[a-f0-9]{64}")))
        require(sessionId == null || sessionId >= 0)
        require(reason == null || reason.length <= 128)
        require(if (kind == "uninstall") requestCode in 0x4000..0x7fff && sessionId == null && !stageTransferred && !successCallbackReceived
            else requestCode == null && !removalLaunched && removalResult == null)
    }
    fun matches(identity: ApkIdentity?) = identity != null && identity.compatibility == "compatible" &&
        identity.family == family && identity.packageName == packageName && identity.versionCode == versionCode &&
        identity.signers == listOf(signer)
    fun toMap() = mapOf("id" to id, "family" to family, "packageName" to packageName,
        "versionCode" to versionCode, "signer" to signer, "kind" to kind,
        "sessionId" to sessionId, "status" to status.name.lowercase(), "stageTransferred" to stageTransferred,
        "reason" to reason)
}

/** App-private operation history. Failed persistence never publishes new memory state. */
internal class ApkInstallJournal(private val file: File) {
    private val atomic = AtomicFile(file)
    private var nextRequestCode = 0x4000
    private var operations: List<ApkInstallOperation> = read()
    private fun read(): List<ApkInstallOperation> {
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        val root = JSONObject(atomic.openRead().use { it.readBytesBounded(1024 * 1024) }.toString(Charsets.UTF_8))
        require(root.getInt("schema") == 1)
        nextRequestCode = root.optInt("nextRequestCode", 0x4000)
        require(nextRequestCode in 0x4000..0x8000)
        val rows = root.getJSONArray("operations")
        require(rows.length() <= 100)
        val result = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ApkInstallOperation(row.getString("id"), row.getString("family"), row.getString("packageName"),
                row.getLong("versionCode"), row.getString("signer"), row.getString("kind"), row.getString("stageToken"),
                if (row.isNull("sessionId")) null else row.getInt("sessionId"),
                InstallStatus.valueOf(row.getString("status")), row.getBoolean("stageTransferred"),
                if (row.isNull("reason")) null else row.getString("reason"), row.getBoolean("successCallbackReceived"),
                if (row.isNull("requestCode")) null else row.getInt("requestCode"), row.optBoolean("removalLaunched", false),
                if (row.isNull("removalResult")) null else row.getInt("removalResult"))
        }
        require(result.map { it.id }.toSet().size == result.size)
        val requests = result.mapNotNull { it.requestCode }
        require(requests.toSet().size == requests.size && requests.all { it < nextRequestCode })
        require(result.filter { !it.status.terminal }.map { it.packageName }.toSet().size == result.count { !it.status.terminal })
        return result
    }
    @Synchronized fun all(): List<ApkInstallOperation> = operations.toList()
    @Synchronized fun find(id: String): ApkInstallOperation = operations.firstOrNull { it.id == id } ?: error("Unknown operation")
    @Synchronized fun create(identity: ApkIdentity, kind: String, token: String): ApkInstallOperation {
        require(identity.compatibility == "compatible")
        check(operations.none { it.packageName == identity.packageName && !it.status.terminal }) { "Package operation already live" }
        val request = if (kind == "uninstall") nextRequestCode.also { check(it <= 0x7fff) { "Uninstall request IDs exhausted" } } else null
        val operation = ApkInstallOperation(UUID.randomUUID().toString(), identity.family, identity.packageName,
            identity.versionCode, identity.signers.single(), kind, token, requestCode = request)
        var next = operations
        if (next.size == 100) {
            val oldest = next.firstOrNull { it.status.terminal } ?: error("Operation history full")
            next = next.filter { it.id != oldest.id }
        }
        persist(next + operation, if (request != null) request + 1 else nextRequestCode)
        return operation
    }
    @Synchronized fun save(operation: ApkInstallOperation) {
        val previous = find(operation.id)
        require(previous.copy(sessionId = operation.sessionId, status = operation.status,
            stageTransferred = operation.stageTransferred, reason = operation.reason,
            successCallbackReceived = operation.successCallbackReceived, removalLaunched = operation.removalLaunched,
            removalResult = operation.removalResult) == operation) { "Operation identity changed" }
        require(!previous.successCallbackReceived || operation.successCallbackReceived)
        require(!previous.removalLaunched || operation.removalLaunched)
        require(previous.removalResult == null || previous.removalResult == operation.removalResult)
        check(!previous.status.terminal || previous == operation) { "Operation already terminal" }
        persist(operations.map { if (it.id == operation.id) operation else it })
    }
    @Synchronized fun acceptsCallback(id: String, session: Int, pkg: String?): Boolean {
        val operation = operations.firstOrNull { it.id == id } ?: return false
        return operation.kind != "uninstall" && !operation.status.terminal && operation.sessionId == session &&
            (pkg == null || pkg == operation.packageName)
    }
    private fun persist(next: List<ApkInstallOperation>, nextCode: Int = nextRequestCode) {
        val rows = JSONArray()
        next.forEach { op -> rows.put(JSONObject().put("id", op.id).put("family", op.family)
            .put("packageName", op.packageName).put("versionCode", op.versionCode).put("signer", op.signer)
            .put("kind", op.kind).put("stageToken", op.stageToken).put("sessionId", op.sessionId ?: JSONObject.NULL)
            .put("status", op.status.name).put("stageTransferred", op.stageTransferred)
            .put("successCallbackReceived", op.successCallbackReceived)
            .put("requestCode", op.requestCode ?: JSONObject.NULL).put("removalLaunched", op.removalLaunched)
            .put("removalResult", op.removalResult ?: JSONObject.NULL)
            .put("reason", op.reason ?: JSONObject.NULL)) }
        file.parentFile!!.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(JSONObject().put("schema", 1).put("nextRequestCode", nextCode).put("operations", rows).toString().toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        operations = next
        nextRequestCode = nextCode
    }
}
