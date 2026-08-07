package app.openstory.plugin.api.selector.catalog

import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.api.selector.SelectorTokenKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogSelectorEndpoints(
    val home: CatalogHomeSelector? = null,
    val search: CatalogSearchSelector? = null,
    val details: CatalogDetailsSelector? = null,
    val filters: CatalogFiltersSelector? = null,
)

@Serializable
data class CatalogHomeSelector(
    val request: SelectorRequestPlan,
    val sections: SelectorBinding,
)

@Serializable
data class CatalogSearchSelector(
    val request: SelectorRequestPlan,
    val items: SelectorBinding,
    val nextToken: SelectorBinding? = null,
    val nextTokenKind: SelectorTokenKind = SelectorTokenKind.OPAQUE,
)

@Serializable
data class CatalogDetailsSelector(
    val request: SelectorRequestPlan,
    val details: SelectorBinding,
)

@Serializable
data class CatalogFiltersSelector(
    val filters: List<CatalogFilterBinding>,
)

@Serializable
sealed interface CatalogFilterBinding {
    val id: String
    val label: String
}

@Serializable
data class CatalogFilterOptionBinding(
    val value: String,
    val label: String,
)

@Serializable
@SerialName("select")
data class CatalogSelectFilterBinding(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOptionBinding>,
) : CatalogFilterBinding

@Serializable
@SerialName("multi_select")
data class CatalogMultiSelectFilterBinding(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOptionBinding>,
) : CatalogFilterBinding

@Serializable
@SerialName("range")
data class CatalogRangeFilterBinding(
    override val id: String,
    override val label: String,
    val minimum: Double,
    val maximum: Double,
    val step: Double,
) : CatalogFilterBinding

@Serializable
@SerialName("text")
data class CatalogTextFilterBinding(
    override val id: String,
    override val label: String,
    val placeholder: String?,
) : CatalogFilterBinding

@Serializable
@SerialName("sort")
data class CatalogSortFilterBinding(
    override val id: String,
    override val label: String,
    val options: List<CatalogFilterOptionBinding>,
) : CatalogFilterBinding
