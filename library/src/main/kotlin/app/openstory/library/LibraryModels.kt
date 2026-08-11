package app.openstory.library

import app.openstory.common.id.StoryId

enum class LibraryStatus {
    WANT_TO_READ,
    READING,
    PAUSED,
    COMPLETED,
    DROPPED,
}

data class LibraryEntry(
    val storyId: StoryId,
    val status: LibraryStatus,
    val addedAt: Long,
    val updatedAt: Long,
)
