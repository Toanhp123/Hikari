package app.openstory.plugin.api.content

import app.openstory.plugin.api.requireStableId
import kotlinx.serialization.Serializable

@Serializable
data class ChapterSyncDelta(
    val upserts: List<SourceChapterRelease>,
    val tombstoneSourceReleaseIds: Set<String>,
    val nextCursor: String?,
) {
    init {
        tombstoneSourceReleaseIds.forEach { sourceReleaseId ->
            requireStableId(sourceReleaseId, "Tombstone source release ID")
        }
        val upsertIds = upserts.map(SourceChapterRelease::sourceReleaseId)
        require(upsertIds.distinct().size == upsertIds.size) {
            "Chapter sync upsert source release IDs must be unique."
        }
        require(upsertIds.none(tombstoneSourceReleaseIds::contains)) {
            "Chapter sync cannot upsert and tombstone the same source release."
        }
        require(nextCursor == null || nextCursor.isNotBlank()) {
            "Chapter sync cursor must be null or non-blank."
        }
    }
}
