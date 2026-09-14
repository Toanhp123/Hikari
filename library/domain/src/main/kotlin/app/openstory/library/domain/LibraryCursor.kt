package app.openstory.library.domain

import app.openstory.common.id.StoryId

data class LibraryCursor(
    val savedAtEpochMs: Long,
    val storyId: String,
) {
    init {
        require(savedAtEpochMs >= 0L)
        StoryId(storyId)
    }
}
