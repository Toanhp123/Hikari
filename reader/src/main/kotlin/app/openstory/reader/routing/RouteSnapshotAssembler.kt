package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.LanguageFallbackMode
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.ReadingContinuity
import app.openstory.reader.engine.RoutingIntent
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.CancellationException

internal data class AssembledRouteSnapshot(
    val targetIndex: Int,
    val targetGroup: CanonicalChapterGroup,
    val candidates: List<ChapterRelease>,
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
    private val cacheFacts: ReaderCacheFactsPort = ReaderCacheFactsPort { releaseIds, _ ->
        releaseIds.associateWith { ReaderLocalCacheFact.Unknown }
    },
    private val networkFacts: ReaderNetworkFactsPort = ReaderNetworkFactsPort { ReaderNetworkState.UNKNOWN },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun assemble(context: ReaderRouteExecutionContext): AssembledRouteSnapshot? =
        assemble(context.toPlanningContext(), RoutingIntent.FOREGROUND)

    suspend fun assemble(
        context: ReaderRoutePlanningContext,
        routingIntent: RoutingIntent,
    ): AssembledRouteSnapshot? {
        val targetIndex = context.chapterGroups.indexOfFirst {
            it.chapter.id == context.targetChapterId
        }
        if (targetIndex < 0) return null

        val targetGroup = context.chapterGroups[targetIndex]
        val restored = progress.find(context.storyId, context.targetChapterId)
        val candidates = targetGroup.releases
        val releaseIds = targetGroup.releases.mapTo(linkedSetOf()) { it.id }
        val resumeFingerprints = restored
            ?.takeIf { it.releaseId in releaseIds }
            ?.let { mapOf(it.releaseId to it.contentFingerprint) }
            .orEmpty()
        val localFacts = inspectCacheFacts(releaseIds, resumeFingerprints)
        val networkClass = networkClass()
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
                        remotePlanningPermitted(routingIntent, networkClass) &&
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
                val localAccess = localAccess(
                    localFacts[release.id] ?: ReaderLocalCacheFact.Unknown,
                    context.knownInvalidLocalFingerprints[release.id].orEmpty(),
                )
                ReaderRoutingCandidateMapper.productionCandidate(
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
                committedSourceId = committed?.sourceId,
                committedSourceGroupKey = null,
                committedLanguageTag = committedLanguage,
                targetResumeReleaseId = restored?.releaseId,
                targetResumeFingerprint = restored?.contentFingerprint,
            )
            return AssembledRouteSnapshot(
                targetIndex = targetIndex,
                targetGroup = targetGroup,
                candidates = candidates,
                restoredProgress = restored,
                snapshot = ReaderRoutingSnapshot.create(
                    targetChapterId = context.targetChapterId,
                    chapterGraphRevision = context.chapterGraphRevision,
                    planRevision = context.planRevision,
                    routingIntent = routingIntent,
                    candidates = routingCandidates,
                    sourceHealth = sourceHealth,
                    continuity = continuity,
                    networkClass = networkClass,
                    explicitReleaseId = context.explicitReleaseId,
                    nowEpochMillis = now,
                ),
                policy = ReaderRoutingPolicy.v1(
                    languageOrder = context.preferences.languageOrder,
                    languageFallbackMode = LanguageFallbackMode.ORDERED_ALLOW,
                ),
                probeLeases = probeLeases.toList(),
            )
        } catch (failure: Throwable) {
            probeLeases.forEach(ReaderHalfOpenProbeLease::release)
            throw failure
        }
    }


    private fun remotePlanningPermitted(
        routingIntent: RoutingIntent,
        networkClass: ReaderNetworkClass,
    ): Boolean = when (routingIntent) {
        RoutingIntent.FOREGROUND -> networkClass != ReaderNetworkClass.OFFLINE
        RoutingIntent.PREFETCH -> networkClass == ReaderNetworkClass.UNMETERED
    }

    private suspend fun inspectCacheFacts(
        releaseIds: Set<ChapterReleaseId>,
        resumeFingerprints: Map<ChapterReleaseId, String>,
    ): Map<ChapterReleaseId, ReaderLocalCacheFact> = try {
        cacheFacts.inspect(releaseIds, resumeFingerprints)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        releaseIds.associateWith { ReaderLocalCacheFact.Unknown }
    }

    private suspend fun networkClass(): ReaderNetworkClass {
        val state = try {
            networkFacts.current()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReaderNetworkState.UNKNOWN
        }
        return when (state) {
            ReaderNetworkState.OFFLINE -> ReaderNetworkClass.OFFLINE
            ReaderNetworkState.METERED -> ReaderNetworkClass.METERED
            ReaderNetworkState.UNMETERED -> ReaderNetworkClass.UNMETERED
            ReaderNetworkState.UNKNOWN -> ReaderNetworkClass.UNKNOWN
        }
    }

    private fun localAccess(
        fact: ReaderLocalCacheFact,
        knownInvalid: Set<String>,
    ): CandidateLocalAccess = when (fact) {
        ReaderLocalCacheFact.Unknown -> CandidateLocalAccess.Unknown
        ReaderLocalCacheFact.Miss -> CandidateLocalAccess.Miss
        is ReaderLocalCacheFact.Exact -> if (fact.fingerprint in knownInvalid) {
            CandidateLocalAccess.KnownInvalid(fact.fingerprint)
        } else {
            CandidateLocalAccess.AvailableExact(fact.fingerprint)
        }
        is ReaderLocalCacheFact.Unverified -> if (fact.fingerprint in knownInvalid) {
            CandidateLocalAccess.KnownInvalid(fact.fingerprint)
        } else {
            CandidateLocalAccess.AvailableUnverified(fact.fingerprint)
        }
    }
}
