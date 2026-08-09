package app.openstory.plugins.api.protocol.catalog

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WireContentType { LIGHT_NOVEL, WEB_NOVEL, MANGA, ANIME }

@Serializable
data class ScoreDto(val value: Double, val scale: Double) {
    init {
        require(value.isFinite()) { "Score value must be finite" }
        require(scale.isFinite() && scale > 0.0) { "Score scale must be finite and positive" }
        require(value in 0.0..scale) { "Score value must be within its scale" }
    }
}

@Serializable
data class CatalogItemDto(
    val sourceId: String,
    val title: String,
    val contentType: WireContentType,
    val authors: List<String> = emptyList(),
    val coverUrl: String? = null,
    val score: ScoreDto? = null,
) {
    init {
        requireStableText(sourceId, "sourceId", MAX_ID_LENGTH)
        requireBoundedText(title, "title")
        require(authors.size <= MAX_AUTHORS) { "Too many authors" }
        authors.forEach { requireBoundedText(it, "author") }
        requireHttpsUrl(coverUrl, "coverUrl")
    }
}

@Serializable
data class CatalogSectionDto(
    val sourceId: String,
    val title: String,
    val items: List<CatalogItemDto>,
) {
    init {
        requireStableText(sourceId, "sourceId", MAX_ID_LENGTH)
        requireBoundedText(title, "title")
        require(items.size <= MAX_SECTION_ITEMS) { "Catalog section has too many items" }
        require(items.map(CatalogItemDto::sourceId).distinct().size == items.size) {
            "Catalog section source IDs must be unique"
        }
    }
}

@Serializable
data class CatalogHomeRequestDto(
    val languageTags: Set<String> = emptySet(),
    val contentTypes: Set<WireContentType> = emptySet(),
) {
    init {
        require(languageTags.size <= MAX_LANGUAGE_TAGS) { "Too many language tags" }
        languageTags.forEach { requireLanguageTag(it) }
    }
}

@Serializable
data class CatalogHomeOutputDto(val sections: List<CatalogSectionDto>) {
    init {
        require(sections.size <= MAX_SECTIONS) { "Too many catalog sections" }
        require(sections.map(CatalogSectionDto::sourceId).distinct().size == sections.size) {
            "Catalog section IDs must be unique"
        }
    }
}

@Serializable
data class CatalogSearchRequestDto(
    val query: String,
    val filterValues: Map<String, List<String>> = emptyMap(),
    val nextToken: String? = null,
) {
    init {
        require(query.isNotBlank()) { "Search query must not be blank" }
        require(query.length <= MAX_QUERY_LENGTH) { "Search query is too long" }
        require(filterValues.size <= MAX_FILTERS) { "Too many filter values" }
        filterValues.forEach { (id, values) ->
            requireStableText(id, "filter id", MAX_ID_LENGTH)
            require(values.size <= MAX_FILTER_VALUES) { "Too many values for filter $id" }
            values.forEach { requireBoundedText(it, "filter value") }
        }
        requireToken(nextToken)
    }
}

@Serializable
data class CatalogSearchOutputDto(
    val items: List<CatalogItemDto>,
    val nextToken: String? = null,
) {
    init {
        require(items.size <= MAX_PAGE_ITEMS) { "Catalog search has too many items" }
        require(items.map(CatalogItemDto::sourceId).distinct().size == items.size) {
            "Catalog search source IDs must be unique"
        }
        requireToken(nextToken)
    }
}

@Serializable
data class CatalogDetailsRequestDto(val sourceId: String) {
    init {
        requireStableText(sourceId, "sourceId", MAX_ID_LENGTH)
    }
}

@Serializable
data class CatalogDetailsOutputDto(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val description: String?,
    val genres: Set<String> = emptySet(),
    val contentType: WireContentType,
    val languageTags: Set<String> = emptySet(),
    val coverUrl: String?,
    val score: ScoreDto?,
    val popularityRank: Long?,
) {
    init {
        requireStableText(sourceId, "sourceId", MAX_ID_LENGTH)
        requireHttpsUrl(sourceUrl, "sourceUrl")
        requireBoundedText(title, "title")
        requireBoundedCollection(aliases, "aliases")
        requireBoundedCollection(authors, "authors")
        require(description == null || description.length <= MAX_DESCRIPTION_LENGTH) { "Description is too long" }
        requireBoundedCollection(genres, "genres")
        require(languageTags.size <= MAX_LANGUAGE_TAGS) { "Too many language tags" }
        languageTags.forEach { requireLanguageTag(it) }
        requireHttpsUrl(coverUrl, "coverUrl")
        require(popularityRank == null || popularityRank > 0) { "Popularity rank must be positive" }
    }
}

