package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId

sealed interface ReaderLocalCacheFact {
    data object Unknown : ReaderLocalCacheFact
    data object Miss : ReaderLocalCacheFact

    data class Exact(val fingerprint: String) : ReaderLocalCacheFact {
        init { require(fingerprint.isNotBlank()) { "Reader cache fingerprint must not be blank." } }
    }

    data class Unverified(val fingerprint: String) : ReaderLocalCacheFact {
        init { require(fingerprint.isNotBlank()) { "Reader cache fingerprint must not be blank." } }
    }
}

fun interface ReaderCacheFactsPort {
    suspend fun inspect(
        releaseIds: Set<ChapterReleaseId>,
        resumeFingerprints: Map<ChapterReleaseId, String>,
    ): Map<ChapterReleaseId, ReaderLocalCacheFact>
}
