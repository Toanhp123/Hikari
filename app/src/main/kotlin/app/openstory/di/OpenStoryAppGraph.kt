package app.openstory.di

import android.content.Context
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.database.OpenStoryDatabase
import app.openstory.database.repository.CatalogRepository
import app.openstory.database.repository.LocalStoryRepository
import app.openstory.database.repository.RoomCatalogRepository
import app.openstory.database.repository.RoomStoryRepository
import app.openstory.home.domain.CatalogSnapshotMapper
import app.openstory.home.domain.ObserveCombinedHome
import app.openstory.home.domain.RefreshHome
import app.openstory.home.domain.SearchCatalogs
import app.openstory.home.ui.HomeViewModel
import app.openstory.home.ui.SearchViewModel
import app.openstory.matching.AggregateRanking
import app.openstory.matching.CatalogStoryResolver
import app.openstory.matching.defaultCatalogMatchPolicy
import app.openstory.plugin.host.PluginHost
import app.openstory.story.domain.CatalogDetailsMapper
import app.openstory.story.ui.StoryDetailRequest
import app.openstory.story.ui.StoryDetailViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenStoryAppGraph @Inject constructor(
    @ApplicationContext context: Context,
    dispatchers: AppDispatchers,
    private val pluginHost: PluginHost,
) {
    private val database = OpenStoryDatabase.open(context)
    private val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
    private val catalogRepository: CatalogRepository = RoomCatalogRepository(
        database = database,
        resolver = resolver,
    )
    private val storyRepository: LocalStoryRepository = RoomStoryRepository(database)
    private val observeCombinedHome = ObserveCombinedHome(
        repository = catalogRepository,
        ranking = AggregateRanking(),
        enabledCatalogIds = {
            pluginHost.enabledCatalogs().mapTo(mutableSetOf()) { hosted -> hosted.id }
        },
    )
    private val refreshHome = RefreshHome(
        host = pluginHost,
        mapper = CatalogSnapshotMapper(),
        repository = catalogRepository,
        dispatchers = dispatchers,
    )
    private val searchCatalogs = SearchCatalogs(
        host = pluginHost,
        resolver = resolver,
        repository = catalogRepository,
    )
    private val detailsMapper = CatalogDetailsMapper()

    fun createHomeViewModel(): HomeViewModel = HomeViewModel(
        observeHome = observeCombinedHome,
        refreshHome = refreshHome,
    )

    fun createSearchViewModel(): SearchViewModel = SearchViewModel(searchCatalogs)

    fun createStoryDetailViewModel(
        request: StoryDetailRequest,
    ): StoryDetailViewModel = StoryDetailViewModel(
        request = request,
        storyRepository = storyRepository,
        catalogRepository = catalogRepository,
        host = pluginHost,
        detailsMapper = detailsMapper,
    )
}
