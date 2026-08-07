package app.openstory.plugin.api.catalog

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CatalogWireInvariantTest {
    @Test
    fun sectionRequiresStableIdentityAndUniqueItems() {
        assertFailsWith<IllegalArgumentException> {
            CatalogSection(
                sourceId = " ",
                title = "Section",
                items = emptyList(),
            )
        }
        val item = card("same")
        assertFailsWith<IllegalArgumentException> {
            CatalogSection(
                sourceId = "featured",
                title = "Featured",
                items = listOf(item, item),
            )
        }
    }

    @Test
    fun stableExternalIdsRejectEmbeddedWhitespace() {
        assertFailsWith<IllegalArgumentException> {
            CatalogSection(
                sourceId = "featured section",
                title = "Featured",
                items = emptyList(),
            )
        }
    }

    @Test
    fun imageRequiresHttpsUrlAndMatchingDeclaredHost() {
        assertFailsWith<IllegalArgumentException> {
            CatalogImageReference(
                url = "http://images.example/cover.webp",
                declaredHost = "images.example",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogImageReference(
                url = "https://cdn.example/cover.webp",
                declaredHost = "images.example",
            )
        }
    }

    @Test
    fun selectFilterRequiresUniqueNonBlankOptions() {
        assertFailsWith<IllegalArgumentException> {
            CatalogSelectFilter(
                id = "genre",
                label = "Genre",
                options = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogSelectFilter(
                id = "genre",
                label = "Genre",
                options = listOf(
                    CatalogFilterOption("fantasy", "Fantasy"),
                    CatalogFilterOption("fantasy", "Duplicate"),
                ),
            )
        }
    }

    @Test
    fun rangeFilterRequiresFiniteAscendingRangeAndPositiveStep() {
        assertFailsWith<IllegalArgumentException> {
            CatalogRangeFilter(
                id = "score",
                label = "Score",
                minimum = 10.0,
                maximum = 1.0,
                step = 1.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogRangeFilter(
                id = "score",
                label = "Score",
                minimum = 0.0,
                maximum = 10.0,
                step = 0.0,
            )
        }
    }

    private fun card(sourceId: String) = CatalogCard(
        sourceId = sourceId,
        title = "Story",
        authors = emptyList(),
        image = null,
        score = null,
    )
}
