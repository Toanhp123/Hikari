package app.openstory.plugin.api.selector

import kotlinx.serialization.Serializable

@Serializable
data class SelectorRequestPlan(
    val operations: List<SelectorOperation>,
    val limits: SelectorRequestedLimits? = null,
)

@Serializable
data class SelectorRequestedLimits(
    val maxOutputItems: Int? = null,
    val maxChapterBlocks: Int? = null,
    val maxChapterTextCharacters: Int? = null,
)