@Serializable
sealed interface CatalogFilterDto {
    val id: String
    val label: String
}

@Serializable
@SerialName("option")
data class CatalogOptionFilterDto(
    override val id: String,
    override val label: String,
    val multiple: Boolean,
    val options: List<CatalogFilterOptionDto>,
) : CatalogFilterDto {
    init {
        requireFilterIdentity(id, label)
        require(options.isNotEmpty() && options.size <= MAX_FILTER_OPTIONS) { "Option filter has invalid option count" }
        require(options.map(CatalogFilterOptionDto::value).distinct().size == options.size) {
            "Filter option values must be unique"
        }
    }
}

@Serializable
@SerialName("range")
data class CatalogRangeFilterDto(
    override val id: String,
    override val label: String,
    val min: Double?,
    val max: Double?,
    val step: Double?,
) : CatalogFilterDto {
    init {
        requireFilterIdentity(id, label)
        require(min == null || min.isFinite()) { "Range minimum must be finite" }
        require(max == null || max.isFinite()) { "Range maximum must be finite" }
        require(min == null || max == null || min <= max) { "Range minimum must not exceed maximum" }
        require(step == null || step.isFinite() && step > 0.0) { "Range step must be positive" }
    }
}

@Serializable
@SerialName("text")
data class CatalogTextFilterDto(
    override val id: String,
    override val label: String,
) : CatalogFilterDto {
    init {
        requireFilterIdentity(id, label)
    }
}

@Serializable
data class CatalogFilterOptionDto(val value: String, val label: String) {
    init {
        requireBoundedText(value, "filter option value")
        requireBoundedText(label, "filter option label")
    }
}

@Serializable
data class CatalogFiltersOutputDto(val filters: List<CatalogFilterDto>) {
    init {
        require(filters.size <= MAX_FILTERS) { "Too many filters" }
        require(filters.map(CatalogFilterDto::id).distinct().size == filters.size) { "Filter IDs must be unique" }
    }
}

private const val MAX_ID_LENGTH = 1024
private const val MAX_TEXT_LENGTH = 4096
private const val MAX_DESCRIPTION_LENGTH = 200_000
private const val MAX_QUERY_LENGTH = 1024
private const val MAX_TOKEN_LENGTH = 4096
private const val MAX_AUTHORS = 100
private const val MAX_LANGUAGE_TAGS = 50
private const val MAX_SECTIONS = 50
private const val MAX_SECTION_ITEMS = 200
private const val MAX_PAGE_ITEMS = 200
private const val MAX_FILTERS = 100
private const val MAX_FILTER_VALUES = 100
private const val MAX_FILTER_OPTIONS = 500
private const val MAX_COLLECTION_ITEMS = 200
private const val MAX_LANGUAGE_TAG_LENGTH = 64

private fun requireStableText(value: String, field: String, maxLength: Int) {
    require(value.isNotBlank()) { "$field must not be blank" }
    require(value.length <= maxLength) { "$field is too long" }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters" }
}

private fun requireBoundedText(value: String, field: String) =
    requireStableText(value, field, MAX_TEXT_LENGTH)

private fun requireBoundedCollection(values: Collection<String>, field: String) {
    require(values.size <= MAX_COLLECTION_ITEMS) { "Too many $field" }
    values.forEach { requireBoundedText(it, field) }
}

private fun requireLanguageTag(value: String) {
    requireStableText(value, "language tag", MAX_LANGUAGE_TAG_LENGTH)
    require(value == value.lowercase() && value.none(Char::isWhitespace)) { "Language tag must be normalized" }
}

private fun requireHttpsUrl(value: String?, field: String) {
    if (value == null) return
    val uri = runCatching { URI(value) }.getOrNull()
    require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "$field must be an HTTPS URL"
    }
}

private fun requireToken(value: String?) {
    require(value == null || value.isNotBlank()) { "Continuation token must be null or non-blank" }
    require(value == null || value.length <= MAX_TOKEN_LENGTH) { "Continuation token is too long" }
}

private fun requireFilterIdentity(id: String, label: String) {
    requireStableText(id, "filter id", MAX_ID_LENGTH)
    requireBoundedText(label, "filter label")
}
