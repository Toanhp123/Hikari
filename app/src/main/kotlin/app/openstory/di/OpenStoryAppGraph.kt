package app.openstory.di

import app.openstory.catalog.details.CatalogDetailsService
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.search.CatalogSearchService
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.common.SystemClock
import app.openstory.home.domain.ObserveCombinedHome
import app.openstory.home.domain.RefreshHome
import app.openstory.home.domain.SearchCatalogs
import app.openstory.home.ui.HomeViewModel
import app.openstory.home.ui.SearchViewModel
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomCatalogRepository
import app.openstory.story.ui.StoryDetailRequest
import app.openstory.story.ui.StoryDetailViewModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenStoryAppGraph @Inject constructor(
    database: OpenStoryDatabase,
    private val catalogSources: CatalogSourceRegistry,
) {
    private val matcher = StoryMatcher()
    private val catalogRepository: CatalogRepository = RoomCatalogRepository(database)
    private val refreshService = CatalogRefreshService(catalogSources, catalogRepository, matcher, SystemClock)
    private val searchService = CatalogSearchService(catalogSources, catalogRepository, matcher)
    private val detailsService = CatalogDetailsService(catalogSources, catalogRepository, matcher, SystemClock)
    private val observeCombinedHome = ObserveCombinedHome(
        repository = catalogRepository,
        ranking = AggregateRanking(),
        enabledCatalogIds = { catalogSources.enabled().mapTo(mutableSetOf()) { it.pluginId } },
    )
    private val refreshHome = RefreshHome(refreshService, catalogRepository)
    private val searchCatalogs = SearchCatalogs(searchService, catalogSources)

    fun createHomeViewModel() = HomeViewModel(observeCombinedHome, refreshHome)
    fun createSearchViewModel() = SearchViewModel(searchCatalogs)
    fun createStoryDetailViewModel(request: StoryDetailRequest) = StoryDetailViewModel(
        request = request,
        catalogRepository = catalogRepository,
        detailsService = detailsService,
    )
}
