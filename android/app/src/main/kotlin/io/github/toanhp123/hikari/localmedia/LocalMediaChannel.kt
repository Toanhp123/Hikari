package io.github.toanhp123.hikari.localmedia

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.os.SystemClock
import android.provider.DocumentsContract
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

private class MissingTreeException : IllegalStateException("Folder is no longer available.")
private class RootAccessException(message: String) : IllegalStateException(message)

/** SAF locators stay opaque; no filesystem path conversion or broad permission. */
class LocalMediaChannel(private val activity: Activity, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hikari/local_media")
    private val worker = Executors.newSingleThreadExecutor()
    private val scans = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pending = mutableSetOf<Work>()
    private val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var picker: MethodChannel.Result? = null
    @Volatile private var closed = false

    init {
        // Serialized before reads: stale copies (including interrupted .part files)
        // belong to the previous engine, never to a live reader in this engine.
        worker.execute {
            File(activity.cacheDir, "local_media").listFiles()?.forEach { file ->
                runCatching { deleteMaterialized(file.absolutePath) }
            }
        }
        channel.setMethodCallHandler { call, result ->
            if (closed) {
                result.error("closed", "Local media channel was closed.", null)
                return@setMethodCallHandler
            }
            when (call.method) {
                "cancelScan" -> {
                    val token = call.arguments as? String
                    val obsolete = synchronized(lock) {
                        pending.filter { it.token == token && token != null }
                    }
                    obsolete.forEach { it.cancel() }
                    result.success(null)
                }
                "scanChildren" -> {
                    val token = call.argument<String>("token")
                    background(result, scans, token) { checkCancelled ->
                        require(!token.isNullOrEmpty()) { "Missing scan token." }
                        val id = call.argument<String>("id") ?: error("Missing document identifier.")
                        val remaining = call.argument<Int>("remaining") ?: error("Missing scan bound.")
                        require(remaining in 0..49999) { "Invalid scan bound." }
                        val start = SystemClock.elapsedRealtimeNanos()
                        val rows = children(Uri.parse(id), checkCancelled, remaining)
                        mapOf("rows" to rows, "queryMicros" to
                            (SystemClock.elapsedRealtimeNanos() - start) / 1000)
                    }
                }
                "pickTree" -> openPicker(result)
                "selectedTree" -> background(result, scans) { checkCancelled -> selectedTree(checkCancelled) }
                "children" -> background(result) { children(Uri.parse(call.arguments as String)) }
                "materialize" -> background(result) {
                    val id = call.argument<String>("id") ?: error("Missing document identifier.")
                    val limit = call.argument<Int>("limit") ?: error("Missing materialization limit.")
                    materialize(Uri.parse(id), limit)
                }
                "deleteMaterialized" -> background(result) {
                    val path = call.argument<String>("path") ?: error("Missing materialized path.")
                    deleteMaterialized(path)
                }
                "read" -> background(result) {
                    val id = call.argument<String>("id") ?: error("Missing document identifier.")
                    val limit = call.argument<Int>("limit") ?: error("Missing read limit.")
                    require(limit in 1..(32 * 1024 * 1024)) { "Invalid read limit." }
                    activity.contentResolver.openInputStream(Uri.parse(id)).use { input ->
                        requireNotNull(input) { "Document could not be opened." }
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= limit) {
                                "Content exceeds the reader size limit (${limit / 1024 / 1024} MiB)."
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun openPicker(result: MethodChannel.Result) {
        if (picker != null) {
            result.error("busy", "Folder picker is already open.", null)
            return
        }
        picker = result
        try {
            activity.startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                ),
                REQUEST_TREE
            )
        } catch (error: Exception) {
            picker = null
            result.error("storage", error.message, null)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_TREE) return false
        val result = picker ?: return true
        picker = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            result.success(null)
            return true
        }
        val intent = requireNotNull(data)
        val tree = requireNotNull(intent.data)
        background(result, scans) { checkCancelled ->
            val flags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            require(flags != 0) { "Folder did not grant read access." }

            val previous = storedTreeUri()
            activity.contentResolver.takePersistableUriPermission(tree, flags)
            val selected = try {
                describeTree(tree, checkCancelled)
            } catch (error: Exception) {
                if (tree != previous) releasePersistedRead(tree)
                throw error
            }

            val saved = preferences.edit().putString(KEY_TREE_URI, tree.toString()).commit()
            if (!saved) {
                if (tree != previous) releasePersistedRead(tree)
                error("Selected folder could not be saved.")
            }
            if (previous != null && previous != tree) releaseAfterReaders(previous)
            selected
        }
        return true
    }

    private fun selectedTree(checkCancelled: (() -> Unit)?): Map<String, String>? {
        val tree = storedTreeUri() ?: return null
        val hasReadGrant = activity.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission
        }
        if (!hasReadGrant) {
            check(preferences.edit().remove(KEY_TREE_URI).commit()) {
                "Selected folder state could not be cleared."
            }
            throw RootAccessException(
                "Selected folder permission is no longer available. Choose the folder again."
            )
        }

        return try {
            describeTree(tree, checkCancelled)
        } catch (error: SecurityException) {
            clearStoredTree(tree)
            throw RootAccessException(
                "Selected folder permission is no longer available. Choose the folder again."
            )
        } catch (error: MissingTreeException) {
            clearStoredTree(tree)
            throw RootAccessException(
                "Selected folder is no longer available. Choose the folder again."
            )
        }
    }

    private fun storedTreeUri(): Uri? =
        preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    private fun describeTree(tree: Uri, checkCancelled: (() -> Unit)?): Map<String, String> {
        val document = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        val name = activity.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        ).use { cursor ->
            requireNotNull(cursor) { "Folder could not be queried." }
            checkCancelled?.invoke()
            check(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                "Storage provider is still loading. Please retry."
            }
            if (!cursor.moveToFirst()) throw MissingTreeException()
            cursor.getString(0) ?: "Selected folder"
        }
        return mapOf("id" to document.toString(), "name" to name)
    }

    private fun clearStoredTree(tree: Uri) {
        check(preferences.edit().remove(KEY_TREE_URI).commit()) {
            "Selected folder state could not be cleared."
        }
        releaseAfterReaders(tree)
    }

    private fun releaseAfterReaders(tree: Uri) {
        try {
            worker.execute {
                try {
                    // Reader barrier first; serialize release with picker take/commit.
                    scans.execute {
                        if (storedTreeUri() != tree) releasePersistedRead(tree)
                    }
                } catch (_: RejectedExecutionException) {
                    // Engine closed: retaining an old grant is safer than revoking a new one.
                }
            }
        } catch (_: RejectedExecutionException) {
            // Engine closed: retain grant rather than overtake an active reader.
        }
    }

    private fun releasePersistedRead(tree: Uri) {
        val hasReadGrant = activity.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission
        }
        if (!hasReadGrant) return
        try {
            activity.contentResolver.releasePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // The grant disappeared between inspection and release.
        }
    }

    // This directory contains disposable reader copies only. The worker queue
    // serializes materialization and deletion, so accounting includes any copy
    // retained by another reader as well as interrupted .part files.
    private fun materialize(uri: Uri, limit: Int): Map<String, Any> {
        require(limit in 1..(1024 * 1024 * 1024)) { "Invalid materialization limit." }
        val directory = File(activity.cacheDir, "local_media")
        require(directory.mkdirs() || directory.isDirectory) { "Cache directory unavailable." }
        val totalBudget = 1024L * 1024 * 1024
        val existingBytes = directory.listFiles()?.sumOf { file ->
            if (file.isFile) file.length() else 0L
        } ?: 0L
        require(existingBytes < totalBudget) { "Archive temporary storage budget exhausted." }
        val availableBudget = minOf(limit.toLong(), totalBudget - existingBytes)
        val target = File(directory, "${UUID.randomUUID()}.part")
        try {
            activity.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Document could not be opened." }
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= availableBudget) {
                            "Content exceeds archive temporary storage budget."
                        }
                        // Preserve headroom for SQLite, progress and other app
                        // work. Actual I/O failure is still handled by cleanup.
                        if (total == count.toLong() || total % (8L * 1024 * 1024) < count) {
                            require(directory.usableSpace > 16L * 1024 * 1024) {
                                "Not enough free space to open archive."
                            }
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            val finalFile = File(directory, target.name.removeSuffix(".part"))
            if (!target.renameTo(finalFile)) {
                throw IllegalStateException("Materialized content could not be finalized.")
            }
            if (closed) {
                finalFile.delete()
                throw OperationCanceledException()
            }
            return mapOf("path" to finalFile.absolutePath, "size" to finalFile.length())
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun deleteMaterialized(path: String): Boolean {
        val directory = File(activity.cacheDir, "local_media").canonicalFile
        val target = File(path).canonicalFile
        require(target.parentFile == directory) { "Invalid materialized path." }
        return !target.exists() || target.delete()
    }

    private fun children(
        parent: Uri, checkCancelled: (() -> Unit)? = null, remaining: Int = 50000
    ): List<Map<String, Any>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent)
        )
        val rows = mutableListOf<Map<String, Any>>()
        activity.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        ).use { cursor ->
            requireNotNull(cursor) { "Folder could not be queried." }
            checkCancelled?.invoke()
            while (cursor.moveToNext()) {
                checkCancelled?.invoke()
                require(rows.size < remaining) {
                    "Folder exceeds 50,000 entries. Choose a smaller folder."
                }
                val id = cursor.getString(0) ?: error("Document has no identifier.")
                require(id.isNotEmpty()) { "Document has no identifier." }
                val mime = cursor.getString(2)
                if (checkCancelled != null) require(!mime.isNullOrEmpty()) { "Document has no type." }
                rows.add(
                    mapOf(
                        "id" to DocumentsContract.buildDocumentUriUsingTree(parent, id).toString(),
                        "name" to (cursor.getString(1) ?: id),
                        "isDirectory" to
                            (mime == DocumentsContract.Document.MIME_TYPE_DIR)
                    )
                )
            }
            checkCancelled?.invoke()
            require(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                "Storage provider is still loading. Please retry."
            }
        }
        return rows
    }

    /** Pending result ownership survives shutdownNow dropping queued tasks. */
    private inner class Work(
        val result: MethodChannel.Result,
        val token: String?,
        val discovery: Boolean
    ) {
        private val cancelled = AtomicBoolean()
        fun checkCancelled() {
            if (cancelled.get() || closed) throw OperationCanceledException()
        }
        private val completed = AtomicBoolean()
        fun finish(value: Any? = null, code: String? = null, message: String? = null): Boolean {
            if (!completed.compareAndSet(false, true)) return false
            synchronized(lock) { pending.remove(this) }
            main.post {
                if (code == null) result.success(value) else result.error(code, message, null)
            }
            return true
        }
        fun cancel() {
            cancelled.set(true)
            finish(code = "cancelled", message = "Discovery was cancelled.")
        }
    }

    private fun background(
        result: MethodChannel.Result,
        executor: ExecutorService = worker,
        token: String? = null,
        action: ((() -> Unit)?) -> Any?
    ) {
        val work = Work(result, token, executor === scans)
        synchronized(lock) {
            if (closed) {
                work.finish(code = "closed", message = "Local media channel was closed.")
                return
            }
            pending.add(work)
        }
        val queued = SystemClock.elapsedRealtimeNanos()
        try {
            executor.execute {
                try {
                    work.checkCancelled()
                    val queueMicros = (SystemClock.elapsedRealtimeNanos() - queued) / 1000
                    val value = action(if (work.discovery) work::checkCancelled else null)
                    val response = if (token != null && value is Map<*, *>) {
                        value + ("queueMicros" to queueMicros)
                    } else value
                    if (!work.finish(response) && value is Map<*, *> && value["path"] is String) {
                        // Close may win after materialization's final closed check.
                        deleteMaterialized(value["path"] as String)
                    }
                } catch (error: Exception) {
                    work.finish(code = when (error) {
                        is OperationCanceledException -> "cancelled"
                        is RootAccessException, is SecurityException -> "access"
                        else -> "storage"
                    }, message = error.message)
                }
            }
        } catch (_: RejectedExecutionException) {
            work.finish(code = "closed", message = "Local media channel was closed.")
        }
    }

    fun close() {
        val unfinished = synchronized(lock) {
            closed = true
            pending.toList()
        }
        picker?.error("closed", "Folder picker was closed.", null)
        picker = null
        channel.setMethodCallHandler(null)
        unfinished.forEach {
            it.finish(code = "closed", message = "Local media channel was closed.")
        }
        scans.shutdownNow()
        worker.shutdownNow()
    }

    private companion object {
        const val REQUEST_TREE = 4101
        const val PREFERENCES = "hikari_local_media"
        const val KEY_TREE_URI = "selected_tree_uri"
    }
}
