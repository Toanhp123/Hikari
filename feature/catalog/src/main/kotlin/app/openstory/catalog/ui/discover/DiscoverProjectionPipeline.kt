package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.common.dispatchers.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DiscoverProjectionPipeline @Inject constructor(
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun project(
        homes: List<CatalogHomeSnapshot>,
        projections: List<CatalogStoryProjection>,
        selectedContentType: ContentType,
    ): DiscoverSemanticContent = withContext(dispatcher) {
        projectSemanticDiscoverContent(
            homes = homes,
            projections = projections,
            selectedContentType = selectedContentType,
        )
    }
}
