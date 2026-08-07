package app.openstory.plugin.api.content

import app.openstory.model.ContentType
import app.openstory.plugin.api.HOST_PATTERN
import app.openstory.plugin.api.PageItem
import app.openstory.plugin.api.httpsHost
import app.openstory.plugin.api.isHttpsUrl
import app.openstory.plugin.api.requireNonBlankDistinct
import app.openstory.plugin.api.requireNormalizedLanguageTag
import app.openstory.plugin.api.requireNormalizedLanguageTags
import app.openstory.plugin.api.requireStableId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentSearchRequest(
    val query: String,
    val nextToken: String? = null,
) {
    init {
        require(query.length <= MAX_QUERY_LENGTH) {
            "Content search query is too long."
        }
        require(nextToken == null || nextToken.isNotBlank()) {
            "Content continuation token must be null or non-blank."
        }
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 1_024
    }
}

@Serializable
data class ContentStoryCandidate(
    val sourceStoryId: String,
    val sourceUrl: String?,
    val title: String,
    val authors: List<String>,
    val contentType: ContentType,
    val languageTags: Set<String>,
) : PageItem {
    init {
        requireStableId(sourceStoryId, "Content story source ID")
        require(sourceUrl == null || isHttpsUrl(sourceUrl)) {
            "Content story URL must use HTTPS."
        }
        require(title.isNotBlank()) {
            "Content story title must not be blank."
        }
        requireNonBlankDistinct(authors, "Content story authors")
        requireNormalizedLanguageTags(languageTags)
    }

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
) {
    init {
        requireStableId(sourceStoryId, "Content story source ID")
        require(isHttpsUrl(sourceUrl)) {
            "Content story URL must use HTTPS."
        }
        require(title.isNotBlank()) {
            "Content story title must not be blank."
        }
        requireNonBlankDistinct(aliases, "Content story aliases")
        requireNonBlankDistinct(authors, "Content story authors")
        requireNormalizedLanguageTags(languageTags)
        require(
            directCatalogMappings
                .map { it.catalogPluginId to it.catalogSourceId }
                .distinct()
                .size == directCatalogMappings.size,
        ) {
            "Direct catalog mappings must be unique."
        }
    }
}

@Serializable
data class DirectCatalogMapping(
    val catalogPluginId: String,
    val catalogSourceId: String,
) {
    init {
        requireStableId(catalogPluginId, "Catalog plugin ID")
        requireStableId(catalogSourceId, "Catalog source ID")
    }
}

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
) {
    init {
        requireStableId(sourceReleaseId, "Source release ID")
        require(isHttpsUrl(sourceUrl)) {
            "Source release URL must use HTTPS."
        }
        requireNormalizedLanguageTag(languageTag)
        require(rawTitle.isNotBlank()) {
            "Source release raw title must not be blank."
        }
        require(publishedAtEpochMillis == null || publishedAtEpochMillis >= 0L) {
            "Published timestamp must not be negative."
        }
        require(updatedAtEpochMillis == null || updatedAtEpochMillis >= 0L) {
            "Updated timestamp must not be negative."
        }
    }
}

@Serializable
data class ChapterDocument(
    val title: String?,
    val blocks: List<ChapterBlock>,
) {
    init {
        require(blocks.isNotEmpty()) {
            "Chapter document must contain at least one block."
        }
        require(blocks.size <= MAX_CHAPTER_BLOCKS) {
            "Chapter document contains too many blocks."
        }
    }

    private companion object {
        const val MAX_CHAPTER_BLOCKS = 5_000
    }
}

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
    ) : ChapterBlock {
        init {
            require(level in MIN_HEADING_LEVEL..MAX_HEADING_LEVEL) {
                "Chapter heading level must be between 1 and 6."
            }
        }
    }


    private companion object {
        const val MIN_HEADING_LEVEL = 1
        const val MAX_HEADING_LEVEL = 6
    }

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
) {
    init {
        require(value.length <= MAX_CHAPTER_TEXT_LENGTH) {
            "Chapter text is too long."
        }
        require(spans.all { span -> span.endExclusive <= value.length }) {
            "Chapter text spans must stay within the text."
        }
        require(spans == spans.sortedWith(compareBy(ChapterTextSpan::start, ChapterTextSpan::endExclusive))) {
            "Chapter text spans must use deterministic order."
        }
    }

    private companion object {
        const val MAX_CHAPTER_TEXT_LENGTH = 1_000_000
    }
}

@Serializable
data class ChapterTextSpan(
    val start: Int,
    val endExclusive: Int,
    val style: ChapterTextStyle,
) {
    init {
        require(start >= 0 && endExclusive > start) {
            "Chapter text span must have a positive in-bounds range."
        }
    }
}

@Serializable
enum class ChapterTextStyle {
    EMPHASIS,
    STRONG,
}

@Serializable
data class ChapterImageReference(
    val url: String,
    val declaredHost: String,
) {
    init {
        require(declaredHost.matches(HOST_PATTERN)) {
            "Chapter image host must be normalized."
        }
        require(httpsHost(url) == declaredHost) {
            "Chapter image URL must use HTTPS and match its declared host."
        }
    }
}
