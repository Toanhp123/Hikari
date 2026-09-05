package app.openstory.reader.assets

import app.openstory.reader.routing.ReaderSessionId

enum class ReaderViewportDirection {
    FORWARD,
    BACKWARD,
    IDLE,
}

data class ReaderViewportSnapshot(
    val sessionId: ReaderSessionId,
    val manifestRevision: Long,
    val leadingVisibleImageOrdinal: Int?,
    val trailingVisibleImageOrdinal: Int?,
    val direction: ReaderViewportDirection,
    val chapterProgressBasisPoints: Int,
) {
    init {
        require(manifestRevision > 0L) { "Reader viewport manifest revision must be positive." }
        require(chapterProgressBasisPoints in 0..BASIS_POINTS) {
            "Reader viewport progress must be between zero and 10000 basis points."
        }
        require((leadingVisibleImageOrdinal == null) == (trailingVisibleImageOrdinal == null)) {
            "Reader viewport image bounds must be present together."
        }
        if (leadingVisibleImageOrdinal != null && trailingVisibleImageOrdinal != null) {
            require(leadingVisibleImageOrdinal >= 0) { "Reader viewport leading image ordinal must be non-negative." }
            require(trailingVisibleImageOrdinal >= leadingVisibleImageOrdinal) {
                "Reader viewport trailing image ordinal must not precede the leading ordinal."
            }
        }
    }

    fun contains(imageOrdinal: Int): Boolean =
        leadingVisibleImageOrdinal != null &&
            trailingVisibleImageOrdinal != null &&
            imageOrdinal in leadingVisibleImageOrdinal..trailingVisibleImageOrdinal

    private companion object {
        const val BASIS_POINTS = 10_000
    }
}
