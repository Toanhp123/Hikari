package app.openstory.library

import app.openstory.common.id.StoryId

fun interface LibraryMappingScheduler {
    fun schedule(storyId: StoryId)
}
