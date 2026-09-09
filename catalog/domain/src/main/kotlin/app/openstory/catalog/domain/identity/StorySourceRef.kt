package app.openstory.catalog.domain.identity

import app.openstory.common.id.StoryId

data class StorySourceRef(
    val storyId: StoryId,
    val catalogSourceKey: CatalogSourceKey,
    val sourceStoryId: String,
) {
    init {
        require(storyId == SourceStoryIdV1.derive(SourceStoryKey(catalogSourceKey, sourceStoryId)))
    }
}
