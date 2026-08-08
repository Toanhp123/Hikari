package app.openstory.plugin.api.catalog

import app.openstory.model.ContentType
import app.openstory.plugin.api.HOST_PATTERN
import app.openstory.plugin.api.PageItem
import app.openstory.plugin.api.httpsHost
import app.openstory.plugin.api.isHttpsUrl
import app.openstory.plugin.api.requireNonBlankDistinct
import app.openstory.plugin.api.requireNormalizedLanguageTags
import app.openstory.plugin.api.requireStableId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogHomeRequest(
    val languageTags: Set<String> = emptySet(),
    val contentTypes: Set<ContentType> = emptySet(),
)

@Serializable
data class CatalogSearchRequest(
    val query: String,
    val filterValues: Map<String, List<String>> = emptyMap(),
    val nextToken: String? = null,
) {
    init {
        require(query.length <= MAX_QUERY_LENGTH) {
            "Catalog search query is too long."
        }
        require(filterValues.keys.all(String::isNotBlank)) {
            "Catalog search filter IDs must not be blank."
        }
        require(nextToken == null || nextToken.isNotBlank()) {
            "Catalog continuation token must be null or non-blank."
        }
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 1_024
    }
}

@Serializable
data class CatalogSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogCard>,
) {
    init {
        requireStableId(sourceId, "Catalog section source ID")
        require(title.isNotBlank()) {
            "Catalog section title must not be blank."
        }
        require(items.size <= MAX_SECTION_ITEMS) {
            "Catalog section contains too many items."
        }
        require(items.map(CatalogCard::sourceId).distinct().size == items.size) {
            "Catalog section item source IDs must be unique."
        }
    }

    private companion object {
        const val MAX_SECTION_ITEMS = 100
    }
}

@Serializable
data class CatalogCard(
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: List<String>,
    val image: CatalogImageReference?,
    val score: CatalogScore?,
) : PageItem {
    init {
        requireStableId(sourceId, "Catalog card source ID")
        require(title.isNotBlank()) {
            "Catalog card title must not be blank."
        }
        requireNonBlankDistinct(authors, "Catalog card authors")
    }

    override val stableKey: String
        get() = sourceId
}

@Serializable
data class CatalogDetails(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: List<String>,
    val authors: List<String>,
    val description: String?,
    val genres: List<String>,
    val contentType: ContentType,
    val languageTags: Set<String>,
    val image: CatalogImageReference?,
    val score: CatalogScore?,
    val popularityRank: Long?,
) {
    init {
        requireStableId(sourceId, "Catalog details source ID")
        require(sourceUrl == null || isHttpsUrl(sourceUrl)) {
            "Catalog source URL must use HTTPS."
        }
        require(title.isNotBlank()) {
            "Catalog details title must not be blank."
        }
        requireNonBlankDistinct(aliases, "Catalog aliases")
        requireNonBlankDistinct(authors, "Catalog authors")
        requireNonBlankDistinct(genres, "Catalog genres")
        requireNormalizedLanguageTags(languageTags)
        require(popularityRank == null || popularityRank > 0L) {
            "Catalog popularity rank must be positive."
        }
    }
}

@Serializable
data class CatalogImageReference(
    val url: String,
    val declaredHost: String,
) {
    init {
        require(declaredHost.matches(HOST_PATTERN)) {
            "Catalog image host must be normalized."
        }
        require(httpsHost(url) == declaredHost) {
            "Catalog image URL must use HTTPS and match its declared host."
        }
    }
}

@Serializable
data class CatalogScore(
    val value: Double,
    val scale: Double,
) {
    init {
        require(scale.isFinite() && scale > 0.0) {
            "Catalog score scale must be finite and greater than zero."
        }
        require(value.isFinite() && value in 0.0..scale) {
            "Catalog score value must be finite and within its declared scale."
        }
    }
}

@Serializable
sealed interface CatalogFilterDefinition {
    val id: String
    val label: String
}

@Serializable
data class CatalogFilterOption(
    val value: String,
    val label: String,
) {
    init {
        require(value.isNotBlank() && value.none(Char::isISOControl)) {
            "Catalog filter option value must be non-blank data."
        }
        require(label.isNotBlank()) {
            "Catalog filter option label must not be blank."
        }
    }
}

@Serializable
@SerialName("select")
data class CatalogSelectFilter(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOption>,
) : CatalogFilterDefinition {
    init {
        validateFilterIdentity(id, label)
        validateOptions(options)
    }
}

@Serializable
@SerialName("multi_select")
data class CatalogMultiSelectFilter(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOption>,
) : CatalogFilterDefinition {
    init {
        validateFilterIdentity(id, label)
        validateOptions(options)
    }
}

@Serializable
@SerialName("range")
data class CatalogRangeFilter(
    override val id: String,
    override val label: String,
    val minimum: Double,
    val maximum: Double,
    val step: Double,
) : CatalogFilterDefinition {
    init {
        validateFilterIdentity(id, label)
        require(minimum.isFinite() && maximum.isFinite() && step.isFinite()) {
            "Catalog range values must be finite."
        }
        require(minimum < maximum) {
            "Catalog range minimum must be less than maximum."
        }
        require(step > 0.0) {
            "Catalog range step must be positive."
        }
    }
}

@Serializable
@SerialName("text")
data class CatalogTextFilter(
    override val id: String,
    override val label: String,
    val placeholder: String?,
) : CatalogFilterDefinition {
    init {
        validateFilterIdentity(id, label)
        require(placeholder == null || placeholder.none(Char::isISOControl)) {
            "Catalog text placeholder must not contain control characters."
        }
    }
}

@Serializable
@SerialName("sort")
data class CatalogSortFilter(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOption>,
) : CatalogFilterDefinition {
    init {
        validateFilterIdentity(id, label)
        validateOptions(options)
    }
}

private fun validateFilterIdentity(
    id: String,
    label: String,
) {
    requireStableId(id, "Catalog filter ID")
    require(label.isNotBlank()) {
        "Catalog filter label must not be blank."
    }
}

private fun validateOptions(options: List<CatalogFilterOption>) {
    require(options.isNotEmpty()) {
        "Catalog filter options must not be empty."
    }
    require(options.size <= MAX_FILTER_OPTIONS) {
        "Catalog filter has too many options."
    }
    require(options.map(CatalogFilterOption::value).distinct().size == options.size) {
        "Catalog filter option values must be unique."
    }
}

private const val MAX_FILTER_OPTIONS = 200
