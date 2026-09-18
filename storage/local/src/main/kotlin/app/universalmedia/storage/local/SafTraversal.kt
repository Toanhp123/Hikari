package app.universalmedia.storage.local

import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.ScanCancellation
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.TraversalResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val DIRECTORY_MIME = "vnd.android.document/directory"
internal const val MAX_PROVIDER_TEXT = 4096

internal data class SafRow(
    val id: String?,
    val name: String?,
    val mimeType: String?,
    val size: Long?,
    val modified: Long?,
)

internal class SafAccessException(val failure: LocalAccessFailure, val providerLoading: Boolean = false) : Exception()

internal interface SafListing : AutoCloseable {
    val loading: Boolean
    fun next(): SafRow?
}

internal interface SafDirectorySource {
    fun children(documentId: String): SafListing
    fun locator(documentId: String): String
}

/** Streaming provider boundary keeps traversal memory bounded independently of cursor row count. */
internal class SafTraversal(
    private val source: SafDirectorySource,
    private val batchSize: Int = 128,
    private val maxEntries: Int = 10_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    init {
        require(batchSize in 1..1024 && maxEntries > 0)
    }

    suspend fun observe(
        root: StorageRoot,
        scope: DeclaredScanScope,
        treeId: String,
        cancellation: ScanCancellation,
        onBatch: suspend (List<LocalDocumentObservation>) -> Unit,
    ): TraversalResult {
        if (scope.rootId != root.id || scope.configGeneration != root.configGeneration) {
            return TraversalResult.Incomplete(listOf(CoverageGap(null, IncompleteReason.SUPERSEDED)))
        }
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf(treeId)
        val gaps = mutableListOf<CoverageGap>()
        val batch = ArrayList<LocalDocumentObservation>(batchSize)
        var entries = 0
        var budgetReached = false
        pending.add(treeId)
        fun locator(id: String) = LocalDocumentLocator(root.id, root.descriptor.providerAuthority, source.locator(id))
        fun gap(id: String, reason: IncompleteReason) {
            if (gaps.size < 64) gaps.add(CoverageGap(locator(id), reason)) else budgetReached = true
        }
        suspend fun cancelled(): Boolean {
            currentCoroutineContext().ensureActive()
            return cancellation.isCancelled()
        }
        suspend fun flush() {
            if (batch.isNotEmpty()) {
                onBatch(batch.toList())
                batch.clear()
            }
        }
        while (pending.isNotEmpty() && !budgetReached) {
            if (cancelled()) return TraversalResult.Cancelled
            val directory = pending.removeFirst()
            val listing = try {
                source.children(directory)
            } catch (failure: SafAccessException) {
                if (cancelled()) return TraversalResult.Cancelled
                if (directory == treeId) return TraversalResult.Failed(failure.failure)
                gap(directory, IncompleteReason.INACCESSIBLE_BRANCH)
                continue
            }
            try {
                while (!budgetReached) {
                    if (cancelled()) return TraversalResult.Cancelled
                    val row = try {
                        listing.next()
                    } catch (_: SafAccessException) {
                        gap(directory, IncompleteReason.INACCESSIBLE_BRANCH)
                        break
                    } ?: break
                    if (++entries > maxEntries) {
                        budgetReached = true
                        break
                    }
                    if (!row.valid()) {
                        gap(directory, IncompleteReason.INVALID_PROVIDER_DATA)
                        continue
                    }
                    val id = requireNotNull(row.id)
                    if (row.mimeType == DIRECTORY_MIME) {
                        if (visited.add(id)) pending.addLast(id)
                    } else {
                        batch.add(LocalDocumentObservation(locator(id), row.name, row.mimeType, row.size, row.modified, now()))
                        if (batch.size == batchSize) flush()
                    }
                }
                try {
                    if (listing.loading) gap(directory, IncompleteReason.PROVIDER_LOADING)
                } catch (_: SafAccessException) {
                    gap(directory, IncompleteReason.INACCESSIBLE_BRANCH)
                }
            } finally {
                try {
                    listing.close()
                } catch (_: SafAccessException) {
                    gap(directory, IncompleteReason.INACCESSIBLE_BRANCH)
                }
            }
        }
        if (cancelled()) return TraversalResult.Cancelled
        flush()
        if (cancelled()) return TraversalResult.Cancelled
        if (budgetReached) gaps.add(CoverageGap(null, IncompleteReason.OBSERVATION_BUDGET))
        return if (gaps.isEmpty()) TraversalResult.Complete else TraversalResult.Incomplete(gaps)
    }
}

internal fun SafRow.valid(): Boolean =
    !id.isNullOrBlank() && id.length <= MAX_PROVIDER_TEXT &&
        !mimeType.isNullOrBlank() && mimeType.length <= 256 &&
        (name == null || name.length <= MAX_PROVIDER_TEXT) &&
        (size == null || size >= 0) && (modified == null || modified >= 0)
