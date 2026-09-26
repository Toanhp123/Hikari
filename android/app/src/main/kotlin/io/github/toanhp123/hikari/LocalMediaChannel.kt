package io.github.toanhp123.hikari

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private class MissingTreeException : IllegalStateException("Folder is no longer available.")

/** SAF locators stay opaque; no filesystem path conversion or broad permission. */
class LocalMediaChannel(private val activity: Activity, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hikari/local_media")
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var picker: MethodChannel.Result? = null
    private var closed = false

    init {
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "pickTree" -> openPicker(result)
                "selectedTree" -> background(result) { selectedTree() }
                "children" -> background(result) { children(Uri.parse(call.arguments as String)) }
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
        background(result) {
            val flags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            require(flags != 0) { "Folder did not grant read access." }

            val previous = storedTreeUri()
            activity.contentResolver.takePersistableUriPermission(tree, flags)
            val selected = try {
                describeTree(tree)
            } catch (error: Exception) {
                if (tree != previous) releasePersistedRead(tree)
                throw error
            }

            val saved = preferences.edit().putString(KEY_TREE_URI, tree.toString()).commit()
            if (!saved) {
                if (tree != previous) releasePersistedRead(tree)
                error("Selected folder could not be saved.")
            }
            if (previous != null && previous != tree) releasePersistedRead(previous)
            selected
        }
        return true
    }

    private fun selectedTree(): Map<String, String>? {
        val tree = storedTreeUri() ?: return null
        val hasReadGrant = activity.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission
        }
        if (!hasReadGrant) {
            check(preferences.edit().remove(KEY_TREE_URI).commit()) {
                "Selected folder state could not be cleared."
            }
            error("Selected folder permission is no longer available. Choose the folder again.")
        }

        return try {
            describeTree(tree)
        } catch (error: SecurityException) {
            clearStoredTree(tree)
            throw IllegalStateException(
                "Selected folder permission is no longer available. Choose the folder again.",
                error
            )
        } catch (error: MissingTreeException) {
            clearStoredTree(tree)
            throw IllegalStateException(
                "Selected folder is no longer available. Choose the folder again.",
                error
            )
        }
    }

    private fun storedTreeUri(): Uri? =
        preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    private fun describeTree(tree: Uri): Map<String, String> {
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
        releasePersistedRead(tree)
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

    private fun children(parent: Uri): List<Map<String, Any>> {
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
            while (cursor.moveToNext()) {
                require(rows.size < 50000) {
                    "Folder exceeds 50,000 entries. Choose a smaller folder."
                }
                val id = cursor.getString(0) ?: error("Document has no identifier.")
                rows.add(
                    mapOf(
                        "id" to DocumentsContract.buildDocumentUriUsingTree(parent, id).toString(),
                        "name" to (cursor.getString(1) ?: id),
                        "isDirectory" to
                            (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                    )
                )
            }
            require(!cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                "Storage provider is still loading. Please retry."
            }
        }
        return rows
    }

    private fun background(result: MethodChannel.Result, action: () -> Any?) {
        worker.execute {
            try {
                val value = action()
                activity.runOnUiThread { if (!closed) result.success(value) }
            } catch (error: Exception) {
                activity.runOnUiThread {
                    if (!closed) result.error("storage", error.message, null)
                }
            }
        }
    }

    fun close() {
        closed = true
        picker?.error("closed", "Folder picker was closed.", null)
        picker = null
        channel.setMethodCallHandler(null)
        worker.shutdownNow()
    }

    private companion object {
        const val REQUEST_TREE = 4101
        const val PREFERENCES = "hikari_local_media"
        const val KEY_TREE_URI = "selected_tree_uri"
    }
}
