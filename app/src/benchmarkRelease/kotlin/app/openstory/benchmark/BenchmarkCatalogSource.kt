package app.openstory.benchmark

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.id.PluginId
import app.openstory.di.ExclusiveCatalogSources
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

internal const val BENCHMARK_SEARCH_QUERY = "hikari deterministic search"
internal const val BENCHMARK_SEARCH_RESULT_TITLE = "Hikari Deterministic Search Result"

internal class BenchmarkCatalogSource : CatalogSource {
    override val pluginId = PluginId("benchmark.local")
    override val version: String = "1.0.0"

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> {
        val items = if (request.query == BENCHMARK_SEARCH_QUERY) {
            listOf(
                SourceItem(
                    sourceId = "benchmark-search-result",
                    title = BENCHMARK_SEARCH_RESULT_TITLE,
                    contentType = SourceContentType.MANGA,
                    authors = setOf("Search Fixture Author"),
                    coverUrl = null,
                    scoreValue = 9.0,
                    scoreScale = 10.0,
                ),
            )
        } else {
            emptyList()
        }
        return CatalogSourceResult.Success(SourceSearchPage(items, nextToken = null))
    }

    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = error("unused")

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
        CatalogSourceResult.Success(emptyList())

    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> =
        CatalogSourceResult.Success(emptyList())
}

@Module
@InstallIn(SingletonComponent::class)
internal object BenchmarkCatalogSourceModule {
    @Provides
    @IntoSet
    @ExclusiveCatalogSources
    fun provideBenchmarkCatalogSource(): CatalogSource = BenchmarkCatalogSource()
}
