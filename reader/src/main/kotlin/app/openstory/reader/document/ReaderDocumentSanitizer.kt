package app.openstory.reader.document

import app.openstory.plugins.api.protocol.content.ChapterBlockDto
import app.openstory.plugins.api.protocol.content.ChapterDocumentDto
import app.openstory.plugins.api.protocol.content.DividerBlockDto
import app.openstory.plugins.api.protocol.content.HeadingBlockDto
import app.openstory.plugins.api.protocol.content.NoteBlockDto
import app.openstory.plugins.api.protocol.content.ParagraphBlockDto
import java.security.MessageDigest

class ReaderDocumentSanitizer {
    fun sanitize(input: ChapterDocumentDto): DocumentValidationResult = when {
        input.blocks.isEmpty() -> invalid("reader.document_empty")
        input.blocks.size > MAX_BLOCKS -> invalid("reader.document_too_large")
        else -> sanitizeContent(input)
    }

    private fun sanitizeContent(input: ChapterDocumentDto): DocumentValidationResult {
        val title = input.title?.safeText(MAX_TITLE_LENGTH)
        if (input.title != null && title == null) return invalid("reader.document_title_invalid")

        var totalCharacters = title?.length ?: 0
        val blocks = ArrayList<ReaderBlock>(input.blocks.size)
        var failure: String? = null
        for ((index, block) in input.blocks.withIndex()) {
            val sanitized = sanitizeBlock(index, block)
            val blockFailure = when {
                sanitized == null -> "reader.document_block_invalid"
                totalCharacters + sanitized.characterCount() > MAX_DOCUMENT_CHARACTERS ->
                    "reader.document_too_large"
                else -> null
            }
            if (blockFailure != null) {
                failure = blockFailure
                break
            }
            totalCharacters += checkNotNull(sanitized).characterCount()
            blocks += sanitized
        }
        return failure?.let(::invalid) ?: valid(title, blocks)
    }

    private fun valid(title: String?, blocks: List<ReaderBlock>): DocumentValidationResult.Valid =
        DocumentValidationResult.Valid(ReaderDocument(title, blocks, sha256(canonicalText(title, blocks))))

    private fun sanitizeBlock(index: Int, block: ChapterBlockDto): ReaderBlock? = when (block) {
        is ParagraphBlockDto -> block.text.safeText(MAX_PARAGRAPH_LENGTH)?.let {
            ReaderBlock.Paragraph(blockId(index, it), it)
        }
        is HeadingBlockDto -> if (block.level in MIN_HEADING_LEVEL..MAX_HEADING_LEVEL) {
            block.text.safeText(MAX_HEADING_LENGTH)?.let { ReaderBlock.Heading(blockId(index, it), block.level, it) }
        } else {
            null
        }
        DividerBlockDto -> ReaderBlock.Divider(blockId(index, "divider"))
        is NoteBlockDto -> block.text.safeText(MAX_NOTE_LENGTH)?.let {
            ReaderBlock.Note(blockId(index, it), it)
        }
    }

    private fun String.safeText(maxLength: Int): String? =
        trim().takeIf { text -> text.isSafe(maxLength) }

    private fun String.isSafe(maxLength: Int): Boolean =
        isNotBlank() && length <= maxLength && none(::isDisallowedControl)

    private fun ReaderBlock.characterCount(): Int = when (this) {
        is ReaderBlock.Paragraph -> text.length
        is ReaderBlock.Heading -> text.length
        is ReaderBlock.Divider -> 0
        is ReaderBlock.Note -> text.length
    }

    private fun canonicalText(title: String?, blocks: List<ReaderBlock>): String = buildString {
        append(title.orEmpty()).append('\n')
        blocks.forEach { block ->
            append(block::class.simpleName).append(':').append(block.id).append(':')
            when (block) {
                is ReaderBlock.Paragraph -> append(block.text)
                is ReaderBlock.Heading -> append(block.level).append(':').append(block.text)
                is ReaderBlock.Divider -> Unit
                is ReaderBlock.Note -> append(block.text)
            }
            append('\n')
        }
    }

    private fun blockId(index: Int, text: String): String = "block-$index-${sha256(text).take(BLOCK_HASH_LENGTH)}"

    private fun invalid(code: String) = DocumentValidationResult.Invalid(code)

    private companion object {
        const val MAX_BLOCKS = 2_000
        const val MAX_DOCUMENT_CHARACTERS = 2_000_000
        const val MAX_TITLE_LENGTH = 512
        const val MAX_HEADING_LENGTH = 512
        const val MAX_PARAGRAPH_LENGTH = 50_000
        const val MAX_NOTE_LENGTH = 20_000
        const val MIN_HEADING_LEVEL = 1
        const val MAX_HEADING_LEVEL = 6
        const val BLOCK_HASH_LENGTH = 12
    }
}

private fun isDisallowedControl(character: Char): Boolean =
    character.isISOControl() && character != '\n' && character != '\t'

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
