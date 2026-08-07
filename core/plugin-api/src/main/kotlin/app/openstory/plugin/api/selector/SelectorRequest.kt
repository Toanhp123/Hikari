package app.openstory.plugin.api.selector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SelectorRequestOperation

@Serializable
@SerialName("http_get")
data class HttpGet(
    val urlTemplate: String,
) : SelectorRequestOperation

@Serializable
@SerialName("remove_elements")
data class RemoveElements(
    val css: String,
) : SelectorRequestOperation

@Serializable
data class SelectorRequestPlan(
    val operations: List<SelectorRequestOperation>,
    val limits: SelectorRequestedLimits? = null,
)

@Serializable
data class SelectorRequestedLimits(
    val maxOutputItems: Int? = null,
    val maxChapterBlocks: Int? = null,
    val maxChapterTextCharacters: Int? = null,
)
