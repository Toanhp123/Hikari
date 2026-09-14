package app.openstory.library.feature

import app.openstory.catalog.domain.identity.StorySourceRef

object HomeTestTags {
    const val ROOT = "app-home"
    const val SEARCH = "home-library-search"
    const val EXPLORE_MANGA = "app-home-explore-manga"
    const val EXPLORE_LIGHT_NOVELS = "app-home-explore-light-novels"

    fun story(ref: StorySourceRef): String = "home-library-story:${ref.storyId.value}"
}
