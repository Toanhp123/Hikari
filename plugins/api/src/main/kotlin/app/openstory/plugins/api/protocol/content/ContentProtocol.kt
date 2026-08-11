package app.openstory.plugins.api.protocol.content

import app.openstory.plugins.api.protocol.catalog.WireContentType
import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentSearchRequestDto(val query: String, val nextToken: String? = null) {
    init {
        require(query.isNotBlank() && query.length <= MAX_QUERY_LENGTH) { "Search query must be non-blank and bounded" }
        requireToken(nextToken)
    }
}

@Serializable
data class ContentResolveUrlRequestDto(val url: String) {
    init {
        requireHttpsUrl(url)
    }
}

@Serializable
data class ContentStoryRequestDto(val sourceStoryId: String) {
    init {
        requireSourceId(sourceStoryId, "sourceStoryId")
    }
}

@Serializable
data class ContentChaptersRequestDto(val sourceStoryId: String, val nextToken: String? = null) {
    init {
        requireSourceId(sourceStoryId, "sourceStoryId")
        requireToken(nextToken)
    }
}

@Serializable
data class ContentChapterRequestDto(val sourceReleaseId: String) {
    init {
        requireSourceId(sourceReleaseId, "sourceReleaseId")
    }
}

@Serializable
data class ContentStoryCandidateDto(
    val sourceStoryId: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val contentType: WireContentType? = null,
    val sourceUrl: String? = null,
) {
    init {
        requireSourceId(sourceStoryId, "sourceStoryId")
        requireText(title, "title")
        requireTextList(aliases, "aliases")
        requireTextList(authors, "authors")
        requireHttpsUrl(sourceUrl)
    }
}

@Serializable
data class ContentStoryDetailsDto(
    val sourceStoryId: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val description: String? = null,
) {
    init {
        requireSourceId(sourceStoryId, "sourceStoryId")
        requireText(title, "title")
        requireTextList(aliases, "aliases")
        requireTextList(authors, "authors")
        require(description == null || description.length <= MAX_DESCRIPTION_LENGTH) { "Description is too long" }
    }
}

@Serializable
data class ContentReleaseDto(
    val sourceReleaseId: String,
    val title: String?,
    val rawNumber: String?,
    val languageTag: String?,
    val publishedAtEpochMillis: Long?,
) {
    init {
        requireSourceId(sourceReleaseId, "sourceReleaseId")
        require(title == null || title.isNotBlank() && title.length <= MAX_TEXT_LENGTH) { "Release title is invalid" }
        require(
            rawNumber == null || rawNumber.isNotBlank() && rawNumber.length <= MAX_TEXT_LENGTH,
        ) { "Raw number is invalid" }
        require(
            languageTag == null || languageTag.isNotBlank() &&
                languageTag == languageTag.lowercase() && languageTag.none(Char::isWhitespace),
        ) {
            "Language tag must be normalized"
        }
        require(
            publishedAtEpochMillis == null || publishedAtEpochMillis >= MIN_EPOCH_MILLIS,
        ) { "Published timestamp must not be negative" }
    }
}

@Serializable
data class ChapterDocumentDto(
    val title: String?,
    val blocks: List<ChapterBlockDto>,
) {
    init {
        require(title == null || title.isNotBlank() && title.length <= MAX_TEXT_LENGTH) { "Chapter title is invalid" }
        require(blocks.size <= MAX_BLOCKS) { "Chapter has too many blocks" }
    }
}

@Serializable
sealed interface ChapterBlockDto

@Serializable
@SerialName("paragraph")
data class ParagraphBlockDto(val text: String) : ChapterBlockDto {
    init {
        requireBlockText(text)
    }
}

@Serializable
@SerialName("heading")
data class HeadingBlockDto(val level: Int, val text: String) : ChapterBlockDto {
    init {
        require(level in MIN_HEADING_LEVEL..MAX_HEADING_LEVEL) {
            "Heading level must be between $MIN_HEADING_LEVEL and $MAX_HEADING_LEVEL"
        }
        requireBlockText(text)
    }
}

@Serializable
@SerialName("divider")
data object DividerBlockDto : ChapterBlockDto

@Serializable
@SerialName("note")
data class NoteBlockDto(val text: String) : ChapterBlockDto {
    init {
        requireBlockText(text)
    }
}

private const val MAX_ID_LENGTH = 1024
private const val MAX_QUERY_LENGTH = 1024
private const val MAX_URL_LENGTH = 4096
private const val MAX_TOKEN_LENGTH = 4096
private const val MAX_TEXT_LENGTH = 4096
private const val MAX_BLOCK_TEXT_LENGTH = 100_000
private const val MAX_DESCRIPTION_LENGTH = 200_000
private const val MAX_LIST_ITEMS = 200
private const val MAX_BLOCKS = 5_000
private const val MIN_EPOCH_MILLIS = 0L
private const val MIN_HEADING_LEVEL = 1
private const val MAX_HEADING_LEVEL = 6

private fun requireSourceId(value: String, field: String) {
    require(value.isNotBlank() && value.length <= MAX_ID_LENGTH) { "$field must be non-blank and bounded" }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters" }
}

private fun requireText(value: String, field: String) {
    require(value.isNotBlank() && value.length <= MAX_TEXT_LENGTH) { "$field must be non-blank and bounded" }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters" }
}

private fun requireTextList(values: List<String>, field: String) {
    require(values.size <= MAX_LIST_ITEMS) { "Too many $field" }
    values.forEach { requireText(it, field) }
}

private fun requireToken(value: String?) {
    require(value == null || value.isNotBlank() && value.length <= MAX_TOKEN_LENGTH) {
        "Continuation token must be null or non-blank and bounded"
    }
}

private fun requireBlockText(value: String) {
    require(
        value.isNotBlank() && value.length <= MAX_BLOCK_TEXT_LENGTH,
    ) { "Chapter block text must be non-blank and bounded" }
    require(value.none(Char::isISOControl)) { "Chapter block text must not contain control characters" }
}

private fun requireHttpsUrl(value: String?) {
    if (value == null) return
    require(value.length <= MAX_URL_LENGTH) { "URL is too long" }
    require(value.none(Char::isISOControl)) { "URL must not contain control characters" }
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null,
    ) { "URL must be HTTPS with a host and no user info" }
}
