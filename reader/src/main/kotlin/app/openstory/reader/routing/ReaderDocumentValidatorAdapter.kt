package app.openstory.reader.routing

import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.RecoveryScope
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import java.net.URI

internal sealed interface ReaderDocumentValidation {
    data class Valid(val document: ReaderDocument) : ReaderDocumentValidation

    data class Invalid(
        val observation: SourceObservation,
        val recoveryScope: RecoveryScope,
        val legacyCode: String,
    ) : ReaderDocumentValidation
}

/** Validates invariants available after plugin sanitization / local materialization. */
internal class ReaderDocumentValidatorAdapter {
    fun validateRemote(
        document: ReaderDocument,
        attemptKind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
    ): ReaderDocumentValidation {
        val invalidCode = materializedInvalidCode(document)
        return when (invalidCode) {
            null -> ReaderDocumentValidation.Valid(document)
            READER_DOCUMENT_EMPTY -> ReaderDocumentValidation.Invalid(
                observation = SourceObservation.ContentFailure.EmptyDocument(attemptKind),
                recoveryScope = RecoveryScope.SOURCE_SCOPED,
                legacyCode = invalidCode,
            )
            else -> ReaderDocumentValidation.Invalid(
                observation = SourceObservation.ContentFailure.InvalidDocument(attemptKind),
                recoveryScope = RecoveryScope.SOURCE_SCOPED,
                legacyCode = invalidCode,
            )
        }
    }

    fun validateLocal(
        document: ReaderDocument,
        requestedFingerprint: String? = null,
    ): ReaderDocumentValidation {
        val invalidCode = materializedInvalidCode(document)
        if (invalidCode != null) {
            return localInvalid("reader.local_document_invalid")
        }
        if (requestedFingerprint != null && document.fingerprint != requestedFingerprint) {
            return localInvalid("reader.local_fingerprint_mismatch")
        }
        return ReaderDocumentValidation.Valid(document)
    }

    private fun localInvalid(code: String) = ReaderDocumentValidation.Invalid(
        observation = SourceObservation.LocalFailure.FingerprintOrDecodeMismatch,
        recoveryScope = RecoveryScope.LOCAL_SCOPED,
        legacyCode = code,
    )

    private fun materializedInvalidCode(document: ReaderDocument): String? = when {
        document.blocks.isEmpty() -> READER_DOCUMENT_EMPTY
        document.fingerprint.isBlank() -> READER_DOCUMENT_BLOCK_INVALID
        document.title != null && document.title.isBlank() -> READER_DOCUMENT_TITLE_INVALID
        document.blocks.any { !it.isMaterializedValid() } -> READER_DOCUMENT_BLOCK_INVALID
        else -> null
    }

    private fun ReaderBlock.isMaterializedValid(): Boolean = when (this) {
        is ReaderBlock.Paragraph -> id.isNotBlank() && text.isNotBlank()
        is ReaderBlock.Heading -> id.isNotBlank() && level in 1..6 && text.isNotBlank()
        is ReaderBlock.Divider -> id.isNotBlank()
        is ReaderBlock.Note -> id.isNotBlank() && text.isNotBlank()
        is ReaderBlock.ImagePage -> id.isNotBlank() && isSafeHttpsUrl(imageUrl)
    }

    private fun isSafeHttpsUrl(value: String): Boolean = runCatching { URI(value) }.getOrNull()?.let { uri ->
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    } == true

    private companion object {
        const val READER_DOCUMENT_EMPTY = "reader.document_empty"
        const val READER_DOCUMENT_TITLE_INVALID = "reader.document_title_invalid"
        const val READER_DOCUMENT_BLOCK_INVALID = "reader.document_block_invalid"
    }
}
