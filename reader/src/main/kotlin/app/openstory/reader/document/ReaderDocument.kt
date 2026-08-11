package app.openstory.reader.document

data class ReaderDocument(
    val title: String?,
    val blocks: List<ReaderBlock>,
    val fingerprint: String,
)

sealed interface ReaderBlock {
    val id: String

    data class Paragraph(override val id: String, val text: String) : ReaderBlock
    data class Heading(override val id: String, val level: Int, val text: String) : ReaderBlock
    data class Divider(override val id: String) : ReaderBlock
    data class Note(override val id: String, val text: String) : ReaderBlock
}

sealed interface DocumentValidationResult {
    data class Valid(val document: ReaderDocument) : DocumentValidationResult
    data class Invalid(val code: String) : DocumentValidationResult
}
