package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogHomeQueryTest {
    @Test
    fun queryRanksOnlyCachedHomesAndDeduplicatesSectionMembership() = runTest {
        val entry = CatalogEntry(
            StoryId("story"),
            PluginId("p"),
            "s",
            "Title",
            contentType = ContentType.MANGA,
            score = Score(8.0, 10.0),
        )
        val home = CatalogHomeSnapshot(
            PluginId("p"),
            "1",
            1,
            listOf(
                CatalogHomeSection("popular", "Popular", listOf(entry)),
                CatalogHomeSection("seasonal", "Seasonal", listOf(entry)),
            ),
        )

        val ranked = CatalogHomeQuery(FakeRepository(listOf(home)))
            .rankedStories
            .first()
            .single()

        assertEquals(StoryId("story"), ranked.storyId)
        assertEquals(1, ranked.contributions.size)
    }

    private class FakeRepository(homes: List<CatalogHomeSnapshot>) : CatalogRepository {
        private val state = MutableStateFlow(homes)

        override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = state

        override fun observeStory(storyId: StoryId) =
            MutableStateFlow<StoryCatalogSnapshot?>(null)

        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())

        override suspend fun commitHomeRefresh(
            mutation: CatalogHomeMutation,
        ): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)

        override suspend fun commitDetails(
            mutation: CatalogDetailsMutation,
        ): Outcome<StoryId, CatalogStoreFailure> = Outcome.Success(mutation.storyId)
    }
}
