package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderGoldenScenariosTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun namedGoldenRegistryFreezesG01ThroughG26AndRuntimeOwnership() {
        assertEquals(EXPECTED_GOLDENS, PURE_GOLDENS + RUNTIME_GOLDENS)
        assertEquals(26, EXPECTED_GOLDENS.size)
        assertEquals(EXPECTED_GOLDENS.size, EXPECTED_GOLDENS.toSet().size)
        assertTrue(RUNTIME_GOLDEN_EVIDENCE.keys.containsAll(RUNTIME_GOLDENS))
        assertTrue(RUNTIME_GOLDEN_EVIDENCE.values.none(String::isBlank))
    }

    @Test
    fun g01StickyHealthySourceAndG02TransientFailureDoNotSwitchBelowHysteresis() {
        val candidates = listOf(
            candidate("incumbent", "source-a", language = "en"),
            candidate("challenger", "source-b", language = "vi"),
        )
        val continuity = ReadingContinuity(committedSourceId = PluginId("source-a"))
        val policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en"))

        val healthy = engine.plan(snapshot(candidates, continuity = continuity), policy)
        assertEquals(ChapterReleaseId("incumbent"), healthy.competitiveSet.primary?.releaseId)
        assertEquals(DecisionReason.INCUMBENT_RETAINED_BY_HYSTERESIS, healthy.reason)

        val afterTransientFailure = engine.plan(
            snapshot(
                candidates,
                continuity = continuity,
                health = listOf(health("source-a", reliability = 8_000)),
            ),
            policy,
        )
        assertEquals(ChapterReleaseId("incumbent"), afterTransientFailure.competitiveSet.primary?.releaseId)
        assertEquals(DecisionReason.INCUMBENT_RETAINED_BY_HYSTERESIS, afterTransientFailure.reason)
    }

    @Test
    fun g03DegradedRemoteIncumbentCanBeHedgedByHealthyAlternate() {
        val candidates = listOf(
            candidate("incumbent", "source-a"),
            candidate("alternate", "source-b"),
        )
        val decision = engine.plan(
            snapshot(
                candidates = candidates,
                continuity = ReadingContinuity(committedSourceId = PluginId("source-a")),
                networkClass = ReaderNetworkClass.UNMETERED,
                health = listOf(
                    health(
                        source = "source-a",
                        reliability = 8_000,
                        latencySamples = listOf(1_500L, 1_600L, 1_700L),
                    ),
                    health(source = "source-b", reliability = 10_000),
                ),
            ),
            ReaderRoutingPolicy.v1(),
        )

        assertEquals(ChapterReleaseId("incumbent"), decision.competitiveSet.primary?.releaseId)
        val hedge = assertIs<HedgeDirective.Launch>(decision.hedgeDirective)
        assertEquals(ChapterReleaseId("alternate"), hedge.attempt.releaseId)
        assertEquals(AttemptRole.HEDGE, hedge.attempt.role)
    }

    @Test
    fun g04OpenRemoteWithoutLocalSwitchesButG22ExactLocalRemainsCompetitive() {
        val incumbent = candidate("incumbent", "source-a")
        val alternate = candidate("alternate", "source-b")
        val openHealth = health("source-a", circuitState = CircuitState.OPEN)

        val remoteOnly = engine.plan(
            snapshot(
                candidates = listOf(incumbent, alternate),
                continuity = ReadingContinuity(committedSourceId = PluginId("source-a")),
                health = listOf(openHealth),
            ),
            ReaderRoutingPolicy.v1(),
        )
        assertEquals(ChapterReleaseId("alternate"), remoteOnly.competitiveSet.primary?.releaseId)
        assertTrue(remoteOnly.rejections.any {
            it.releaseId == ChapterReleaseId("incumbent") && it.code == RejectionCode.REMOTE_CIRCUIT_OPEN
        })

        val localExact = engine.plan(
            snapshot(
                candidates = listOf(incumbent.copy(localAccess = CandidateLocalAccess.AvailableExact("fp-a")), alternate),
                continuity = ReadingContinuity(committedSourceId = PluginId("source-a")),
                health = listOf(openHealth),
            ),
            ReaderRoutingPolicy.v1(),
        )
        assertEquals(ChapterReleaseId("incumbent"), localExact.competitiveSet.primary?.releaseId)
        assertEquals(AccessMode.LOCAL, localExact.competitiveSet.primary?.accessMode)
        assertEquals("fp-a", localExact.competitiveSet.primary?.localFingerprint)
        assertFalse(localExact.trace.routeConstruction.any {
            it.releaseId == ChapterReleaseId("incumbent") && it.accessMode == AccessMode.REMOTE
        })
    }

    @Test
    fun g05ExplicitEligibleReleaseWinsAndG20CannotBypassHardRejection() {
        val allowed = candidate("allowed", "source-a", language = "vi")
        val explicit = candidate("explicit", "source-z", language = "en")

        val eligible = engine.plan(
            snapshot(listOf(allowed, explicit), explicitReleaseId = explicit.releaseId),
            ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en")),
        )
        assertEquals(explicit.releaseId, eligible.competitiveSet.primary?.releaseId)
        assertEquals(DecisionReason.EXPLICIT_ELIGIBLE_RELEASE, eligible.reason)

        val rejected = engine.plan(
            snapshot(listOf(allowed, explicit), explicitReleaseId = explicit.releaseId),
            ReaderRoutingPolicy.v1(
                languageOrder = listOf("vi"),
                languageFallbackMode = LanguageFallbackMode.STRICT_ALLOWED,
            ),
        )
        assertEquals(allowed.releaseId, rejected.competitiveSet.primary?.releaseId)
        assertTrue(rejected.rejections.any {
            it.releaseId == explicit.releaseId && it.code == RejectionCode.LANGUAGE_FORBIDDEN
        })
    }

    @Test
    fun g07PrefetchedLocalCopyCanWin() {
        val remote = candidate("remote", "source-a")
        val prefetched = candidate(
            "prefetched",
            "source-b",
            localAccess = CandidateLocalAccess.AvailableExact("prefetched-fp"),
        )

        val decision = engine.plan(snapshot(listOf(remote, prefetched)), ReaderRoutingPolicy.v1())

        assertEquals(prefetched.releaseId, decision.competitiveSet.primary?.releaseId)
        assertEquals(AccessMode.LOCAL, decision.competitiveSet.primary?.accessMode)
        assertEquals("prefetched-fp", decision.competitiveSet.primary?.localFingerprint)
    }

    @Test
    fun g09TrustedGroupContinuityCanCrossSource() {
        val trusted = candidate("trusted", "source-b", group = "team")
        val other = candidate("other", "source-a")

        val decision = engine.plan(
            snapshot(
                listOf(other, trusted),
                continuity = ReadingContinuity(committedSourceGroupKey = SourceGroupKey("team")),
            ),
            ReaderRoutingPolicy.v1(),
        )

        assertEquals(trusted.releaseId, decision.competitiveSet.primary?.releaseId)
        assertEquals(IncumbentKind.TRUSTED_SOURCE_GROUP, decision.trace.incumbentKind)
    }

    @Test
    fun g10StrictLanguageNeverSwitchesToUnlisted() {
        val allowed = candidate("allowed", "source-z", language = "vi", completeness = 0)
        val forbidden = candidate("forbidden", "source-a", language = "en", completeness = 10_000)

        val decision = engine.plan(
            snapshot(listOf(forbidden, allowed)),
            ReaderRoutingPolicy.v1(
                languageOrder = listOf("vi"),
                languageFallbackMode = LanguageFallbackMode.STRICT_ALLOWED,
            ),
        )

        assertEquals(allowed.releaseId, decision.competitiveSet.primary?.releaseId)
        assertTrue(decision.rejections.any {
            it.releaseId == forbidden.releaseId && it.code == RejectionCode.LANGUAGE_FORBIDDEN
        })
    }

    @Test
    fun g17AllRoutesExhaustedProducesNoExecutableAttempt() {
        val candidate = candidate(
            "unavailable",
            "source-a",
            remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE,
        )

        val decision = engine.plan(snapshot(listOf(candidate)), ReaderRoutingPolicy.v1())

        assertNull(decision.competitiveSet.primary)
        assertNull(decision.competitiveSet.hedge)
        assertTrue(decision.recoveryChain.isEmpty())
        assertEquals(DecisionReason.NO_ELIGIBLE_CANDIDATE, decision.reason)
    }

    @Test
    fun g18InputPermutationIsStable() {
        val candidates = listOf(
            candidate("release-c", "source-b", language = "en"),
            candidate("release-a", "source-a", language = "vi"),
            candidate("release-b", "source-a", language = "en"),
        )
        val policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en"))

        val expected = engine.plan(snapshot(candidates), policy)
        val reversed = engine.plan(snapshot(candidates.reversed()), policy)
        val rotated = engine.plan(snapshot(listOf(candidates[1], candidates[2], candidates[0])), policy)

        assertEquals(expected, reversed)
        assertEquals(expected, rotated)
    }

    @Test
    fun g19HalfOpenRequiresProbePermit() {
        val candidate = candidate("release", "source-a")
        val denied = engine.plan(
            snapshot(
                listOf(candidate),
                health = listOf(health("source-a", circuitState = CircuitState.HALF_OPEN, probe = false)),
            ),
            ReaderRoutingPolicy.v1(),
        )
        assertNull(denied.competitiveSet.primary)
        assertTrue(denied.rejections.any { it.code == RejectionCode.HALF_OPEN_PROBE_NOT_PERMITTED })

        val permitted = engine.plan(
            snapshot(
                listOf(candidate),
                health = listOf(health("source-a", circuitState = CircuitState.HALF_OPEN, probe = true)),
            ),
            ReaderRoutingPolicy.v1(),
        )
        assertEquals(candidate.releaseId, permitted.competitiveSet.primary?.releaseId)
    }

    @Test
    fun g24AuthCredentialFailureDoesNotOpenCircuit() {
        val reducer = SourceHealthReducer.v1()
        val initial = SourceHealthState()
        val afterAuth = reducer.reduce(
            initial,
            SourceObservation.AuthFailure.CredentialsUnavailable,
            nowEpochMillis = 1_000L,
            policy = HealthPolicy.v1(),
        )

        assertEquals(CircuitState.CLOSED, afterAuth.circuitState)
        assertEquals(BasisPoints(10_000), afterAuth.successEwmaBasisPoints)
        assertEquals(0, afterAuth.consecutivePenalizingFailures)
    }

    @Test
    fun g25AutomaticCacheLocatorSelectionIsDeterministic() {
        val candidates = listOf(
            candidate("release-b", "source-b", localAccess = CandidateLocalAccess.AvailableExact("fp-b")),
            candidate("release-a", "source-a", localAccess = CandidateLocalAccess.AvailableExact("fp-a")),
        )

        val forward = engine.plan(snapshot(candidates), ReaderRoutingPolicy.v1())
        val reverse = engine.plan(snapshot(candidates.reversed()), ReaderRoutingPolicy.v1())

        assertEquals(forward, reverse)
        assertEquals("fp-a", forward.competitiveSet.primary?.localFingerprint)
    }

    private fun snapshot(
        candidates: List<RoutingCandidate>,
        continuity: ReadingContinuity = ReadingContinuity(),
        networkClass: ReaderNetworkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId: ChapterReleaseId? = null,
        health: List<SourceHealthSnapshot> = emptyList(),
        routingIntent: RoutingIntent = RoutingIntent.FOREGROUND,
    ): ReaderRoutingSnapshot = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter"),
        chapterGraphRevision = ReaderChapterGraphRevision(1),
        planRevision = ReaderPlanRevision(1),
        routingIntent = routingIntent,
        candidates = candidates,
        sourceHealth = health,
        continuity = continuity,
        networkClass = networkClass,
        explicitReleaseId = explicitReleaseId,
        nowEpochMillis = 10_000L,
    )

    private fun candidate(
        id: String,
        source: String,
        language: String = "vi",
        group: String? = null,
        completeness: Int = 10_000,
        publishedAt: Long? = 1_000L,
        remoteAccess: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess: CandidateLocalAccess = CandidateLocalAccess.Miss,
    ): RoutingCandidate = RoutingCandidate(
        releaseId = ChapterReleaseId(id),
        sourceId = PluginId(source),
        languageTag = language,
        sourceGroupKey = group?.let(::SourceGroupKey),
        publishedAtEpochMillis = publishedAt,
        completeness = BasisPoints(completeness),
        remoteAccess = remoteAccess,
        localAccess = localAccess,
    )

    private fun health(
        source: String,
        reliability: Int = 10_000,
        latencySamples: List<Long> = emptyList(),
        circuitState: CircuitState = CircuitState.CLOSED,
        probe: Boolean = false,
    ): SourceHealthSnapshot {
        val open = circuitState != CircuitState.CLOSED
        return SourceHealthSnapshot(
            key = SourceOperationKey(PluginId(source)),
            state = SourceHealthState(
                circuitState = circuitState,
                successEwmaBasisPoints = BasisPoints(reliability),
                recentLatencySamplesMillis = latencySamples,
                openCount = if (open) 1 else 0,
                openedAtEpochMillis = if (open) 1_000L else null,
                nextProbeAtEpochMillis = if (open) 31_000L else null,
            ),
            origin = SourceHealthOrigin.PROCESS_OBSERVED,
            halfOpenProbePermitted = probe,
        )
    }

    private companion object {
        val EXPECTED_GOLDENS = (1..26).map { index ->
            when (index) {
                1 -> "G01_STICKY_HEALTHY_SOURCE"
                2 -> "G02_TRANSIENT_FAILURE_DOES_NOT_SWITCH"
                3 -> "G03_DEGRADED_SOURCE_HEDGED"
                4 -> "G04_OPEN_REMOTE_SOURCE_WITHOUT_LOCAL_SWITCHES"
                5 -> "G05_EXPLICIT_ELIGIBLE_RELEASE_WINS"
                6 -> "G06_EXPLICIT_RELEASE_FAILURE_FALLS_BACK"
                7 -> "G07_PREFETCHED_LOCAL_COPY_CAN_WIN"
                8 -> "G08_STALE_PREFETCH_IS_REPLANNED"
                9 -> "G09_TRUSTED_GROUP_CONTINUITY_ACROSS_SOURCE"
                10 -> "G10_STRICT_LANGUAGE_NEVER_SWITCHES_TO_UNLISTED"
                11 -> "G11_HEDGE_REDUCES_TAIL_LATENCY"
                12 -> "G12_HEDGE_LOSER_NOT_PENALIZED"
                13 -> "G13_NAVIGATION_CANCEL_NOT_PENALIZED"
                14 -> "G14_CORRUPT_LOCAL_CONTENT_QUARANTINED"
                15 -> "G15_STALE_GENERATION_CANNOT_COMMIT"
                16 -> "G16_STALE_REPLAN_CANNOT_COMMIT"
                17 -> "G17_ALL_ROUTES_EXHAUSTED"
                18 -> "G18_INPUT_PERMUTATION_STABLE"
                19 -> "G19_HALF_OPEN_REQUIRES_PROBE_PERMIT"
                20 -> "G20_USER_OVERRIDE_CANNOT_BYPASS_HARD_REJECTION"
                21 -> "G21_RESUME_FINGERPRINT_CHANGE_ACCEPTS_VALID_REMOTE_WITHOUT_STALE_EXACT_OFFSET"
                22 -> "G22_OPEN_REMOTE_SOURCE_WITH_EXACT_LOCAL_COPY_REMAINS_LOCALLY_COMPETITIVE"
                23 -> "G23_REACTIVE_GRAPH_REMOVAL_INVALIDATES_ACTIVE_PLAN"
                24 -> "G24_AUTH_CREDENTIAL_FAILURE_DOES_NOT_OPEN_SOURCE_CIRCUIT"
                25 -> "G25_AUTOMATIC_CACHE_LOCATOR_SELECTION_IS_DETERMINISTIC"
                26 -> "G26_TWO_READER_SESSIONS_SHARE_HEALTH_BUT_NOT_EXECUTION_STATE"
                else -> error("unreachable")
            }
        }.toSet()

        val PURE_GOLDENS = setOf(
            "G01_STICKY_HEALTHY_SOURCE",
            "G02_TRANSIENT_FAILURE_DOES_NOT_SWITCH",
            "G03_DEGRADED_SOURCE_HEDGED",
            "G04_OPEN_REMOTE_SOURCE_WITHOUT_LOCAL_SWITCHES",
            "G05_EXPLICIT_ELIGIBLE_RELEASE_WINS",
            "G07_PREFETCHED_LOCAL_COPY_CAN_WIN",
            "G09_TRUSTED_GROUP_CONTINUITY_ACROSS_SOURCE",
            "G10_STRICT_LANGUAGE_NEVER_SWITCHES_TO_UNLISTED",
            "G17_ALL_ROUTES_EXHAUSTED",
            "G18_INPUT_PERMUTATION_STABLE",
            "G19_HALF_OPEN_REQUIRES_PROBE_PERMIT",
            "G20_USER_OVERRIDE_CANNOT_BYPASS_HARD_REJECTION",
            "G22_OPEN_REMOTE_SOURCE_WITH_EXACT_LOCAL_COPY_REMAINS_LOCALLY_COMPETITIVE",
            "G24_AUTH_CREDENTIAL_FAILURE_DOES_NOT_OPEN_SOURCE_CIRCUIT",
            "G25_AUTOMATIC_CACHE_LOCATOR_SELECTION_IS_DETERMINISTIC",
        )

        val RUNTIME_GOLDEN_EVIDENCE = mapOf(
            "G06_EXPLICIT_RELEASE_FAILURE_FALLS_BACK" to
                "ReaderRouteExecutorAdaptiveTest.explicitReleaseFailuresPreserveFallbackOrderAndFailureSurface",
            "G08_STALE_PREFETCH_IS_REPLANNED" to
                "PrefetchCoordinatorTest.graphChangeWithinSameNextChapterReplacesPrefetchWhenReleaseSetChanges",
            "G11_HEDGE_REDUCES_TAIL_LATENCY" to
                "ReaderCompetitiveExecutionTest.unresolved remote primary launches one hedge at the configured delay",
            "G12_HEDGE_LOSER_NOT_PENALIZED" to
                "ReaderCompetitiveExecutionTest.hedge winner cancellation does not penalize primary health",
            "G13_NAVIGATION_CANCEL_NOT_PENALIZED" to
                "ReaderCompetitiveExecutionTest.navigation cancellation blocks late success from health and cache effects",
            "G14_CORRUPT_LOCAL_CONTENT_QUARANTINED" to
                "ReaderRouteExecutorAdaptiveTest.exactCorruptionQuarantinesLocatorThenRemoteProbeRecovers",
            "G15_STALE_GENERATION_CANNOT_COMMIT" to
                "ReaderCoordinatorModelTest.new user intent supersedes an older completion without changing committed state",
            "G16_STALE_REPLAN_CANNOT_COMMIT" to
                "ReaderCoordinatorModelTest.hard invalidation rejects stale plan completion and commits only the revised plan",
            "G21_RESUME_FINGERPRINT_CHANGE_ACCEPTS_VALID_REMOTE_WITHOUT_STALE_EXACT_OFFSET" to
                "ReaderRouteCoordinatorAdaptiveTest.resumeFingerprintIsNotRemoteIntegrityExpectation",
            "G23_REACTIVE_GRAPH_REMOVAL_INVALIDATES_ACTIVE_PLAN" to
                "ReaderChapterGraphInvalidationTest.removingAPlannedReleaseHardInvalidatesActiveUncommittedPlan",
            "G26_TWO_READER_SESSIONS_SHARE_HEALTH_BUT_NOT_EXECUTION_STATE" to
                "ReaderSourceHealthRegistryTest.observationsRecordedByOneSessionAreVisibleToAnotherSnapshot + ReaderRouteSessionStateTest.twoSessionsMayBothUseGenerationOneWithoutIdentityCollision",
        )
        val RUNTIME_GOLDENS = RUNTIME_GOLDEN_EVIDENCE.keys
    }
}
