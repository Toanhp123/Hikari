package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.RecoveryScope
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.penalizesSourceHealth

/**
 * Reader-internal semantic failure. String error codes survive only at the UI/diagnostic result
 * boundary; routing and health behavior are driven by typed facts.
 */
internal data class ReaderAttemptFailure(
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val accessMode: AccessMode,
    val observation: SourceObservation,
    val recoveryScope: RecoveryScope,
    val code: String,
    val retryable: Boolean,
    val remoteAttemptKind: RemoteAttemptKind? = null,
) {
    init {
        require(code.isNotBlank()) { "Reader failure code must not be blank." }
        when (accessMode) {
            AccessMode.REMOTE -> require(remoteAttemptKind != null) {
                "REMOTE Reader attempt failures require an attempt origin."
            }
            AccessMode.LOCAL -> require(remoteAttemptKind == null) {
                "LOCAL Reader attempt failures cannot carry a remote attempt origin."
            }
        }
        val observationKind = (observation as? SourceObservation.RemoteAttemptObservation)?.kind
        require(observationKind == null || observationKind == remoteAttemptKind) {
            "Typed remote observation origin must match ReaderAttemptFailure.remoteAttemptKind."
        }
    }

    val penalizesSourceHealth: Boolean
        get() = observation.penalizesSourceHealth

    fun toLoadFailure(): ReaderLoadFailure = ReaderLoadFailure(
        releaseId = releaseId,
        code = code,
        retryable = retryable,
    )
}
