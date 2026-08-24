package app.openstory.chapters.sync

fun interface ChapterSyncCandidateSource {
    suspend fun eligibleCandidates(): List<ChapterSyncCandidate>
}
