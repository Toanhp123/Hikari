package app.openstory.plugin.api.content

import kotlinx.serialization.Serializable

@Serializable
data class ChapterSyncDelta(
    val upserts: List<SourceChapterRelease>,
    val tombstoneSourceReleaseIds: Set<String>,
    val nextCursor: String?,
) {
    init {
        require(tombstoneSourceReleaseIds.all { it.isNotBlank() }) {
            "Tombstone source release IDs must not be blank."
        }
    }
}
