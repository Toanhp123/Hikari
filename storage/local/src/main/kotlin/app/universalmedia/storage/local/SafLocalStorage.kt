package app.universalmedia.storage.local

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.LocalTreeObservationSource
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.ScanCancellation
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.TraversalResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SafRegistrationResult {
    data class Registered(val evidence: RootRegistrationEvidence) : SafRegistrationResult
    data class Failed(val failure: LocalAccessFailure) : SafRegistrationResult
}

sealed interface SafDocumentResult {
    data class Readable(val observation: LocalDocumentObservation) : SafDocumentResult
    data class Failed(val failure: LocalAccessFailure) : SafDocumentResult
}

/** Android-only adapter. Callers persist successful evidence via StorageRootStore. */
class SafLocalStorage(private val resolver: ContentResolver) : LocalTreeObservationSource {
    suspend fun register(treeUri: Uri): SafRegistrationResult = withContext(Dispatchers.IO) {
        try {
            val tree = SafTree.decode(LocalRootDescriptor(treeUri.authority.orEmpty(), treeUri.toString()))
            providerCall { resolver.takePersistableUriPermission(tree.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (providerCall { resolver.persistedUriPermissions.none { it.uri == tree.uri && it.isReadPermission } }) {
                return@withContext SafRegistrationResult.Failed(LocalAccessFailure.ACCESS_LOST)
            }
            val row = queryDocument(tree.document(tree.id), CancellationSignal())
            if (row.mimeType != DIRECTORY_MIME) {
                return@withContext SafRegistrationResult.Failed(LocalAccessFailure.UNAVAILABLE)
            }
            SafRegistrationResult.Registered(
                RootRegistrationEvidence(LocalRootDescriptor(tree.authority, tree.uri.toString()), true, System.currentTimeMillis()),
            )
        } catch (failure: SafAccessException) {
            SafRegistrationResult.Failed(failure.failure)
        } catch (_: IllegalArgumentException) {
            SafRegistrationResult.Failed(LocalAccessFailure.UNAVAILABLE)
        }
    }

    override suspend fun observe(
        root: StorageRoot,
        scope: DeclaredScanScope,
        cancellation: ScanCancellation,
        onBatch: suspend (List<LocalDocumentObservation>) -> Unit,
    ): TraversalResult = withContext(Dispatchers.IO) {
        if (scope.rootId != root.id || scope.configGeneration != root.configGeneration) {
            return@withContext TraversalResult.Incomplete(listOf(CoverageGap(null, IncompleteReason.SUPERSEDED)))
        }
        if (cancellation.isCancelled()) return@withContext TraversalResult.Cancelled
        val tree = try {
            SafTree.decode(root.descriptor)
        } catch (_: IllegalArgumentException) {
            return@withContext TraversalResult.Failed(LocalAccessFailure.UNAVAILABLE)
        }
        withSignal(cancellation) { signal ->
            try {
                val row = queryDocument(tree.document(tree.id), signal)
                if (row.mimeType != DIRECTORY_MIME) {
                    return@withSignal TraversalResult.Failed(LocalAccessFailure.UNAVAILABLE)
                }
            } catch (failure: SafAccessException) {
                return@withSignal when {
                    cancellation.isCancelled() -> TraversalResult.Cancelled
                    failure.providerLoading -> TraversalResult.Incomplete(
                        listOf(CoverageGap(null, IncompleteReason.PROVIDER_LOADING)),
                    )
                    else -> TraversalResult.Failed(failure.failure)
                }
            }
            val source = object : SafDirectorySource {
                override fun locator(documentId: String): String = tree.document(documentId).toString()
                override fun children(documentId: String): SafListing = providerCall {
                    val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree.uri, documentId)
                    val cursor = resolver.query(uri, PROJECTION, null, null, null, signal)
                        ?: throw SafAccessException(LocalAccessFailure.UNAVAILABLE)
                    CursorListing(cursor)
                }
            }
            SafTraversal(source).observe(root, scope, tree.id, cancellation, onBatch)
        }
    }

    /** Revalidates persisted current-locator evidence; never creates canonical identity or runtime content. */
    suspend fun inspect(root: StorageRoot, locator: LocalDocumentLocator): SafDocumentResult = withContext(Dispatchers.IO) {
        try {
            val tree = SafTree.decode(root.descriptor)
            require(locator.rootId == root.id && locator.providerAuthority == tree.authority)
            require(locator.documentLocator.length <= MAX_PROVIDER_TEXT * 6)
            val uri = Uri.parse(locator.documentLocator)
            require(uri.scheme == "content" && uri.authority == tree.authority)
            require(DocumentsContract.getTreeDocumentId(uri) == tree.id)
            val documentId = DocumentsContract.getDocumentId(uri)
            require(uri == tree.document(documentId))
            val row = queryDocument(uri, CancellationSignal())
            if (row.mimeType == DIRECTORY_MIME) return@withContext SafDocumentResult.Failed(LocalAccessFailure.UNAVAILABLE)
            providerCall { resolver.openFileDescriptor(uri, "r")?.use { } }
                ?: return@withContext SafDocumentResult.Failed(LocalAccessFailure.UNAVAILABLE)
            SafDocumentResult.Readable(
                LocalDocumentObservation(locator, row.name, row.mimeType, row.size, row.modified, System.currentTimeMillis()),
            )
        } catch (failure: SafAccessException) {
            SafDocumentResult.Failed(failure.failure)
        } catch (_: IllegalArgumentException) {
            SafDocumentResult.Failed(LocalAccessFailure.UNAVAILABLE)
        }
    }

    private fun queryDocument(uri: Uri, signal: CancellationSignal): SafRow = providerCall {
        // Null can mean provider failure or a swallowed FileNotFoundException; it cannot prove absence.
        val cursor = resolver.query(uri, PROJECTION, null, null, null, signal)
            ?: throw SafAccessException(LocalAccessFailure.UNAVAILABLE)
        CursorListing(cursor).use { listing ->
            if (listing.loading) throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE, providerLoading = true)
            val row = listing.next() ?: throw SafAccessException(LocalAccessFailure.NOT_FOUND)
            if (!row.valid() || row.id != DocumentsContract.getDocumentId(uri)) {
                throw SafAccessException(LocalAccessFailure.UNAVAILABLE)
            }
            row
        }
    }
}

