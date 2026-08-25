package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.internal.EligibilityEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EligibilityEvaluatorTest {
    private val evaluator = EligibilityEvaluator()

    @Test
    fun localEligibilityIsIndependentFromRemoteAvailabilityHealthAndNetwork() {
        val candidate = candidate(
            local = CandidateLocalAccess.AvailableExact("fp"),
            remote = CandidateRemoteAccess.SOURCE_UNAVAILABLE,
        )
        val result = evaluator.evaluate(
            snapshot(
                candidates = listOf(candidate),
                network = ReaderNetworkClass.OFFLINE,
                health = listOf(health("source", CircuitState.OPEN)),
            ),
            ReaderRoutingPolicy.v1(),
        )

        assertEquals("fp", result.eligible.single().localFingerprint)
        assertFalse(result.eligible.single().remoteEligible)
        assertTrue(result.rejections.any { it.code == RejectionCode.REMOTE_SOURCE_DISABLED_OR_UNAVAILABLE })
    }

    @Test
    fun exactAndPolicyAllowedUnverifiedLocalAreRoutableButMissUnknownAndInvalidAreNot() {
        fun eligible(local: CandidateLocalAccess, allowUnverified: Boolean = true) = evaluator.evaluate(
            snapshot(listOf(candidate(local = local))),
            ReaderRoutingPolicy.v1(allowUnverifiedLocalAttempt = allowUnverified),
        ).eligible.single()

        assertEquals("exact", eligible(CandidateLocalAccess.AvailableExact("exact")).localFingerprint)
        assertEquals("unverified", eligible(CandidateLocalAccess.AvailableUnverified("unverified")).localFingerprint)
        assertEquals(null, eligible(CandidateLocalAccess.AvailableUnverified("blocked"), false).localFingerprint)
        assertEquals(null, eligible(CandidateLocalAccess.Miss).localFingerprint)
        assertEquals(null, eligible(CandidateLocalAccess.Unknown).localFingerprint)

        val invalid = evaluator.evaluate(
            snapshot(listOf(candidate(local = CandidateLocalAccess.KnownInvalid("bad")))),
            ReaderRoutingPolicy.v1(),
        )
        assertTrue(invalid.rejections.any { it.code == RejectionCode.LOCAL_COPY_KNOWN_INVALID })
        assertTrue(invalid.eligible.single().remoteEligible)
    }

    @Test
    fun remoteEligibilityHonorsOfflineAvailabilityOpenAndHalfOpenLease() {
        fun remoteEligible(
            network: ReaderNetworkClass = ReaderNetworkClass.UNKNOWN,
            remote: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
            circuit: CircuitState = CircuitState.CLOSED,
            probe: Boolean = false,
        ): Boolean = evaluator.evaluate(
            snapshot(
                listOf(candidate(remote = remote)),
                network = network,
                health = listOf(health("source", circuit, probe)),
            ),
            ReaderRoutingPolicy.v1(),
        ).eligible.firstOrNull()?.remoteEligible == true

        assertFalse(remoteEligible(network = ReaderNetworkClass.OFFLINE))
        assertFalse(remoteEligible(remote = CandidateRemoteAccess.SOURCE_UNAVAILABLE))
        assertFalse(remoteEligible(circuit = CircuitState.OPEN))
        assertFalse(remoteEligible(circuit = CircuitState.HALF_OPEN, probe = false))
        assertTrue(remoteEligible(circuit = CircuitState.HALF_OPEN, probe = true))
    }

    @Test
    fun strictLanguageRejectsCandidateWideButOrderedAllowNeverDoes() {
        val strict = evaluator.evaluate(
            snapshot(listOf(candidate(language = "fr"))),
            ReaderRoutingPolicy.v1(
                languageOrder = listOf("vi", "en"),
                languageFallbackMode = LanguageFallbackMode.STRICT_ALLOWED,
            ),
        )
        assertTrue(strict.eligible.isEmpty())
        assertTrue(strict.rejections.any { it.code == RejectionCode.LANGUAGE_FORBIDDEN })

        val ordered = evaluator.evaluate(
            snapshot(listOf(candidate(language = "fr"))),
            ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en")),
        )
        assertEquals(1, ordered.eligible.size)
    }

    @Test
    fun allPathsRejectedProducesNoUsableAccessPathAndExplicitCannotBypassHardRules() {
        val explicit = ChapterReleaseId("release")
        val result = evaluator.evaluate(
            snapshot(
                candidates = listOf(candidate(remote = CandidateRemoteAccess.SOURCE_UNAVAILABLE)),
                explicit = explicit,
            ),
            ReaderRoutingPolicy.v1(),
        )

        assertTrue(result.eligible.isEmpty())
        assertTrue(result.rejections.any { it.code == RejectionCode.NO_USABLE_ACCESS_PATH })
    }

    @Test
    fun absentExplicitReleaseIsDiagnosticOnlyAndAutomaticCandidatesRemain() {
        val result = evaluator.evaluate(
            snapshot(
                candidates = listOf(candidate()),
                explicit = ChapterReleaseId("absent"),
            ),
            ReaderRoutingPolicy.v1(),
        )
        assertEquals(1, result.eligible.size)
        assertEquals(
            listOf(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT),
            result.diagnostics.map { it.code },
        )
    }

    private fun candidate(
        language: String = "vi",
        remote: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        local: CandidateLocalAccess = CandidateLocalAccess.Miss,
    ) = RoutingCandidate(
        releaseId = ChapterReleaseId("release"),
        sourceId = PluginId("source"),
        languageTag = language,
        sourceGroupKey = null,
        publishedAtEpochMillis = 1L,
        completeness = BasisPoints(10_000),
        remoteAccess = remote,
        localAccess = local,
    )

    private fun snapshot(
        candidates: List<RoutingCandidate>,
        network: ReaderNetworkClass = ReaderNetworkClass.UNKNOWN,
        health: List<SourceHealthSnapshot> = emptyList(),
        explicit: ChapterReleaseId? = null,
    ) = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(1),
        planRevision = ReaderPlanRevision(0),
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = health,
        continuity = ReadingContinuity(),
        networkClass = network,
        explicitReleaseId = explicit,
        nowEpochMillis = 100L,
    )

    private fun health(source: String, circuit: CircuitState, probe: Boolean = false) = SourceHealthSnapshot(
        key = SourceOperationKey(PluginId(source)),
        state = when (circuit) {
            CircuitState.CLOSED -> SourceHealthState()
            CircuitState.OPEN -> SourceHealthState(
                circuitState = CircuitState.OPEN,
                openCount = 1,
                openedAtEpochMillis = 1L,
                nextProbeAtEpochMillis = 2L,
            )
            CircuitState.HALF_OPEN -> SourceHealthState(
                circuitState = CircuitState.HALF_OPEN,
                openCount = 1,
                openedAtEpochMillis = 1L,
                nextProbeAtEpochMillis = 2L,
            )
        },
        origin = SourceHealthOrigin.PROCESS_OBSERVED,
        halfOpenProbePermitted = probe,
    )
}
