package app.universalmedia.source.api

import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.ConsumptionTargetRef

/** Ephemeral consumer input. Never persist or place in navigation state. */
sealed interface ResolvedContent

data class ResolvedVideo(val contentUri: String, val mimeType: String) : ResolvedContent {
    override fun toString(): String = "ResolvedVideo(<redacted>, mimeType=$mimeType)"
}

enum class ResolutionFailure {
    NO_SOURCE,
    ACCESS_LOST,
    UNAVAILABLE,
    NOT_FOUND,
    UNSUPPORTED_REPRESENTATION,
    TRANSIENT_PROVIDER_FAILURE,
}

sealed interface SourceResolution {
    /** Provenance for progress compatibility, separate from the runtime content descriptor. */
    data class Ready(
        val target: ConsumptionTargetRef,
        val provenance: VideoResumeContext,
        val content: ResolvedVideo,
    ) : SourceResolution

    data class Failed(val failure: ResolutionFailure) : SourceResolution
}

fun interface SourceResolver {
    suspend fun resolve(target: ConsumptionTargetRef): SourceResolution
}
