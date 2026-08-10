package app.openstory.catalog.source

import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogFilterDto
import app.openstory.plugins.api.protocol.catalog.CatalogFiltersOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogItemDto
import app.openstory.plugins.api.protocol.catalog.CatalogOptionFilterDto
import app.openstory.plugins.api.protocol.catalog.CatalogRangeFilterDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogSectionDto
import app.openstory.plugins.api.protocol.catalog.CatalogTextFilterDto
import app.openstory.plugins.api.protocol.catalog.WireContentType
import app.openstory.plugins.runtime.InstalledPlugin
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.PluginRuntime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class PluginCatalogSource(
    private val installed: InstalledPlugin,
    private val runtime: PluginRuntime,
    private val json: Json,
) : CatalogSource {
    override val pluginId = installed.pluginId
    override val version = installed.version

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = invoke(
        operation = PluginOperation.CATALOG_HOME,
        input = json.encodeToJsonElement(request.toDto()),
        serializer = CatalogHomeOutputDto.serializer(),
    ) { output -> output.sections.map(CatalogSectionDto::toSource) }

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> = invoke(
        operation = PluginOperation.CATALOG_SEARCH,
        input = json.encodeToJsonElement(request.toDto()),
        serializer = CatalogSearchOutputDto.serializer(),
    ) { output -> SourceSearchPage(output.items.map(CatalogItemDto::toSource), output.nextToken) }

    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> = invoke(
        operation = PluginOperation.CATALOG_DETAILS,
        input = json.encodeToJsonElement(CatalogDetailsRequestDto(sourceId)),
        serializer = CatalogDetailsOutputDto.serializer(),
        transform = CatalogDetailsOutputDto::toSource,
    )

    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = invoke(
        operation = PluginOperation.CATALOG_FILTERS,
        input = json.encodeToJsonElement(emptyMap<String, String>()),
        serializer = CatalogFiltersOutputDto.serializer(),
    ) { output -> output.filters.map(CatalogFilterDto::toSource) }

    private suspend fun <Wire, Source> invoke(
        operation: PluginOperation,
        input: kotlinx.serialization.json.JsonElement,
        serializer: KSerializer<Wire>,
        transform: (Wire) -> Source,
    ): CatalogSourceResult<Source> = when (val result = runtime.invoke(pluginId, operation, input)) {
        is PluginCallResult.Success -> CatalogSourceResult.Success(
            transform(json.decodeFromJsonElement(serializer, result.value)),
        )
        is PluginCallResult.Failure -> CatalogSourceResult.Failure(
            CatalogSourceFailure(code = result.code, retryable = result.retryable),
        )
    }
}

private fun SourceHomeRequest.toDto() = CatalogHomeRequestDto(
    languageTags = languageTags,
    contentTypes = contentTypes.map(SourceContentType::toWire).toSet(),
)

private fun SourceSearchRequest.toDto() = CatalogSearchRequestDto(query, filterValues, nextToken)

private fun CatalogSectionDto.toSource() = SourceSection(sourceId, title, items.map(CatalogItemDto::toSource))

private fun CatalogItemDto.toSource() = SourceItem(
    sourceId = sourceId,
    title = title,
    contentType = contentType.toSource(),
    authors = authors.toSet(),
    coverUrl = coverUrl,
    scoreValue = score?.value,
    scoreScale = score?.scale,
)

private fun CatalogDetailsOutputDto.toSource() = SourceDetails(
    sourceId = sourceId,
    sourceUrl = sourceUrl,
    title = title,
    aliases = aliases,
    authors = authors,
    description = description,
    genres = genres,
    contentType = contentType.toSource(),
    languageTags = languageTags,
    coverUrl = coverUrl,
    scoreValue = score?.value,
    scoreScale = score?.scale,
    popularityRank = popularityRank,
)

private fun CatalogFilterDto.toSource(): SourceFilter = when (this) {
    is CatalogOptionFilterDto -> SourceOptionFilter(
        id = id,
        label = label,
        multiple = multiple,
        options = options.map { SourceFilterOption(it.value, it.label) },
    )
    is CatalogRangeFilterDto -> SourceRangeFilter(id, label, min, max, step)
    is CatalogTextFilterDto -> SourceTextFilter(id, label)
}

private fun WireContentType.toSource(): SourceContentType = when (this) {
    WireContentType.LIGHT_NOVEL -> SourceContentType.LIGHT_NOVEL
    WireContentType.WEB_NOVEL -> SourceContentType.WEB_NOVEL
    WireContentType.MANGA -> SourceContentType.MANGA
    WireContentType.ANIME -> SourceContentType.ANIME
}

private fun SourceContentType.toWire(): WireContentType = when (this) {
    SourceContentType.LIGHT_NOVEL -> WireContentType.LIGHT_NOVEL
    SourceContentType.WEB_NOVEL -> WireContentType.WEB_NOVEL
    SourceContentType.MANGA -> WireContentType.MANGA
    SourceContentType.ANIME -> WireContentType.ANIME
}
