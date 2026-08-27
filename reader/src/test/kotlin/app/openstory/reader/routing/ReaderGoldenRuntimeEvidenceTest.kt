package app.openstory.reader.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile/runtime link for the G01-G26 registry entries that intentionally stay in focused Reader
 * runtime suites. This does not rerun those slower fixtures; it makes the evidence mapping fail if a
 * referenced test class or method is removed/renamed without updating the HES-v1 golden contract.
 */
class ReaderGoldenRuntimeEvidenceTest {
    @Test
    fun runtimeGoldenEvidenceTargetsExist() {
        val evidence = mapOf(
            "G06_EXPLICIT_RELEASE_FAILURE_FALLS_BACK" to listOf(
                Evidence(ReaderRouteExecutorAdaptiveTest::class.java, "explicitReleaseFailuresPreserveFallbackOrderAndFailureSurface"),
            ),
            "G08_STALE_PREFETCH_IS_REPLANNED" to listOf(
                Evidence(PrefetchCoordinatorTest::class.java, "graphChangeWithinSameNextChapterReplacesPrefetchWhenReleaseSetChanges"),
            ),
            "G11_HEDGE_REDUCES_TAIL_LATENCY" to listOf(
                Evidence(ReaderCompetitiveExecutionTest::class.java, "unresolved remote primary launches one hedge at the configured delay"),
            ),
            "G12_HEDGE_LOSER_NOT_PENALIZED" to listOf(
                Evidence(ReaderCompetitiveExecutionTest::class.java, "hedge winner cancellation does not penalize primary health"),
            ),
            "G13_NAVIGATION_CANCEL_NOT_PENALIZED" to listOf(
                Evidence(ReaderCompetitiveExecutionTest::class.java, "navigation cancellation blocks late success from health and cache effects"),
            ),
            "G14_CORRUPT_LOCAL_CONTENT_QUARANTINED" to listOf(
                Evidence(ReaderRouteExecutorAdaptiveTest::class.java, "exactCorruptionQuarantinesLocatorThenRemoteProbeRecovers"),
            ),
            "G15_STALE_GENERATION_CANNOT_COMMIT" to listOf(
                Evidence(ReaderCoordinatorModelTest::class.java, "new user intent supersedes an older completion without changing committed state"),
            ),
            "G16_STALE_REPLAN_CANNOT_COMMIT" to listOf(
                Evidence(ReaderCoordinatorModelTest::class.java, "hard invalidation rejects stale plan completion and commits only the revised plan"),
            ),
            "G21_RESUME_FINGERPRINT_CHANGE_ACCEPTS_VALID_REMOTE_WITHOUT_STALE_EXACT_OFFSET" to listOf(
                Evidence(ReaderRouteCoordinatorAdaptiveTest::class.java, "resumeFingerprintIsNotRemoteIntegrityExpectation"),
            ),
            "G23_REACTIVE_GRAPH_REMOVAL_INVALIDATES_ACTIVE_PLAN" to listOf(
                Evidence(ReaderChapterGraphInvalidationTest::class.java, "removingAPlannedReleaseHardInvalidatesActiveUncommittedPlan"),
            ),
            "G26_TWO_READER_SESSIONS_SHARE_HEALTH_BUT_NOT_EXECUTION_STATE" to listOf(
                Evidence(ReaderSourceHealthRegistryTest::class.java, "observationsRecordedByOneSessionAreVisibleToAnotherSnapshot"),
                Evidence(ReaderRouteSessionStateTest::class.java, "twoSessionsMayBothUseGenerationOneWithoutIdentityCollision"),
            ),
        )

        assertEquals(EXPECTED_RUNTIME_GOLDENS, evidence.keys)
        evidence.forEach { (golden, targets) ->
            assertTrue(targets.isNotEmpty(), "$golden must retain at least one focused runtime proof")
            targets.forEach { target ->
                assertTrue(
                    target.owner.declaredMethods.any { it.name == target.methodName },
                    "$golden runtime evidence disappeared: ${target.owner.simpleName}.${target.methodName}",
                )
            }
        }
    }

    private data class Evidence(
        val owner: Class<*>,
        val methodName: String,
    )

    private companion object {
        val EXPECTED_RUNTIME_GOLDENS = setOf(
            "G06_EXPLICIT_RELEASE_FAILURE_FALLS_BACK",
            "G08_STALE_PREFETCH_IS_REPLANNED",
            "G11_HEDGE_REDUCES_TAIL_LATENCY",
            "G12_HEDGE_LOSER_NOT_PENALIZED",
            "G13_NAVIGATION_CANCEL_NOT_PENALIZED",
            "G14_CORRUPT_LOCAL_CONTENT_QUARANTINED",
            "G15_STALE_GENERATION_CANNOT_COMMIT",
            "G16_STALE_REPLAN_CANNOT_COMMIT",
            "G21_RESUME_FINGERPRINT_CHANGE_ACCEPTS_VALID_REMOTE_WITHOUT_STALE_EXACT_OFFSET",
            "G23_REACTIVE_GRAPH_REMOVAL_INVALIDATES_ACTIVE_PLAN",
            "G26_TWO_READER_SESSIONS_SHARE_HEALTH_BUT_NOT_EXECUTION_STATE",
        )
    }
}
