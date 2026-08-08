package app.openstory.plugin.api.catalog

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.plugin.api.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CatalogContractTest {

    @Test
    fun catalogCardCarriesExplicitContentType() {
        val card = catalogCard(sourceId = "story-1")

        assertEquals(ContentType.WEB_NOVEL, card.contentType)
    }

    @Test
    fun catalogPageRejectsDuplicateSourceIds() {
        val item = catalogCard(sourceId = "same")

        assertFailsWith<IllegalArgumentException> {
            Page(
                items = listOf(item, item),
                nextToken = null,
            )
        }
    }

    @Test
    fun catalogPageRejectsBlankSourceId() {
        assertFailsWith<IllegalArgumentException> {
            Page(
                items = listOf(catalogCard(sourceId = "   ")),
                nextToken = null,
            )
        }
    }

    @Test
    fun catalogPageRejectsMoreThanMaximumItems() {
        val items = List(101) { index ->
            catalogCard(sourceId = "source-$index")
        }

        assertFailsWith<IllegalArgumentException> {
            Page(
                items = items,
                nextToken = "cursor::opaque",
            )
        }
    }

    @Test
    fun catalogPageRejectsBlankContinuationToken() {
        assertFailsWith<IllegalArgumentException> {
            Page(
                items = listOf(catalogCard(sourceId = "story-1")),
                nextToken = "   ",
            )
        }
    }

    @Test
    fun catalogScoreRejectsInvalidValueOrScale() {
        assertFailsWith<IllegalArgumentException> {
            CatalogScore(
                value = 5.0,
                scale = 0.0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            CatalogScore(
                value = -1.0,
                scale = 10.0,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            CatalogScore(
                value = 11.0,
                scale = 10.0,
            )
        }
    }


    @Test
    fun catalogPluginExposesHostOwnedOperations() {
        val plugin: CatalogPlugin = object : CatalogPlugin {
            override suspend fun home(
                request: CatalogHomeRequest,
            ): AppResult<List<CatalogSection>> = TODO("Contract-only fixture")

            override suspend fun search(
                request: CatalogSearchRequest,
            ): AppResult<Page<CatalogCard>> = TODO("Contract-only fixture")

            override suspend fun details(
                sourceId: String,
            ): AppResult<CatalogDetails> = TODO("Contract-only fixture")

            override suspend fun filters(): AppResult<List<CatalogFilterDefinition>> =
                TODO("Contract-only fixture")
        }

        assertNotNull(plugin)
    }

    @Test
    fun catalogFiltersAreDeclarativeWireDefinitions() {
        val option = CatalogFilterOption(
            value = "fantasy",
            label = "Fantasy",
        )

        val definitions: List<CatalogFilterDefinition> = listOf(
            CatalogSelectFilter(
                id = "genre",
                label = "Genre",
                options = listOf(option),
            ),
            CatalogMultiSelectFilter(
                id = "tags",
                label = "Tags",
                options = listOf(option),
            ),
            CatalogRangeFilter(
                id = "rating",
                label = "Rating",
                minimum = 0.0,
                maximum = 10.0,
                step = 0.5,
            ),
            CatalogTextFilter(
                id = "author",
                label = "Author",
                placeholder = "Author name",
            ),
            CatalogSortFilter(
                id = "sort",
                label = "Sort",
                options = listOf(
                    CatalogFilterOption(
                        value = "popular",
                        label = "Popular",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("genre", "tags", "rating", "author", "sort"),
            definitions.map { it.id },
        )
    }
    private fun catalogCard(
        sourceId: String,
    ): CatalogCard = CatalogCard(
        sourceId = sourceId,
        title = "Title",
        contentType = ContentType.WEB_NOVEL,
        authors = emptyList(),
        image = null,
        score = null,
    )
}
