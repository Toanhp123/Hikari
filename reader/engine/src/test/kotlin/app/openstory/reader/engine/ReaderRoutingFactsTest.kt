package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderRoutingFactsTest {
    private fun candidate(
        release: String = "release-1",
        source: String = "source-1",
        language: String = "vi",
        localAccess: CandidateLocalAccess = CandidateLocalAccess.Miss,
    ) = RoutingCandidate(
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        languageTag = language,
        sourceGroupKey = null,
        publishedAtEpochMillis = null,
        completeness = BasisPoints(10_000),
        remoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess = localAccess,
    )

    private fun snapshot(candidates: List<RoutingCandidate>): ReaderRoutingSnapshot =
        ReaderRoutingSnapshot.create(
            targetChapterId = CanonicalChapterId("chapter-1"),
            chapterGraphRevision = ReaderChapterGraphRevision(1),
            planRevision = ReaderPlanRevision(2),
            routingIntent = RoutingIntent.FOREGROUND,
            candidates = candidates,
            sourceHealth = emptyList(),
            continuity = ReadingContinuity(),
            networkClass = ReaderNetworkClass.UNKNOWN,
            explicitReleaseId = null,
            nowEpochMillis = 123L,
        )

    @Test
    fun routingCandidateRejectsBlankLanguageAndLocalLocatorRejectsBlankFingerprint() {
        assertFailsWith<IllegalArgumentException> { candidate(language = "  ") }
        assertFailsWith<IllegalArgumentException> { CandidateLocalAccess.AvailableExact(" ") }
        assertFailsWith<IllegalArgumentException> { CandidateLocalAccess.AvailableUnverified("") }
        assertFailsWith<IllegalArgumentException> { CandidateLocalAccess.KnownInvalid("\t") }
    }

    @Test
    fun snapshotRejectsDuplicateReleaseIdsAndCanonicalizesCandidates() {
        val a = candidate(release = "release-a", source = "source-z")
        val b = candidate(release = "release-b", source = "source-a")
        assertFailsWith<IllegalArgumentException> { snapshot(listOf(a, a)) }

        val input = mutableListOf(a, b)
        val routed = snapshot(input)
        input.clear()

        assertEquals(listOf(b, a), routed.candidates)
    }

    @Test
    fun localAttemptRequiresFingerprintAndRemoteForbidsIt() {
        assertFailsWith<IllegalArgumentException> {
            RouteAttempt(
                attemptId = "attempt-0",
                releaseId = ChapterReleaseId("release-1"),
                sourceId = PluginId("source-1"),
                accessMode = AccessMode.LOCAL,
                localFingerprint = null,
                role = AttemptRole.PRIMARY,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RouteAttempt(
                attemptId = "attempt-0",
                releaseId = ChapterReleaseId("release-1"),
                sourceId = PluginId("source-1"),
                accessMode = AccessMode.REMOTE,
                localFingerprint = "fp",
                role = AttemptRole.PRIMARY,
            )
        }
        val remote = RouteAttempt(
            attemptId = "attempt-0",
            releaseId = ChapterReleaseId("release-1"),
            sourceId = PluginId("source-1"),
            accessMode = AccessMode.REMOTE,
            localFingerprint = null,
            role = AttemptRole.PRIMARY,
        )
        assertNull(remote.localFingerprint)
    }

    @Test
    fun healthPolicyAndStateEnforceBoundedV1Facts() {
        assertEquals(HealthPolicyVersion.HEALTH_POLICY_V1, HealthPolicy.v1().version)
        assertEquals(BasisPoints(2_000), HealthPolicy.v1().alpha)
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(alpha = BasisPoints(0)) }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(minimumCooldownMillis = 0) }
        assertFailsWith<IllegalArgumentException> { HealthPolicy.v1(maxLatencySamples = 21) }
        assertFailsWith<IllegalArgumentException> {
            SourceHealthState(recentLatencySamplesMillis = List(21) { 1L })
        }
        assertFailsWith<IllegalArgumentException> {
            SourceHealthState(recentLatencySamplesMillis = listOf(-1L))
        }
    }

    @Test
    fun decisionAndTraceUseTheSameSinglePlanRevisionTypeAndOrderedLists() {
        val revision = ReaderPlanRevision(7)
        val trace = emptyTrace(
            planRevision = revision,
            chapterGraphRevision = ReaderChapterGraphRevision(3),
            policyVersion = ReaderPolicyVersion.READER_POLICY_V1,
            decisionReason = DecisionReason.NO_ELIGIBLE_CANDIDATE,
        )
        val decision = ReaderRouteDecision(
            hesContractVersion = HesContractVersion.HES_V1,
            algorithmVersion = ReaderRoutingAlgorithmVersion.READER_ROUTING_V1,
            policyVersion = ReaderPolicyVersion.READER_POLICY_V1,
            planRevision = revision,
            competitiveSet = CompetitiveSet(primary = null, hedge = null),
            hedgeDirective = HedgeDirective.Omitted(HedgeOmissionReason.NOT_ELIGIBLE),
            recoveryChain = emptyList(),
            rejections = emptyList(),
            trace = trace,
            confidence = BasisPoints(0),
            reason = DecisionReason.NO_ELIGIBLE_CANDIDATE,
        )

        assertEquals(revision, decision.planRevision)
        assertEquals(revision, decision.trace.planRevision)
        assertEquals(emptyList<ChapterReleaseId>(), decision.trace.canonicalCandidateIds)
        assertEquals(emptyList<RouteAttempt>(), decision.recoveryChain)
    }

    @Test
    fun explicitReleaseNotPresentIsDiagnosticNotCandidateRejection() {
        assertFailsWith<IllegalArgumentException> {
            CandidateRejection(
                releaseId = ChapterReleaseId("release-1"),
                sourceId = PluginId("source-1"),
                accessMode = null,
                code = RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT,
            )
        }
        val note = DiagnosticNote(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT)
        assertEquals(RejectionCode.EXPLICIT_RELEASE_NOT_PRESENT, note.code)
    }
    @Test
    fun semanticAndAccessTraceFeaturesCannotBeConflated() {
        val semantic = SemanticFeatureVector(
            language = BasisPoints(10_000),
            continuity = BasisPoints(8_000),
            completeness = BasisPoints(10_000),
            freshness = BasisPoints(5_000),
        )
        val access = AccessFeatureVector(
            health = BasisPoints(10_000),
            reliability = BasisPoints(9_000),
            latency = BasisPoints(6_500),
            cacheUtility = BasisPoints(0),
        )
        val evaluation = CandidateEvaluationTrace(
            releaseId = ChapterReleaseId("release-1"),
            semanticFeatures = semantic,
            preferredAccessFeatures = access,
            semanticWeightedScore = BasisPoints(8_500),
            remoteAccessScore = BasisPoints(8_100),
        )

        assertEquals(semantic, evaluation.semanticFeatures)
        assertEquals(access, evaluation.preferredAccessFeatures)
    }

    @Test
    fun hedgeDirectiveIsAnExecutionContractRatherThanTraceOnlyMetadata() {
        val primary = RouteAttempt(
            attemptId = "attempt-0",
            releaseId = ChapterReleaseId("release-1"),
            sourceId = PluginId("source-1"),
            accessMode = AccessMode.REMOTE,
            localFingerprint = null,
            role = AttemptRole.PRIMARY,
        )
        val hedge = RouteAttempt(
            attemptId = "attempt-1",
            releaseId = ChapterReleaseId("release-2"),
            sourceId = PluginId("source-2"),
            accessMode = AccessMode.REMOTE,
            localFingerprint = null,
            role = AttemptRole.HEDGE,
        )
        val directive = HedgeDirective.Launch(hedge, 650L)
        val trace = emptyTrace(
            planRevision = ReaderPlanRevision(7),
            chapterGraphRevision = ReaderChapterGraphRevision(3),
            policyVersion = ReaderPolicyVersion.READER_POLICY_V1,
            decisionReason = DecisionReason.TOP_RANKED_NO_INCUMBENT,
        ).copy(
            routeConstruction = listOf(primary, hedge),
            hedgeDirective = directive,
        )
        val decision = ReaderRouteDecision(
            hesContractVersion = HesContractVersion.HES_V1,
            algorithmVersion = ReaderRoutingAlgorithmVersion.READER_ROUTING_V1,
            policyVersion = ReaderPolicyVersion.READER_POLICY_V1,
            planRevision = ReaderPlanRevision(7),
            competitiveSet = CompetitiveSet(primary = primary, hedge = hedge),
            hedgeDirective = directive,
            recoveryChain = emptyList(),
            rejections = emptyList(),
            trace = trace,
            confidence = BasisPoints(8_500),
            reason = DecisionReason.TOP_RANKED_NO_INCUMBENT,
        )

        assertEquals(directive, decision.hedgeDirective)
    }

    @Test
    fun halfOpenProbePermissionCannotExistOnAClosedCircuit() {
        assertFailsWith<IllegalArgumentException> {
            SourceHealthSnapshot(
                key = SourceOperationKey(PluginId("source-1")),
                state = SourceHealthState(circuitState = CircuitState.CLOSED),
                origin = SourceHealthOrigin.STARTUP_NEUTRAL,
                halfOpenProbePermitted = true,
            )
        }
    }

    private fun emptyTrace(
        planRevision: ReaderPlanRevision,
        chapterGraphRevision: ReaderChapterGraphRevision,
        policyVersion: ReaderPolicyVersion,
        decisionReason: DecisionReason,
    ) = ReaderDecisionTrace(
        hesContractVersion = HesContractVersion.HES_V1,
        algorithmVersion = ReaderRoutingAlgorithmVersion.READER_ROUTING_V1,
        policyVersion = policyVersion,
        planRevision = planRevision,
        chapterGraphRevision = chapterGraphRevision,
        canonicalCandidateIds = emptyList(),
        rejections = emptyList(),
        diagnostics = emptyList(),
        candidateEvaluations = emptyList(),
        stableRanking = emptyList(),
        incumbentReleaseId = null,
        incumbentKind = IncumbentKind.NONE,
        rawChallengerReleaseId = null,
        switchAdvantage = null,
        requiredHysteresisThreshold = null,
        finalWinnerReleaseId = null,
        routeConstruction = emptyList(),
        hedgeDirective = HedgeDirective.Omitted(HedgeOmissionReason.NOT_ELIGIBLE),
        finalDecisionReason = decisionReason,
        healthOrigins = emptyList(),
    )
}
