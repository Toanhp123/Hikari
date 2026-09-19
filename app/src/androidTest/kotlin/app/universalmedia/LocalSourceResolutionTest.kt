package app.universalmedia

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalDocumentAccess
import app.universalmedia.core.domain.LocalDocumentAccessResult
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.model.ConsumptionTargetRef.MediaTarget
import app.universalmedia.core.model.MediaId
import app.universalmedia.data.RoomMediaStore
import app.universalmedia.data.UniversalMediaDatabase
import app.universalmedia.source.api.ResolutionFailure
import app.universalmedia.source.api.SourceResolution
import app.universalmedia.source.local.LocalSourceResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSourceResolutionTest {
    @Test
    fun freshGraphResolvesFromReopenedDurableCatalogAndRechecksAccess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "source-resolution-test.db"
        context.deleteDatabase(name)
        var database = UniversalMediaDatabase.open(context, name)
        try {
            val firstStore = RoomMediaStore(database)
            val root = firstStore.registerOrReauthorize(
                RootRegistrationEvidence(
                    LocalRootDescriptor("fixture", "content://fixture/tree/root"),
                    true,
                    1,
                ),
            )
            val locator =
                LocalDocumentLocator(
                    root.id,
                    "fixture",
                    "content://fixture/tree/root/document/video",
                )
            val run = firstStore.beginRun(DeclaredScanScope(root.id, root.configGeneration), 2)
            val materialized = firstStore.commitRecognizedLocalVideo(
                run.id,
                LocalDocumentObservation(locator, "video.mp4", "video/mp4", 100, 1, 3),
            )
            val target = MediaTarget(materialized.mediaId)
            val first = LocalSourceResolver(
                firstStore.sources,
                LocalDocumentAccess { _, _ ->
                    LocalDocumentAccessResult.Readable("video/mp4")
                },
            ).resolve(target) as SourceResolution.Ready
            database.close()
            database = UniversalMediaDatabase.open(context, name)
            val secondStore = RoomMediaStore(database)
            var checks = 0
            val secondResolver =
                LocalSourceResolver(
                    secondStore.sources,
                    LocalDocumentAccess {
                            restoredRoot,
                            restoredLocator,
                        ->
                        assertEquals(root, restoredRoot)
                        assertEquals(locator, restoredLocator)
                        checks++
                        LocalDocumentAccessResult.Readable("video/mp4")
                    },
                )
            assertEquals(first, secondResolver.resolve(target))
            assertEquals(1, checks)
            assertEquals(materialized.assetRevision, first.provenance.assetRevision)
            val revoked = LocalSourceResolver(
                secondStore.sources,
                LocalDocumentAccess { _, _ ->
                    LocalDocumentAccessResult.Failed(LocalAccessFailure.ACCESS_LOST)
                },
            )
            assertEquals(
                SourceResolution.Failed(ResolutionFailure.ACCESS_LOST),
                revoked.resolve(target),
            )
            assertEquals(first, secondResolver.resolve(target))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun applicationComposesResolverWithoutLibraryLocatorInput() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<UniversalMediaApplication>()
        assertEquals(
            SourceResolution.Failed(ResolutionFailure.NO_SOURCE),
            application.sources.resolve(MediaTarget(MediaId.generate())),
        )
    }
}
