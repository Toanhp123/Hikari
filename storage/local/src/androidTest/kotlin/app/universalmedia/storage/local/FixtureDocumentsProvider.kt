package app.universalmedia.storage.local

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Lives only in the instrumentation APK; production declares no provider or extra permission. */
class FixtureDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == "root" && documentId in setOf(
            "root", "dir", "bad", "video", "text", "missing", "empty", "missing-on-open", "denied",
        )

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID),
    ).apply { addRow(arrayOf("fixture", "root")) }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        if (documentId == "missing") throw FileNotFoundException()
        if (documentId == "empty") return documents(projection, emptyList())
        return documents(projection, listOf(documentId)).apply {
            if (rootLoading && documentId == "root") {
                extras = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) }
            }
        }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        queries.add(parentDocumentId)
        if (parentDocumentId == "bad") throw SecurityException()
        val children = when (parentDocumentId) {
            "root" -> if (brokenBranch) listOf("bad", "dir", "video") else listOf("dir", "video")
            "dir" -> listOf("root", "text")
            else -> emptyList()
        }
        return documents(projection, children).apply {
            if (loading) extras = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) }
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (documentId == "missing" || documentId == "missing-on-open") throw FileNotFoundException()
        if (documentId == "denied") throw SecurityException()
        require(mode == "r")
        val file = File(requireNotNull(context).cacheDir, "saf-fixture.bin")
        file.writeBytes(byteArrayOf(1, 2, 3))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun documents(projection: Array<out String>?, ids: List<String>): MatrixCursor {
        val columns = requireNotNull(projection)
        return MatrixCursor(columns).apply {
            ids.forEach { id ->
                addRow(columns.map { column ->
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> if (malformed && id == "video") null else id
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> "$id fixture"
                        DocumentsContract.Document.COLUMN_MIME_TYPE -> when (id) {
                            "root", "dir", "bad" -> DocumentsContract.Document.MIME_TYPE_DIR
                            "video" -> "video/mp4"
                            else -> "text/plain"
                        }
                        DocumentsContract.Document.COLUMN_SIZE -> 3L
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1000L
                        else -> error("Unexpected projection")
                    }
                }.toTypedArray<Any?>())
            }
        }
    }

    companion object {
        const val AUTHORITY = "app.universalmedia.storage.local.fixture"
        val queries = mutableListOf<String>()
        var loading = false
        var rootLoading = false
        var brokenBranch = false
        var malformed = false

        fun reset() {
            queries.clear()
            loading = false
            rootLoading = false
            brokenBranch = false
            malformed = false
        }
    }
}
