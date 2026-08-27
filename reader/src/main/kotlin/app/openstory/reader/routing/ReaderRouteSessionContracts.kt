package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.ReaderChapterGraphRevision

/** User intent only. Session-owned story/graph/preferences/continuity never ride on each request. */
data class ReaderForegroundIntent(
    val targetChapterId: CanonicalChapterId,
    val explicitReleaseId: ChapterReleaseId? = null,
)

data class ReaderExactRestoration(
    val blockId: String,
    val characterOffset: Int,
    val progressFraction: Float,
) {
    init {
        require(blockId.isNotBlank()) { "Restoration block ID must not be blank." }
        require(characterOffset >= 0) { "Restoration character offset must not be negative." }
        require(progressFraction.isFinite() && progressFraction in 0f..1f) {
            "Restoration progress fraction must be between zero and one."
        }
    }
}

data class ReaderForegroundIdentity(
    val sessionId: ReaderSessionId,
    val generationId: ReaderGenerationId,
    val targetChapterId: CanonicalChapterId,
)

sealed interface ReaderForegroundResult {
    val identity: ReaderForegroundIdentity

    data class Committed(
        override val identity: ReaderForegroundIdentity,
        val chapterGroup: CanonicalChapterGroup,
        val release: ChapterRelease,
        val document: ReaderDocument,
        val fromLocal: Boolean,
        val restoration: ReaderExactRestoration?,
    ) : ReaderForegroundResult

    data class Exhausted(
        override val identity: ReaderForegroundIdentity,
        val code: String,
        val retryable: Boolean,
        val attempts: List<ReaderLoadFailure>,
    ) : ReaderForegroundResult {
        init {
            require(code.isNotBlank()) { "Reader exhaustion code must not be blank." }
        }
    }

    data class Superseded(
        override val identity: ReaderForegroundIdentity,
    ) : ReaderForegroundResult
}

internal data class ReaderRouteExecutionContext(
    val storyId: StoryId,
    val identity: ReaderExecutionIdentity,
    val chapterGraphRevision: ReaderChapterGraphRevision,
    val chapterGraph: ReaderSessionChapterGraph,
    val preferences: ReaderRoutingPreferences,
    val committedIdentity: ReaderCommittedIdentity?,
    val explicitReleaseId: ChapterReleaseId?,
    val knownInvalidLocalFingerprints: Map<ChapterReleaseId, Set<String>> = emptyMap(),
) {
    val foregroundIdentity: ReaderForegroundIdentity
        get() = identity.toForegroundIdentity()
}

internal fun interface ReaderRouteExecutionDelegate {
    suspend fun execute(
        session: ReaderRouteSession,
        context: ReaderRouteExecutionContext,
    ): ReaderForegroundResult
}

internal fun ReaderExecutionIdentity.toForegroundIdentity() = ReaderForegroundIdentity(
    sessionId = sessionId,
    generationId = generationId,
    targetChapterId = targetChapterId,
)
