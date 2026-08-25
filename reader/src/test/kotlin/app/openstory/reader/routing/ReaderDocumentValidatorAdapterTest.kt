package app.openstory.reader.routing

import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderDocumentValidatorAdapterTest {
    private val validator = ReaderDocumentValidatorAdapter()

    @Test
    fun emptyMaterializedRemoteDocumentIsTypedEmptyContentFailure() {
        val result = validator.validateRemote(
            ReaderDocument(title = null, blocks = emptyList(), fingerprint = "fingerprint"),
            RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        )

        val invalid = assertIs<ReaderDocumentValidation.Invalid>(result)
        assertIs<SourceObservation.ContentFailure.EmptyDocument>(invalid.observation)
        assertEquals("reader.document_empty", invalid.legacyCode)
    }

    @Test
    fun validMaterializedDocumentIsSemanticSuccess() {
        val document = document("fingerprint")
        assertEquals(document, assertIs<ReaderDocumentValidation.Valid>(validator.validateRemote(document)).document)
    }

    @Test
    fun exactLocalFingerprintMismatchIsLocalDecodeMismatch() {
        val result = validator.validateLocal(document("decoded"), requestedFingerprint = "requested")

        val invalid = assertIs<ReaderDocumentValidation.Invalid>(result)
        assertIs<SourceObservation.LocalFailure.FingerprintOrDecodeMismatch>(invalid.observation)
        assertEquals("reader.local_fingerprint_mismatch", invalid.legacyCode)
    }

    @Test
    fun remoteChangedFingerprintIsNotComparedWithSavedProgressFingerprint() {
        val changed = document("new-fingerprint")
        assertIs<ReaderDocumentValidation.Valid>(validator.validateRemote(changed))
    }

    @Test
    fun invalidMaterializedFieldsAreRejectedBeforeSemanticSuccess() {
        val blankFingerprint = document(" ")
        assertIs<ReaderDocumentValidation.Invalid>(validator.validateRemote(blankFingerprint))
        val blankBlockId = ReaderDocument(
            title = null,
            blocks = listOf(ReaderBlock.Paragraph(" ", "text")),
            fingerprint = "fingerprint",
        )
        assertIs<ReaderDocumentValidation.Invalid>(validator.validateRemote(blankBlockId))
    }

    private fun document(fingerprint: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", "text")),
        fingerprint = fingerprint,
    )
}
