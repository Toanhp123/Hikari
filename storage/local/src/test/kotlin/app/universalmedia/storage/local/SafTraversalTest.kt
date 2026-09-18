package app.universalmedia.storage.local

import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.TraversalResult
import app.universalmedia.core.model.RootId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafTraversalTest {
    private val root = StorageRoot(RootId.generate(), LocalRootDescriptor("provider", "tree"), 1, LocalAccessState.READABLE)
    private val scope = DeclaredScanScope(root.id, 1)

    @Test
    fun visitsDirectoriesOnceAndAwaitsBoundedBatchesWithoutRecognizingMedia() = runBlocking {
        val provider = Fixture(mapOf("root" to listOf(dir("a"), file("1"), file("2")), "a" to listOf(dir("root"), file("3"))))
        val batches = mutableListOf<List<LocalDocumentObservation>>()
        val result = SafTraversal(provider, batchSize = 2).observe(root, scope, "root", { false }, batches::add)
        assertEquals(TraversalResult.Complete, result)
        assertEquals(listOf("root", "a"), provider.queries)
        assertEquals(listOf(2, 1), batches.map { it.size })
        assertEquals(listOf("1", "2", "3"), batches.flatten().map { it.locator.documentLocator })
        assertTrue(batches.flatten().all { it.locator.rootId == root.id && it.mimeType == "text/plain" })
        assertEquals(2, provider.closed)
    }

    @Test
    fun loadingPreservesPositiveEvidenceButCannotComplete() = runBlocking {
        val provider = Fixture(mapOf("root" to listOf(file("1"))), loading = true)
        val seen = mutableListOf<LocalDocumentObservation>()
        val result = SafTraversal(provider).observe(root, scope, "root", { false }) { seen.addAll(it) }
        assertEquals(1, seen.size)
        assertEquals(IncompleteReason.PROVIDER_LOADING, (result as TraversalResult.Incomplete).gaps.single().reason)
    }

    @Test
    fun branchFailureDoesNotLoseSuccessfulSiblings() = runBlocking {
        val provider = Fixture(mapOf("root" to listOf(dir("bad"), dir("good")), "good" to listOf(file("1"))))
        val seen = mutableListOf<LocalDocumentObservation>()
        val result = SafTraversal(provider).observe(root, scope, "root", { false }) { seen.addAll(it) }
        assertEquals(1, seen.size)
        assertEquals("bad", (result as TraversalResult.Incomplete).gaps.single().locator?.documentLocator)
    }

    @Test
    fun cancellationAfterBatchStopsAndClosesCursor() = runBlocking {
        val provider = Fixture(mapOf("root" to listOf(file("1"), file("2"))))
        var cancelled = false
        val result = SafTraversal(provider, batchSize = 1).observe(root, scope, "root", { cancelled }) { cancelled = true }
        assertEquals(TraversalResult.Cancelled, result)
        assertEquals(1, provider.closed)
    }

    @Test
    fun staleScopeNeverQueriesProvider() = runBlocking {
        val provider = Fixture(emptyMap())
        val result = SafTraversal(provider).observe(root, scope.copy(configGeneration = 2), "root", { false }) { }
        assertEquals(IncompleteReason.SUPERSEDED, (result as TraversalResult.Incomplete).gaps.single().reason)
        assertTrue(provider.queries.isEmpty())
    }

    @Test
    fun malformedRowsCannotManufactureCompleteCoverage() = runBlocking {
        val rows = listOf(file(""), file("x").copy(mimeType = null), file("y").copy(size = -1))
        val seen = mutableListOf<LocalDocumentObservation>()
        val result = SafTraversal(Fixture(mapOf("root" to rows))).observe(root, scope, "root", { false }) { seen.addAll(it) }
        assertTrue(result is TraversalResult.Incomplete)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun budgetStopsWideProviderGraphAsIncomplete() = runBlocking {
        val provider = Fixture(mapOf("root" to (1..20).map { dir("d$it") }))
        val result = SafTraversal(provider, maxEntries = 3).observe(root, scope, "root", { false }) { }
        assertTrue((result as TraversalResult.Incomplete).gaps.any { it.reason == IncompleteReason.OBSERVATION_BUDGET })
        assertEquals(listOf("root"), provider.queries)
        assertEquals(1, provider.closed)
    }

    @Test
    fun rootFailureIsTypedAndSinkFailurePropagates() = runBlocking {
        val result = SafTraversal(Fixture(emptyMap())).observe(root, scope, "root", { false }) { }
        assertEquals(TraversalResult.Failed(LocalAccessFailure.NOT_FOUND), result)
        val failure = IllegalStateException("sink")
        try {
            SafTraversal(Fixture(mapOf("root" to listOf(file("1")))), batchSize = 1)
                .observe(root, scope, "root", { false }) { throw failure }
            fail("Expected sink failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun cursorCloseFailureCannotEscapeOrManufactureCompletion() = runBlocking {
        val source = object : SafDirectorySource {
            override fun locator(documentId: String) = documentId
            override fun children(documentId: String) = object : SafListing {
                override val loading = false
                override fun next(): SafRow? = null
                override fun close(): Unit = throw SafAccessException(LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE)
            }
        }
        val result = SafTraversal(source).observe(root, scope, "root", { false }) { }
        assertEquals(IncompleteReason.INACCESSIBLE_BRANCH, (result as TraversalResult.Incomplete).gaps.single().reason)
    }

    private fun dir(id: String) = file(id).copy(mimeType = "vnd.android.document/directory")
    private fun file(id: String) = SafRow(id, id, "text/plain", 12, 34)

    private class Fixture(val tree: Map<String, List<SafRow>>, val loading: Boolean = false) : SafDirectorySource {
        val queries = mutableListOf<String>()
        var closed = 0
        override fun children(documentId: String): SafListing {
            queries.add(documentId)
            val rows = tree[documentId] ?: throw SafAccessException(LocalAccessFailure.NOT_FOUND)
            return object : SafListing {
                private val iterator = rows.iterator()
                override val loading = this@Fixture.loading
                override fun next(): SafRow? = if (iterator.hasNext()) iterator.next() else null
                override fun close() { closed++ }
            }
        }
        override fun locator(documentId: String): String = documentId
    }
}
