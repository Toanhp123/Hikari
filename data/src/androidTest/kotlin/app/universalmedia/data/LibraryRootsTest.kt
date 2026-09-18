package app.universalmedia.data

import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.ScanCoverage
import app.universalmedia.core.domain.ScanFinalization
import app.universalmedia.core.domain.ScanOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRootsTest {
    @Test
    fun projectionUsesCurrentGenerationAndRestoresFinalizedScanAfterReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-roots-test.db"
        context.deleteDatabase(name)
        var database = UniversalMediaDatabase.open(context, name)
        try {
            var store = RoomMediaStore(database)
            val evidence =
                RootRegistrationEvidence(LocalRootDescriptor("provider", "tree"), true, 1)
            val root = store.registerOrReauthorize(evidence)
            assertNull(database.catalog().libraryRoots().first().single().runId)
            val first = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 100)
            assertEquals(
                first.id.value.toString(),
                database.catalog().libraryRoots().first().single().runId,
            )
            store.finalizeRun(
                first.id,
                ScanFinalization(ScanOutcome.FAILED, ScanCoverage.Unknown, 101),
            )
            // Clock rollback must not make an older finalized attempt shadow the active attempt.
            val second = store.beginRun(DeclaredScanScope(root.id, root.configGeneration), 50)
            assertEquals(
                second.id.value.toString(),
                database.catalog().libraryRoots().first().single().runId,
            )
            store.finalizeRun(
                second.id,
                ScanFinalization(ScanOutcome.COMPLETE, ScanCoverage.Complete, 51),
            )
            database.close()
            database = UniversalMediaDatabase.open(context, name)
            store = RoomMediaStore(database)
            assertEquals("COMPLETE", database.catalog().libraryRoots().first().single().outcome)
            store.registerOrReauthorize(evidence.copy(observedAtEpochMs = 200))
            assertNull(database.catalog().libraryRoots().first().single().runId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
