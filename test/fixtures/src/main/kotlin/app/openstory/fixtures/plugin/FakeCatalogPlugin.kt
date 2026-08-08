package app.openstory.fixtures.plugin

import app.openstory.common.AppResult
import app.openstory.model.ContentType
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogDetails
import app.openstory.plugin.api.catalog.CatalogFilterDefinition
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogImageReference
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSearchRequest
import app.openstory.plugin.api.catalog.CatalogSection

enum class FakeCatalogMode {
    NORMAL,
    UNSTABLE_SEARCH_IDS,
    UNDECLARED_IMAGE_HOST,
}

class FakeCatalogPlugin(
    private val mode: FakeCatalogMode =
        FakeCatalogMode.NORMAL,
) : CatalogPlugin {
    private var searchCallCount = 0

    override suspend fun home(
        request: CatalogHomeRequest,
    ): AppResult<List<CatalogSection>> =
        AppResult.Success(
            listOf(
                CatalogSection(
                    sourceId = "fixture-section",
                    title = "Fixture section",
                    items = listOf(card()),
                ),
            ),
        )

    override suspend fun search(
        request: CatalogSearchRequest,
    ): AppResult<Page<CatalogCard>> {
        searchCallCount += 1

        val sourceId =
            if (
                mode ==
                FakeCatalogMode.UNSTABLE_SEARCH_IDS
            ) {
                "fixture-catalog-$searchCallCount"
            } else {
                DEFAULT_SOURCE_ID
            }

        return AppResult.Success(
            Page(
                items = listOf(
                    card(sourceId),
                ),
                nextToken = null,
            ),
        )
    }

    override suspend fun details(
        sourceId: String,
    ): AppResult<CatalogDetails> =
        AppResult.Success(
            CatalogDetails(
                sourceId = sourceId,
                sourceUrl =
                    "https://fixture.example/catalog/$sourceId",
                title = "Deterministic Catalog Story",
                aliases = emptyList(),
                authors = listOf("Fixture Author"),
                description =
                    "Deterministic catalog fixture.",
                genres = listOf("Fantasy"),
                contentType = ContentType.WEB_NOVEL,
                languageTags = setOf("en"),
                image = image(),
                score = null,
                popularityRank = 1L,
            ),
        )

    override suspend fun filters():
        AppResult<List<CatalogFilterDefinition>> =
        AppResult.Success(
            emptyList(),
        )

    private fun card(
        sourceId: String = DEFAULT_SOURCE_ID,
    ): CatalogCard =
        CatalogCard(
            sourceId = sourceId,
            title = "Deterministic Catalog Story",
            contentType = ContentType.WEB_NOVEL,
            authors = listOf("Fixture Author"),
            image = image(),
            score = null,
        )

    private fun image(): CatalogImageReference {
        val host =
            if (
                mode ==
                FakeCatalogMode.UNDECLARED_IMAGE_HOST
            ) {
                "undeclared.example"
            } else {
                "fixture.example"
            }

        return CatalogImageReference(
            url = "https://$host/cover.jpg",
            declaredHost = host,
        )
    }

    private companion object {
        const val DEFAULT_SOURCE_ID =
            "fixture-catalog-1"
    }
}
