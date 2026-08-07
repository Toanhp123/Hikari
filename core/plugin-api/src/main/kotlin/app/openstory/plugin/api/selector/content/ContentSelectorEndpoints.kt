package app.openstory.plugin.api.selector.content

import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.api.selector.SelectorTokenKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentSelectorEndpoints(
    val search: ContentSearchSelector? = null,
    val story: ContentStorySelector? = null,
    val latest: ContentReleasesSelector? = null,
    val allChapters: ContentReleasesSelector? = null,
    val sync: ContentSyncSelector? = null,
    val chapter: ContentChapterSelector? = null,
)

@Serializable
data class ContentSearchSelector(
    val request: SelectorRequestPlan,
    val items: SelectorBinding,
    val nextToken: SelectorBinding? = null,
    val nextTokenKind: SelectorTokenKind = SelectorTokenKind.OPAQUE,
)

@Serializable
data class ContentStorySelector(
    val request: SelectorRequestPlan,
    val details: SelectorBinding,
)

@Serializable
data class ContentReleasesSelector(
    val request: SelectorRequestPlan,
    val releases: SelectorBinding,
)

@Serializable
data class ContentSyncSelector(
    val request: SelectorRequestPlan,
    val delta: SelectorBinding,
    val nextTokenKind: SelectorTokenKind = SelectorTokenKind.OPAQUE,
)

@Serializable
data class ContentChapterSelector(
    val request: SelectorRequestPlan,
    val document: ChapterDocumentBinding,
)

@Serializable
data class ChapterDocumentBinding(
    val title: SelectorBinding? = null,
    val blocks: ChapterBlockListBinding,
)

@Serializable
data class ChapterBlockListBinding(
    val css: String,
    val variants: List<ChapterBlockVariantBinding>,
    val unmatchedElementPolicy: UnmatchedElementPolicy = UnmatchedElementPolicy.SKIP,
)

@Serializable
sealed interface ChapterBlockVariantBinding {
    val matches: String
}

@Serializable
@SerialName("paragraph")
data class ParagraphBlockBinding(
    override val matches: String,
    val text: ChapterTextBinding,
) : ChapterBlockVariantBinding

@Serializable
@SerialName("heading")
data class HeadingBlockBinding(
    override val matches: String,
    val level: SelectorBinding,
    val text: ChapterTextBinding,
) : ChapterBlockVariantBinding

@Serializable
@SerialName("divider")
data class DividerBlockBinding(
    override val matches: String,
) : ChapterBlockVariantBinding

@Serializable
@SerialName("image")
data class ImageBlockBinding(
    override val matches: String,
    val url: SelectorBinding,
    val declaredHost: SelectorBinding? = null,
    val altText: SelectorBinding? = null,
) : ChapterBlockVariantBinding

@Serializable
@SerialName("note")
data class NoteBlockBinding(
    override val matches: String,
    val text: ChapterTextBinding,
) : ChapterBlockVariantBinding

@Serializable
data class ChapterTextBinding(
    val value: SelectorBinding,
    val spans: ChapterSpanMode = ChapterSpanMode.NONE,
)

@Serializable
enum class ChapterSpanMode {
    NONE,
    SEMANTIC_HTML,
}

@Serializable
enum class UnmatchedElementPolicy {
    SKIP,
    ERROR,
}
