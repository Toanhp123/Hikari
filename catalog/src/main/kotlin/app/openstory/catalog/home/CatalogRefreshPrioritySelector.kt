package app.openstory.catalog.home

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.common.id.StoryId

fun interface CatalogRefreshPrioritySelector {
    fun select(committedHomes: List<CatalogHomeSnapshot>): Set<StoryId>

    companion object {
        val ALL = CatalogRefreshPrioritySelector { homes ->
            homes.asSequence()
                .flatMap { home -> home.sections.asSequence() }
                .flatMap { section -> section.items.asSequence() }
                .map { entry -> entry.storyId }
                .toSet()
        }
    }
}
