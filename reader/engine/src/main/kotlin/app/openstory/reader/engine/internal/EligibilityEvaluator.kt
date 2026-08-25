package app.openstory.reader.engine.internal

import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRejection
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.CircuitState
import app.openstory.reader.engine.DiagnosticNote
import app.openstory.reader.engine.LanguageFallbackMode
import app.openstory.reader.engine.ReaderNetworkClass
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.ReaderRoutingSnapshot
import app.openstory.reader.engine.RejectionCode
import app.openstory.reader.engine.RoutingCandidate
import app.openstory.reader.engine.RoutingIntent
import app.openstory.reader.engine.SourceHealthSnapshot
import app.openstory.reader.engine.normalizeLanguageTag

internal data class EligibleRoutingCandidate(
    val candidate: RoutingCandidate,
    val localFingerprint: String?,
    val remoteEligible: Boolean,
)

internal data class EligibilityResult(
    val eligible: List<EligibleRoutingCandidate>,
    val rejections: List<CandidateRejection>,
    val diagnostics: List<DiagnosticNote>,
)

/** Pure access-path eligibility. Hard rules always run before preference or ranking. */
internal class EligibilityEvaluator {
    fun evaluate(
        snapshot: ReaderRoutingSnapshot,
        policy: ReaderRoutingPolicy,
    ): EligibilityResult {
        val healthBySource = snapshot.sourceHealth.associateBy { it.key.sourceId }
        val rejections = mutableListOf<CandidateRejection>()
        val eligible = mutableListOf<EligibleRoutingCandidate>()

        snapshot.candidates.forEach { candidate ->
            val localFingerprint = localFingerprint(candidate, policy, rejections)
            val remoteEligible = remoteEligible(
                candidate = candidate,
                networkClass = snapshot.networkClass,
                routingIntent = snapshot.routingIntent,
                health = healthBySource[candidate.sourceId],
                rejections = rejections,
            )
            val languageForbidden = policy.languageFallbackMode == LanguageFallbackMode.STRICT_ALLOWED &&
                normalizeLanguageTag(candidate.languageTag) !in policy.languageOrder
            if (languageForbidden) {
                rejections += rejection(candidate, null, RejectionCode.LANGUAGE_FORBIDDEN)
            }

            if (!languageForbidden && (localFingerprint != null || remoteEligible)) {
                eligible += EligibleRoutingCandidate(candidate, localFingerprint, remoteEligible)
            } else if (!languageForbidden && localFingerprint == null && !remoteEligible) {
                rejections += rejection(candidate, null, RejectionCode.NO_USABLE_ACCESS_PATH)
            }
        }

        val diagnostics = if (
            snapshot.explicitReleaseId != null &&
            snapshot.candidates.none { it.releaseId == snapshot.explicitReleaseId }
        ) {
            listOf(DiagnosticNote(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT))
        } else {
            emptyList()
        }

        return EligibilityResult(
            eligible = eligible.toList(),
            rejections = rejections.toList(),
            diagnostics = diagnostics,
        )
    }

    private fun localFingerprint(
        candidate: RoutingCandidate,
        policy: ReaderRoutingPolicy,
        rejections: MutableList<CandidateRejection>,
    ): String? = when (val local = candidate.localAccess) {
        is CandidateLocalAccess.AvailableExact -> local.fingerprint
        is CandidateLocalAccess.AvailableUnverified -> local.fingerprint.takeIf {
            policy.allowUnverifiedLocalAttempt
        }
        is CandidateLocalAccess.KnownInvalid -> {
            rejections += rejection(candidate, AccessMode.LOCAL, RejectionCode.LOCAL_COPY_KNOWN_INVALID)
            null
        }
        CandidateLocalAccess.Miss,
        CandidateLocalAccess.Unknown,
        -> null
    }

    private fun remoteEligible(
        candidate: RoutingCandidate,
        networkClass: ReaderNetworkClass,
        routingIntent: RoutingIntent,
        health: SourceHealthSnapshot?,
        rejections: MutableList<CandidateRejection>,
    ): Boolean {
        val rejectionCode = when {
            candidate.remoteAccess == CandidateRemoteAccess.SOURCE_UNAVAILABLE ->
                RejectionCode.REMOTE_SOURCE_DISABLED_OR_UNAVAILABLE
            networkClass == ReaderNetworkClass.OFFLINE -> RejectionCode.REMOTE_NETWORK_UNAVAILABLE
            routingIntent == RoutingIntent.PREFETCH && networkClass != ReaderNetworkClass.UNMETERED ->
                RejectionCode.REMOTE_NETWORK_UNAVAILABLE
            health?.state?.circuitState == CircuitState.OPEN -> RejectionCode.REMOTE_CIRCUIT_OPEN
            health?.state?.circuitState == CircuitState.HALF_OPEN && !health.halfOpenProbePermitted ->
                RejectionCode.HALF_OPEN_PROBE_NOT_PERMITTED
            else -> null
        }
        if (rejectionCode != null) {
            rejections += rejection(candidate, AccessMode.REMOTE, rejectionCode)
            return false
        }
        return true
    }

    private fun rejection(
        candidate: RoutingCandidate,
        accessMode: AccessMode?,
        code: RejectionCode,
    ) = CandidateRejection(
        releaseId = candidate.releaseId,
        sourceId = candidate.sourceId,
        accessMode = accessMode,
        code = code,
    )
}
