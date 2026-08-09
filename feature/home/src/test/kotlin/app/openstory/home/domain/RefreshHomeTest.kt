package app.openstory.home.domain

import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.source.*
import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.common.FakeClock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshHomeTest {
    @Test
    fun sourceFailureIsReportedWithoutWritingAHomeSnapshot() = runTest {
        val plugin = PluginId("catalog.a")
        val repository = EmptyCatalogRepository()
        val report = RefreshHome(
            app.openstory.catalog.home.CatalogRefreshService(
                SingleSourceRegistry(FailingSource(plugin)), repository, StoryMatcher(), FakeClock(100),
            ), repository,
        )()
        assertEquals(listOf(plugin), report.failed.keys.toList())
        assertEquals(emptyList(), report.succeeded)
    }
}

private class FailingSource(override val pluginId: PluginId) : CatalogSource {
    override val version = "1.0.0"
    override suspend fun home(request: SourceHomeRequest) = CatalogSourceResult.Failure(CatalogSourceFailure("catalog.unavailable", true))
    override suspend fun search(request: SourceSearchRequest) = CatalogSourceResult.Failure(CatalogSourceFailure("unused", false))
    override suspend fun details(sourceId: String) = CatalogSourceResult.Failure(CatalogSourceFailure("unused", false))
    override suspend fun filters() = CatalogSourceResult.Success(emptyList<SourceFilter>())
}

private class SingleSourceRegistry(private val sourceValue: CatalogSource) : CatalogSourceRegistry {
    override suspend fun enabled() = listOf(sourceValue)
    override suspend fun source(pluginId: PluginId) = sourceValue.takeIf { it.pluginId == pluginId }
}

private class EmptyCatalogRepository : CatalogRepository {
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: app.openstory.common.id.StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)
    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.common.id.StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
}
