package app.openstory.catalog.model

import app.openstory.common.id.StoryId

data class Story(
    val id: StoryId,
    val contentType: ContentType,
)
