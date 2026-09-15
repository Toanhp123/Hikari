package app.openstory.artwork

enum class ArtworkFailureReason {
    INVALID_LOCATOR,
    POLICY_REJECTED,
    REDIRECT_REJECTED,
    MEDIA_TYPE_REJECTED,
    ENCODED_TOO_LARGE,
    DIMENSIONS_TOO_LARGE,
    DECODE_FAILED,
    TIMEOUT,
    SATURATED,
    IO_FAILED,
}

class ArtworkFailureException(
    val reason: ArtworkFailureReason,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)

internal fun artworkFailure(reason: ArtworkFailureReason): Nothing = throw ArtworkFailureException(reason)