private class SafTree(val uri: Uri, val authority: String, val id: String) {
    fun document(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(uri, id)

    companion object {
        fun decode(descriptor: LocalRootDescriptor): SafTree {
            require(descriptor.treeLocator.length <= MAX_PROVIDER_TEXT * 6)
            val uri = Uri.parse(descriptor.treeLocator)
            require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.authority == descriptor.providerAuthority)
            val id = DocumentsContract.getTreeDocumentId(uri)
            require(id.isNotBlank() && id.length <= MAX_PROVIDER_TEXT)
            require(uri == DocumentsContract.buildTreeDocumentUri(uri.authority, id))
            return SafTree(uri, requireNotNull(uri.authority), id)
        }
    }
}

private class CursorListing(private val cursor: Cursor) : SafListing {
    override val loading: Boolean
        get() = providerCall { cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false) }

    override fun next(): SafRow? = providerCall {
        if (cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID) < 0 ||
            cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE) < 0
        ) {
            throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
        }
        if (!cursor.moveToNext()) return@providerCall null
        fun text(column: String): String? {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
        }
        fun number(column: String): Long? {
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) return null
            if (cursor.getType(index) != Cursor.FIELD_TYPE_INTEGER) {
                throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
            }
            return cursor.getLong(index)
        }
        SafRow(
            text(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            text(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            text(DocumentsContract.Document.COLUMN_MIME_TYPE),
            number(DocumentsContract.Document.COLUMN_SIZE),
            number(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
        )
    }

    override fun close() = providerCall { cursor.close() }
}

private val PROJECTION = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
)

private inline fun <T> providerCall(block: () -> T): T = try {
    block()
} catch (failure: SafAccessException) {
    throw failure
} catch (_: SecurityException) {
    throw SafAccessException(LocalAccessFailure.ACCESS_LOST)
} catch (_: FileNotFoundException) {
    throw SafAccessException(LocalAccessFailure.NOT_FOUND)
} catch (_: IOException) {
    throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
} catch (_: OperationCanceledException) {
    throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
} catch (_: RuntimeException) {
    throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
}

/** Bridge the polling domain token and coroutine cancellation to a blocking Binder query. */
private suspend fun <T> withSignal(cancellation: ScanCancellation, block: suspend (CancellationSignal) -> T): T =
    coroutineScope {
        val signal = CancellationSignal()
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                while (isActive && !cancellation.isCancelled()) delay(25)
            } finally {
                signal.cancel()
            }
        }
        try {
            block(signal)
        } finally {
            watcher.cancelAndJoin()
        }
    }
