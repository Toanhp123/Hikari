package app.openstory.plugin.api.catalog

import app.openstory.model.ContentType
import app.openstory.plugin.api.PageItem
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
)

@Serializable
data class CatalogSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogCard>,
)

@Serializable
data class CatalogCard(
    val sourceId: String,
    val title: String,
    val authors: List<String>,
    val image: CatalogImageReference?,
    val score: CatalogScore?,
) : PageItem {
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
)

@Serializable
data class CatalogImageReference(
    val url: String,
    val declaredHost: String,
)

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
)

@Serializable
@SerialName("select")
data class CatalogSelectFilter(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOption>,
) : CatalogFilterDefinition

@Serializable
@SerialName("multi_select")
data class CatalogMultiSelectFilter(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOption>,
) : CatalogFilterDefinition

@Serializable
@SerialName("range")
data class CatalogRangeFilter(
    override val id: String,
    override val label: String,
    val minimum: Double,
    val maximum: Double,
    val step: Double,
) : CatalogFilterDefinition

@Serializable
@SerialName("text")
data class CatalogTextFilter(
    override val id: String,
    override val label: String,
    val placeholder: String?,
) : CatalogFilterDefinition

@Serializable
@SerialName("sort")
data class CatalogSortFilter(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOption>,
) : CatalogFilterDefinition
