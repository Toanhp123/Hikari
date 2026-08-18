package app.openstory.catalog.details

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogDetailsServiceTest {
    @Test
    fun sourceIdMismatchFailsWithoutPersistence() = runTest {
        val repository = FakeRepository()
        val source = Source("a", details("different"))

        val result = service(source, repository).load(PluginId("a"), "requested")

        assertEquals(
            CatalogDetailsFailure.SourceIdMismatch("requested", "different"),
            (result as CatalogDetailsResult.Failure).failure,
        )
        assertEquals(0, repository.detailCommits)
    }

    @Test
    fun enrichmentPreservesHomeMembershipAndUsesDurableStoryId() = runTest {
        val repository = FakeRepository(durableStoryId = StoryId("story:durable"))
        val source = Source("a", details("source"))
        val homesBefore = repository.homes.value

        val result = service(source, repository).load(PluginId("a"), "source")

        assertEquals(1, repository.detailCommits)
        assertEquals(homesBefore, repository.homes.value)
        assertEquals(
            StoryId("story:durable"),
            (result as CatalogDetailsResult.Success).story.id,
        )
        assertEquals(StoryId("story:durable"), result.entry.storyId)
    }

    @Test
    fun storeFailureBecomesTypedDetailsFailure() = runTest {
        val repository = FakeRepository(
            storeFailure = CatalogStoreFailure("store.down", retryable = true),
        )
        val source = Source("a", details("source"))

        val result = service(source, repository).load(PluginId("a"), "source")

        assertEquals(
            CatalogDetailsFailure.StoreFailure("store.down", retryable = true),
            (result as CatalogDetailsResult.Failure).failure,
        )
        assertEquals(1, repository.detailCommits)
    }

    @Test
    fun unexpectedSourceExceptionBecomesTypedFailure() = runTest {
        val repository = FakeRepository()

        val result = service(ThrowingSource(), repository)
            .load(PluginId("a"), "source")

        assertEquals(
            CatalogDetailsFailure.SourceFailure(
                "catalog.source.exception",
                retryable = true,
            ),
            (result as CatalogDetailsResult.Failure).failure,
        )
        assertEquals(0, repository.detailCommits)
    }

    private fun service(source: CatalogSource, repository: CatalogRepository) =
        CatalogDetailsService(Registry(source), repository, StoryMatcher(), Clock { 1 })

    private fun details(sourceId: String) = SourceDetails(
        sourceId = sourceId,
        sourceUrl = "url",
        title = "Title",
        aliases = emptySet(),
        authors = setOf("Author"),
        description = "Description",
        genres = emptySet(),
        contentType = SourceContentType.MANGA,
        languageTags = setOf("en"),
        coverUrl = null,
        scoreValue = null,
        scoreScale = null,
        popularityRank = 4,
    )

    private class Registry(
        private val source: CatalogSource,
    ) : CatalogSourceRegistry {
        override suspend fun enabled() = listOf(source)
        override suspend fun source(pluginId: PluginId) =
            source.takeIf { it.pluginId == pluginId }
    }

    private open class Source(
        id: String,
        private val detailsValue: SourceDetails,
    ) : CatalogSource {
        override val pluginId = PluginId(id)
        override val version = "1"
        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
            error("unused")
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
            error("unused")
        override suspend fun details(
            sourceId: String,
        ): CatalogSourceResult<SourceDetails> = CatalogSourceResult.Success(detailsValue)
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = error("unused")
    }

    private class ThrowingSource : Source("a", detailsValue()) {
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> =
            throw IllegalStateException("boom")

        companion object {
            private fun detailsValue() = SourceDetails(
                "source",
                null,
                "Title",
                emptySet(),
                emptySet(),
                null,
                emptySet(),
                SourceContentType.MANGA,
                emptySet(),
                null,
                null,
                null,
                null,
            )
        }
    }

    private class FakeRepository(
        private val durableStoryId: StoryId? = null,
        private val storeFailure: CatalogStoreFailure? = null,
    ) : CatalogRepository {
        var detailCommits = 0
        val homes = MutableStateFlow<List<CatalogHomeSnapshot>>(emptyList())

        override fun observeHomes() = homes
        override fun observeStory(storyId: StoryId) = emptyFlow<StoryCatalogSnapshot?>()
        override suspend fun matchSnapshot() = CatalogMatchSnapshot(emptyList())
        override suspend fun commitHomeRefresh(
            mutation: CatalogHomeMutation,
        ): Outcome<Unit, CatalogStoreFailure> = Outcome.Success(Unit)

        override suspend fun commitDetails(
            mutation: CatalogDetailsMutation,
        ): Outcome<StoryId, CatalogStoreFailure> {
            detailCommits++
            return storeFailure?.let { Outcome.Failure(it) }
                ?: Outcome.Success(durableStoryId ?: mutation.storyId)
        }
    }
}
