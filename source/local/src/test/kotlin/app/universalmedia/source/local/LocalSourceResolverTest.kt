package app.universalmedia.source.local

import app.universalmedia.core.domain.AssetRevision
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalDocumentAccess
import app.universalmedia.core.domain.LocalDocumentAccessResult
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.LocalSourceCatalog
import app.universalmedia.core.domain.LocalSourceContext
import app.universalmedia.core.domain.LocalSourceLookup
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.RepresentationFamily
import app.universalmedia.core.model.RootId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.core.model.SourceId
import app.universalmedia.source.api.ResolutionFailure
import app.universalmedia.source.api.ResolvedVideo
import app.universalmedia.source.api.SourceResolution
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSourceResolverTest {
    private val target = ConsumptionTargetRef.MediaTarget(MediaId.generate())
    private val root = StorageRoot(
        RootId.generate(),
        LocalRootDescriptor("fixture", "content://fixture/tree/root"),
        1,
        LocalAccessState.READABLE,
    )
    private val context = LocalSourceContext(
        target,
        SourceId.generate(),
        SourceBindingId.generate(),
        AssetId.generate(),
        AssetRevision(3),
        RepresentationFamily.VIDEO,
        root,
        LocalDocumentLocator(root.id, "fixture", "content://fixture/tree/root/document/video"),
    )
    private var lookup: LocalSourceLookup = LocalSourceLookup.Found(context)
    private var access: LocalDocumentAccessResult = LocalDocumentAccessResult.Readable("video/mp4")
    private var calls = 0
    private val catalog = object : LocalSourceCatalog {
        override suspend fun load(target: ConsumptionTargetRef): LocalSourceLookup {
            assertEquals(this@LocalSourceResolverTest.target, target)
            return lookup
        }
    }
    private fun resolver() = LocalSourceResolver(
        catalog,
        LocalDocumentAccess {
                actualRoot,
                locator,
            ->
            assertEquals(root.id, actualRoot.id)
            assertEquals(context.locator, locator)
            calls++
            access
        },
    )

    @Test fun resolvesCurrentRevisionAndChecksAccessOnEveryRequest() = runBlocking {
        val first = resolver().resolve(target) as SourceResolution.Ready
        assertEquals(ResolvedVideo(context.locator.documentLocator, "video/mp4"), first.content)
        assertEquals(
            VideoResumeContext(context.bindingId, context.assetId, context.assetRevision),
            first.provenance,
        )
        lookup = LocalSourceLookup.Found(context.copy(assetRevision = AssetRevision(4)))
        val second = resolver().resolve(target) as SourceResolution.Ready
        assertEquals(AssetRevision(4), second.provenance.assetRevision)
        assertEquals(2, calls)
        assertEquals(target, first.target)
    }

    @Test fun noBindingDoesNotOpenStorage() = runBlocking {
        lookup = LocalSourceLookup.NotFound
        assertEquals(
            SourceResolution.Failed(ResolutionFailure.NO_SOURCE),
            resolver().resolve(target),
        )
        assertEquals(0, calls)
    }

    @Test fun accessFailuresRemainTyped() = runBlocking {
        val failures = mapOf(
            LocalAccessFailure.ACCESS_LOST to ResolutionFailure.ACCESS_LOST,
            LocalAccessFailure.NOT_FOUND to ResolutionFailure.NOT_FOUND,
            LocalAccessFailure.UNAVAILABLE to ResolutionFailure.UNAVAILABLE,
            LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE to
                ResolutionFailure.TRANSIENT_PROVIDER_FAILURE,
        )
        failures.forEach { (local, expected) ->
            access = LocalDocumentAccessResult.Failed(local)
            assertEquals(SourceResolution.Failed(expected), resolver().resolve(target))
        }
    }

    @Test fun rejectsUnsupportedCatalogAndChangedMime() = runBlocking {
        lookup = LocalSourceLookup.UnsupportedRepresentation
        assertEquals(
            SourceResolution.Failed(ResolutionFailure.UNSUPPORTED_REPRESENTATION),
            resolver().resolve(target),
        )
        assertEquals(0, calls)
        lookup = LocalSourceLookup.Found(context)
        access = LocalDocumentAccessResult.Readable("application/pdf")
        assertEquals(
            SourceResolution.Failed(ResolutionFailure.UNSUPPORTED_REPRESENTATION),
            resolver().resolve(target),
        )
    }

    @Test fun oldRootAvailabilityDoesNotReplaceCurrentAccessCheck() = runBlocking {
        lookup =
            LocalSourceLookup.Found(
                context.copy(root = root.copy(access = LocalAccessState.ACCESS_LOST)),
            )
        assertTrue(resolver().resolve(target) is SourceResolution.Ready)
        assertEquals(1, calls)
    }

    @Test fun rejectsMismatchedTargetWithoutOpeningStorage() = runBlocking {
        lookup =
            LocalSourceLookup.Found(
                context.copy(target = ConsumptionTargetRef.MediaTarget(MediaId.generate())),
            )
        assertEquals(
            SourceResolution.Failed(ResolutionFailure.UNAVAILABLE),
            resolver().resolve(target),
        )
        assertEquals(0, calls)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotUnavailable() = runBlocking<Unit> {
        LocalSourceResolver(
            catalog,
            LocalDocumentAccess { _, _ ->
                throw CancellationException()
            },
        ).resolve(target)
    }
}
