package app.universalmedia.source.local

import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalDocumentAccess
import app.universalmedia.core.domain.LocalDocumentAccessResult
import app.universalmedia.core.domain.LocalSourceCatalog
import app.universalmedia.core.domain.LocalSourceLookup
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.RepresentationFamily
import app.universalmedia.source.api.ResolutionFailure
import app.universalmedia.source.api.ResolvedVideo
import app.universalmedia.source.api.SourceResolution
import app.universalmedia.source.api.SourceResolver

class LocalSourceResolver(
    private val catalog: LocalSourceCatalog,
    private val access: LocalDocumentAccess,
) : SourceResolver {
    override suspend fun resolve(target: ConsumptionTargetRef): SourceResolution {
        val context = when (val lookup = catalog.load(target)) {
            LocalSourceLookup.NotFound -> return SourceResolution.Failed(
                ResolutionFailure.NO_SOURCE,
            )

            LocalSourceLookup.UnsupportedRepresentation ->
                return SourceResolution.Failed(ResolutionFailure.UNSUPPORTED_REPRESENTATION)

            is LocalSourceLookup.Found -> lookup.context
        }
        if (context.target != target || context.locator.rootId != context.root.id ||
            context.locator.providerAuthority != context.root.descriptor.providerAuthority
        ) {
            return SourceResolution.Failed(ResolutionFailure.UNAVAILABLE)
        }
        if (context.family != RepresentationFamily.VIDEO) {
            return SourceResolution.Failed(ResolutionFailure.UNSUPPORTED_REPRESENTATION)
        }
        return when (val checked = access.validate(context.root, context.locator)) {
            is LocalDocumentAccessResult.Failed -> SourceResolution.Failed(
                when (checked.failure) {
                    LocalAccessFailure.ACCESS_LOST -> ResolutionFailure.ACCESS_LOST

                    LocalAccessFailure.UNAVAILABLE -> ResolutionFailure.UNAVAILABLE

                    LocalAccessFailure.NOT_FOUND -> ResolutionFailure.NOT_FOUND

                    LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE ->
                        ResolutionFailure.TRANSIENT_PROVIDER_FAILURE
                },
            )

            is LocalDocumentAccessResult.Readable -> {
                val mimeType = checked.mimeType
                if (mimeType != "video/mp4") {
                    SourceResolution.Failed(ResolutionFailure.UNSUPPORTED_REPRESENTATION)
                } else {
                    SourceResolution.Ready(
                        target,
                        VideoResumeContext(
                            context.bindingId,
                            context.assetId,
                            context.assetRevision,
                        ),
                        ResolvedVideo(context.locator.documentLocator, mimeType),
                    )
                }
            }
        }
    }
}
