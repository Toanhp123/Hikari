package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId

enum class CandidateRemoteAccess {
    PERMITTED,
    SOURCE_UNAVAILABLE,
}

sealed interface CandidateLocalAccess {
    data object Unknown : CandidateLocalAccess
    data object Miss : CandidateLocalAccess

    data class AvailableExact(val fingerprint: String) : CandidateLocalAccess {
        init {
            require(fingerprint.isNotBlank()) { "Local fingerprint must not be blank." }
        }
    }

    data class AvailableUnverified(val fingerprint: String) : CandidateLocalAccess {
        init {
            require(fingerprint.isNotBlank()) { "Local fingerprint must not be blank." }
        }
    }

    data class KnownInvalid(val fingerprint: String) : CandidateLocalAccess {
        init {
            require(fingerprint.isNotBlank()) { "Local fingerprint must not be blank." }
        }
    }
}

data class RoutingCandidate(
    val releaseId: ChapterReleaseId,
    val sourceId: PluginId,
    val languageTag: String,
    val sourceGroupKey: SourceGroupKey?,
    val publishedAtEpochMillis: Long?,
    val completeness: BasisPoints,
    val remoteAccess: CandidateRemoteAccess,
    val localAccess: CandidateLocalAccess,
) {
    init {
        require(normalizeLanguageTag(languageTag).isNotBlank()) {
            "Routing candidate languageTag must not be blank."
        }
    }
}

data class ReadingContinuity(
    val committedChapterId: CanonicalChapterId? = null,
    val committedReleaseId: ChapterReleaseId? = null,
    val committedSourceId: PluginId? = null,
    val committedSourceGroupKey: SourceGroupKey? = null,
    val committedLanguageTag: String? = null,
    val targetResumeReleaseId: ChapterReleaseId? = null,
    val targetResumeFingerprint: String? = null,
) {
    init {
        require(committedLanguageTag == null || normalizeLanguageTag(committedLanguageTag).isNotBlank()) {
            "Committed language tag must not be blank when present."
        }
        require(targetResumeFingerprint == null || targetResumeFingerprint.isNotBlank()) {
            "Target resume fingerprint must not be blank when present."
        }
    }
}

class ReaderRoutingSnapshot private constructor(
    val targetChapterId: CanonicalChapterId,
    val chapterGraphRevision: ReaderChapterGraphRevision,
    val planRevision: ReaderPlanRevision,
    val routingIntent: RoutingIntent,
    candidates: List<RoutingCandidate>,
    sourceHealth: List<SourceHealthSnapshot>,
    val continuity: ReadingContinuity,
    val networkClass: ReaderNetworkClass,
    val explicitReleaseId: ChapterReleaseId?,
    val nowEpochMillis: Long,
) {
    val candidates: List<RoutingCandidate> = candidates
        .sortedWith(compareBy({ it.sourceId.value }, { it.releaseId.value }))
        .toList()
    val sourceHealth: List<SourceHealthSnapshot> = sourceHealth
        .sortedWith(compareBy({ it.key.sourceId.value }, { it.key.operation.name }))
        .toList()

    init {
        require(this.candidates.map { it.releaseId }.distinct().size == this.candidates.size) {
            "ReaderRoutingSnapshot requires unique releaseId values."
        }
        require(this.sourceHealth.map { it.key }.distinct().size == this.sourceHealth.size) {
            "ReaderRoutingSnapshot requires unique source health keys."
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ReaderRoutingSnapshot &&
            targetChapterId == other.targetChapterId &&
            chapterGraphRevision == other.chapterGraphRevision &&
            planRevision == other.planRevision &&
            routingIntent == other.routingIntent &&
            candidates == other.candidates &&
            sourceHealth == other.sourceHealth &&
            continuity == other.continuity &&
            networkClass == other.networkClass &&
            explicitReleaseId == other.explicitReleaseId &&
            nowEpochMillis == other.nowEpochMillis

    override fun hashCode(): Int {
        var result = targetChapterId.hashCode()
        result = 31 * result + chapterGraphRevision.hashCode()
        result = 31 * result + planRevision.hashCode()
        result = 31 * result + routingIntent.hashCode()
        result = 31 * result + candidates.hashCode()
        result = 31 * result + sourceHealth.hashCode()
        result = 31 * result + continuity.hashCode()
        result = 31 * result + networkClass.hashCode()
        result = 31 * result + (explicitReleaseId?.hashCode() ?: 0)
        result = 31 * result + nowEpochMillis.hashCode()
        return result
    }

    companion object {
        fun create(
            targetChapterId: CanonicalChapterId,
            chapterGraphRevision: ReaderChapterGraphRevision,
            planRevision: ReaderPlanRevision,
            routingIntent: RoutingIntent,
            candidates: List<RoutingCandidate>,
            sourceHealth: List<SourceHealthSnapshot>,
            continuity: ReadingContinuity,
            networkClass: ReaderNetworkClass,
            explicitReleaseId: ChapterReleaseId?,
            nowEpochMillis: Long,
        ): ReaderRoutingSnapshot = ReaderRoutingSnapshot(
            targetChapterId = targetChapterId,
            chapterGraphRevision = chapterGraphRevision,
            planRevision = planRevision,
            routingIntent = routingIntent,
            candidates = candidates.toList(),
            sourceHealth = sourceHealth.toList(),
            continuity = continuity,
            networkClass = networkClass,
            explicitReleaseId = explicitReleaseId,
            nowEpochMillis = nowEpochMillis,
        )
    }
}
