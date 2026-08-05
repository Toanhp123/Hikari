package app.openstory.plugin.api.content

import app.openstory.model.ContentType
import app.openstory.plugin.api.PageItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentSearchRequest(
    val query: String,
    val nextToken: String? = null,
)

@Serializable
data class ContentStoryCandidate(
    val sourceStoryId: String,
    val sourceUrl: String?,
    val title: String,
    val authors: List<String>,
    val contentType: ContentType,
    val languageTags: Set<String>,
) : PageItem {
    override val stableKey: String
        get() = sourceStoryId
}

@Serializable
data class ContentStoryDetails(
    val sourceStoryId: String,
    val sourceUrl: String,
    val title: String,
    val aliases: List<String>,
    val authors: List<String>,
    val description: String?,
    val contentType: ContentType,
    val languageTags: Set<String>,
    val directCatalogMappings: List<DirectCatalogMapping> = emptyList(),
)

@Serializable
data class DirectCatalogMapping(
    val catalogPluginId: String,
    val catalogSourceId: String,
)

@Serializable
enum class ChapterKindHint {
    NUMBERED,
    PROLOGUE,
    EPILOGUE,
    SIDE_STORY,
    EXTRA,
    UNKNOWN,
}

@Serializable
data class SourceChapterRelease(
    val sourceReleaseId: String,
    val sourceUrl: String,
    val languageTag: String,
    val rawTitle: String,
    val rawVolume: String?,
    val rawChapter: String?,
    val rawPart: String?,
    val kindHint: ChapterKindHint,
    val normalizedVolumeHint: String?,
    val normalizedChapterHint: String?,
    val normalizedPartHint: String?,
    val normalizedTitleHint: String?,
    val translatorOrUploader: String?,
    val publishedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val contentFingerprint: String?,
)

@Serializable
data class ChapterDocument(
    val title: String?,
    val blocks: List<ChapterBlock>,
)

@Serializable
sealed interface ChapterBlock {

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val text: ChapterText,
    ) : ChapterBlock

    @Serializable
    @SerialName("heading")
    data class Heading(
        val level: Int,
        val text: ChapterText,
    ) : ChapterBlock

    @Serializable
    @SerialName("divider")
    data object Divider : ChapterBlock

    @Serializable
    @SerialName("image")
    data class Image(
        val reference: ChapterImageReference,
        val altText: String?,
    ) : ChapterBlock

    @Serializable
    @SerialName("note")
    data class Note(
        val text: ChapterText,
    ) : ChapterBlock
}

@Serializable
data class ChapterText(
    val value: String,
    val spans: List<ChapterTextSpan> = emptyList(),
)

@Serializable
data class ChapterTextSpan(
    val start: Int,
    val endExclusive: Int,
    val style: ChapterTextStyle,
)

@Serializable
enum class ChapterTextStyle {
    EMPHASIS,
    STRONG,
}

@Serializable
data class ChapterImageReference(
    val url: String,
    val declaredHost: String,
)
