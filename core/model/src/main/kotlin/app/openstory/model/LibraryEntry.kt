package app.openstory.model

data class LibraryEntry(
    val storyId: StoryId,
    val status: LibraryStatus,
    val addedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
