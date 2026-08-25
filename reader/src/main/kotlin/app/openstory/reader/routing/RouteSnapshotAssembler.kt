package app.openstory.reader.routing

import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.ReadingContinuity
import app.openstory.reader.engine.RoutingIntent
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionPolicy

internal data class AssembledRouteSnapshot(
    val targetIndex: Int,
    val targetGroup: CanonicalChapterGroup,
    val candidates: List<ReleaseCandidate>,
    val expectedFingerprints: Map<ChapterReleaseId, String>,
    val restoredProgress: ReadingProgress?,
    val snapshot: ReaderRoutingSnapshot,
    val policy: ReaderRoutingPolicy,
    val probeLeases: List<ReaderHalfOpenProbeLease>,
)

internal class RouteSnapshotAssembler(
    private val progress: ReadingProgressRepository,
    private val sourceAvailability: ReaderSourceAvailability,
    private val healthRegistry: ReaderSourceHealthRegistry,
    private val executionLimiter: ReaderSourceExecutionLimiter,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun assemble(context: ReaderRouteExecutionContext): AssembledRouteSnapshot? {
        val targetIndex = context.chapterGroups.indexOfFirst {
            it.chapter.id == context.identity.targetChapterId
        }
        if (targetIndex < 0) return null

        val targetGroup = context.chapterGroups[targetIndex]
        val restored = progress.find(context.storyId, context.identity.targetChapterId)
        val candidates = targetGroup.releases.map(::ReleaseCandidate)
        val previousPluginId = restored?.releaseId?.let { releaseId ->
            targetGroup.releases.firstOrNull { it.id == releaseId }?.pluginId
        }
        val selectionPolicy = ReleaseSelectionPolicy(
            explicitReleaseId = context.explicitReleaseId,
            previousReleaseId = restored?.releaseId,
            previousPluginId = previousPluginId,
            languageOrder = context.preferences.languageOrder,
        )
        val expectedFingerprints = restored?.let {
            mapOf(it.releaseId to it.contentFingerprint)
        }.orEmpty()
        val now = nowEpochMillis()
        require(now >= 0L) { "Reader route snapshot clock must be non-negative." }
        val enabledSourceIds = sourceAvailability.enabledPluginIds().toSet()
        val probeLeases = mutableListOf<ReaderHalfOpenProbeLease>()

        try {
            val sourceHealth = targetGroup.releases
                .map { it.pluginId }
                .distinct()
                .sortedBy(PluginId::value)
                .map { sourceId ->
                    val key = SourceOperationKey(sourceId)
                    val base = healthRegistry.snapshot(key, now)
                    if (
                        sourceId in enabledSourceIds &&
                        base.state.circuitState == CircuitState.HALF_OPEN
                    ) {
                        executionLimiter.tryAcquireHalfOpenProbe(key)?.let { lease ->
                            probeLeases += lease
                            base.copy(halfOpenProbePermitted = true)
                        } ?: base
                    } else {
                        base
                    }
                }

            val routingCandidates = targetGroup.releases.map { release ->
                val expectedFingerprint = expectedFingerprints[release.id]
                val localAccess = if (
                    expectedFingerprint != null &&
                    context.knownInvalidLocalFingerprints[release.id]?.contains(expectedFingerprint) == true
                ) {
                    CandidateLocalAccess.KnownInvalid(expectedFingerprint)
                } else {
                    CandidateLocalAccess.Unknown
                }
                LegacyReaderRoutingAdapter.productionCandidate(
                    release = release,
                    remoteAccess = if (release.pluginId in enabledSourceIds) {
                        CandidateRemoteAccess.PERMITTED
                    } else {
                        CandidateRemoteAccess.SOURCE_UNAVAILABLE
                    },
                    localAccess = localAccess,
                )
            }
            val committed = context.committedIdentity
            val committedLanguage = committed?.let { identity ->
                context.chapterGroups.asSequence()
                    .flatMap { it.releases.asSequence() }
                    .firstOrNull { it.id == identity.releaseId }
                    ?.languageTag
            }
            val continuity = ReadingContinuity(
                committedChapterId = committed?.chapterId,
                committedReleaseId = committed?.releaseId,
                // Preserve the M1/M2 compatibility ranking key. Cross-chapter committed-source
                // preference becomes adaptive policy work in M4 rather than leaking in here.
                committedSourceId = previousPluginId,
                committedSourceGroupKey = selectionPolicy.previousSourceGroup?.let {
                    app.openstory.reader.engine.SourceGroupKey(it)
                },
                committedLanguageTag = committedLanguage,
                targetResumeReleaseId = restored?.releaseId,
                targetResumeFingerprint = restored?.contentFingerprint,
            )
            return AssembledRouteSnapshot(
                targetIndex = targetIndex,
                targetGroup = targetGroup,
                candidates = candidates,
                expectedFingerprints = expectedFingerprints,
                restoredProgress = restored,
                snapshot = ReaderRoutingSnapshot.create(
                    targetChapterId = context.identity.targetChapterId,
                    chapterGraphRevision = context.chapterGraphRevision,
                    planRevision = context.identity.planRevision,
                    routingIntent = RoutingIntent.FOREGROUND,
                    candidates = routingCandidates,
                    sourceHealth = sourceHealth,
                    continuity = continuity,
                    networkClass = ReaderNetworkClass.UNKNOWN,
                    explicitReleaseId = context.explicitReleaseId,
                    nowEpochMillis = now,
                ),
                policy = LegacyReaderRoutingAdapter.compatibilityPolicy(selectionPolicy),
                probeLeases = probeLeases.toList(),
            )
        } catch (failure: Throwable) {
            probeLeases.forEach(ReaderHalfOpenProbeLease::release)
            throw failure
        }
    }
}
