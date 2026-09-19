package app.universalmedia.storage.local

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalDocumentAccessResult
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.TraversalResult
import app.universalmedia.core.model.RootId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafLocalStorageTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    private val storage = SafLocalStorage(resolver)
    private val tree = DocumentsContract.buildTreeDocumentUri(
        FixtureDocumentsProvider.AUTHORITY,
        "root",
    )
    private val root = StorageRoot(
        RootId.generate(),
        LocalRootDescriptor(FixtureDocumentsProvider.AUTHORITY, tree.toString()),
        1,
        LocalAccessState.READABLE,
    )
    private val scope = DeclaredScanScope(root.id, 1)

    @Before
    fun resetProvider() = FixtureDocumentsProvider.reset()

    @Test
    fun directTraversalVisitsEachDirectoryOnceAndKeepsIdentitySeparate() = runBlocking {
        val seen = mutableListOf<LocalDocumentObservation>()
        assertEquals(
            TraversalResult.Complete,
            storage.observe(root, scope, {
                false
            }) { seen.addAll(it) },
        )
        assertEquals(listOf("root", "dir"), FixtureDocumentsProvider.queries)
        assertEquals(listOf("video/mp4", "text/plain"), seen.map { it.mimeType })
        assertTrue(seen.all { it.locator.rootId == root.id })
        assertFalse(root.id.toString().contains(tree.toString()))
        assertFalse(root.descriptor.toString().contains(FixtureDocumentsProvider.AUTHORITY))
        assertFalse(seen.first().locator.toString().contains(FixtureDocumentsProvider.AUTHORITY))
    }

    @Test
    fun loadingAndFailedBranchesKeepPositiveFactsWithoutCompletion() = runBlocking {
        FixtureDocumentsProvider.loading = true
        FixtureDocumentsProvider.brokenBranch = true
        val seen = mutableListOf<LocalDocumentObservation>()
        val result = storage.observe(root, scope, {
            false
        }) { seen.addAll(it) } as TraversalResult.Incomplete
        assertEquals(2, seen.size)
        assertTrue(result.gaps.any { it.reason == IncompleteReason.PROVIDER_LOADING })
        assertTrue(result.gaps.any { it.reason == IncompleteReason.INACCESSIBLE_BRANCH })
    }

    @Test
    fun cancelledScanNeverQueriesOrCompletes() = runBlocking {
        assertEquals(
            TraversalResult.Cancelled,
            storage.observe(root, scope, {
                true
            }) { error("Unexpected batch") },
        )
        assertTrue(FixtureDocumentsProvider.queries.isEmpty())
    }

    @Test
    fun nullableRequiredColumnIsIncomplete() = runBlocking {
        FixtureDocumentsProvider.malformed = true
        val result = storage.observe(root, scope, { false }) { } as TraversalResult.Incomplete
        assertTrue(result.gaps.any { it.reason == IncompleteReason.INVALID_PROVIDER_DATA })
    }

    @Test
    fun loadingRootIsIncompleteAndDoesNotTraverse() = runBlocking {
        FixtureDocumentsProvider.rootLoading = true
        val result = storage.observe(root, scope, {
            false
        }) { error("Unexpected batch") } as TraversalResult.Incomplete
        assertEquals(IncompleteReason.PROVIDER_LOADING, result.gaps.single().reason)
        assertTrue(FixtureDocumentsProvider.queries.isEmpty())
    }

    @Test
    fun currentDocumentIsOpenedReadOnlyAndWrongRootIsRejected() = runBlocking {
        val locator = locator("video")
        val result = storage.inspect(root, locator) as SafDocumentResult.Readable
        assertEquals("video/mp4", result.observation.mimeType)
        assertEquals(3L, result.observation.sizeBytes)
        assertEquals(
            SafDocumentResult.Failed(LocalAccessFailure.UNAVAILABLE),
            storage.inspect(root, locator.copy(rootId = RootId.generate())),
        )
        // DocumentsProvider.query catches FileNotFoundException and returns null, losing the reason.
        resolver.query(
            Uri.parse(locator("missing").documentLocator),
            null,
            null,
            null,
            null,
        ).use { cursor ->
            assertNull(cursor)
        }
        assertEquals(
            SafDocumentResult.Failed(LocalAccessFailure.UNAVAILABLE),
            storage.inspect(root, locator("missing")),
        )
        assertEquals(
            SafDocumentResult.Failed(LocalAccessFailure.NOT_FOUND),
            storage.inspect(root, locator("empty")),
        )
        assertEquals(
            SafDocumentResult.Failed(LocalAccessFailure.NOT_FOUND),
            storage.inspect(root, locator("missing-on-open")),
        )
        assertEquals(
            SafDocumentResult.Failed(LocalAccessFailure.ACCESS_LOST),
            storage.inspect(root, locator("denied")),
        )
    }

    @Test
    fun sourceAccessPortPreservesCurrentOpenabilityAndTypedFailures() = runBlocking {
        assertEquals(
            LocalDocumentAccessResult.Readable("video/mp4"),
            storage.validate(root, locator("video")),
        )
        assertEquals(
            LocalDocumentAccessResult.Failed(LocalAccessFailure.ACCESS_LOST),
            storage.validate(root, locator("denied")),
        )
        assertEquals(
            LocalDocumentAccessResult.Failed(LocalAccessFailure.NOT_FOUND),
            storage.validate(root, locator("missing-on-open")),
        )
        assertEquals(
            LocalDocumentAccessResult.Failed(LocalAccessFailure.UNAVAILABLE),
            storage.validate(root, locator("missing")),
        )
    }

    @Test
    fun registrationRejectsInvalidUriAndUnpersistedGrant() = runBlocking {
        assertEquals(
            SafRegistrationResult.Failed(LocalAccessFailure.UNAVAILABLE),
            storage.register(Uri.parse("file:///root")),
        )
        assertEquals(
            SafRegistrationResult.Failed(LocalAccessFailure.ACCESS_LOST),
            storage.register(tree),
        )
    }

    @Test
    fun registrationPersistsReadGrantAndValidatesDirectory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.grantUriPermission(
            context.packageName,
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        try {
            val result = storage.register(tree) as SafRegistrationResult.Registered
            assertTrue(result.evidence.persistedReadAccess)
            assertEquals(root.descriptor, result.evidence.descriptor)
            assertTrue(
                resolver.persistedUriPermissions.any {
                    it.uri == tree && it.isReadPermission
                },
            )
        } finally {
            if (resolver.persistedUriPermissions.any { it.uri == tree }) {
                resolver.releasePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            context.revokeUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun locator(id: String) = LocalDocumentLocator(
        root.id,
        FixtureDocumentsProvider.AUTHORITY,
        DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
    )
}
