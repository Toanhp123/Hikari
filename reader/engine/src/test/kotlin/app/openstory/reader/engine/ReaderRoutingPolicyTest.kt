package app.openstory.reader.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReaderRoutingPolicyTest {
    @Test
    fun basisPointsRejectOutOfRange() {
        assertFailsWith<IllegalArgumentException> { BasisPoints(-1) }
        assertFailsWith<IllegalArgumentException> { BasisPoints(10_001) }
        assertEquals(0, BasisPoints(0).value)
        assertEquals(10_000, BasisPoints(10_000).value)
    }

    @Test
    fun revisionsAndSourceGroupRejectInvalidValues() {
        assertFailsWith<IllegalArgumentException> { ReaderPlanRevision(-1) }
        assertFailsWith<IllegalArgumentException> { ReaderChapterGraphRevision(-1) }
        assertFailsWith<IllegalArgumentException> { SourceGroupKey("   ") }
    }

    @Test
    fun defaultWeightsAndPolicyMatchR2() {
        val policy = ReaderRoutingPolicy.v1()

        assertEquals(10_000, policy.weights.total)
        assertEquals(BasisPoints(2_500), policy.weights.language)
        assertEquals(BasisPoints(2_500), policy.weights.continuity)
        assertEquals(BasisPoints(1_800), policy.weights.health)
        assertEquals(BasisPoints(1_000), policy.weights.reliability)
        assertEquals(BasisPoints(900), policy.weights.completeness)
        assertEquals(BasisPoints(700), policy.weights.latency)
        assertEquals(BasisPoints(300), policy.weights.freshness)
        assertEquals(BasisPoints(300), policy.weights.cacheUtility)
        assertEquals(LanguageFallbackMode.ORDERED_ALLOW, policy.languageFallbackMode)
        assertEquals(BasisPoints(800), policy.normalSwitchThreshold)
        assertEquals(BasisPoints(350), policy.degradedSwitchThreshold)
        assertTrue(policy.allowUnverifiedLocalAttempt)
        assertEquals(6, policy.maxRecoveryAttempts)
        assertEquals(4, policy.maxPlannedForegroundRemoteAttempts)
        assertEquals(650, policy.hedge.delayMillis)
        assertEquals(1_200, policy.hedge.primaryP95ThresholdMillis)
        assertEquals(3, policy.hedge.minimumLatencySamples)
        assertEquals(BasisPoints(8_000), policy.hedge.alternateMinimumRemoteAccessScore)
        assertEquals(BasisPoints(9_000), policy.hedge.alternateMinimumReliability)
        assertEquals(HesContractVersion.HES_V1, policy.hesContractVersion)
        assertEquals(ReaderRoutingAlgorithmVersion.READER_ROUTING_V1, policy.algorithmVersion)
        assertEquals(ReaderPolicyVersion.READER_POLICY_V1, policy.version)
    }

    @Test
    fun strictLanguageRequiresNonEmptyUniqueNormalizedTags() {
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(
                languageOrder = emptyList(),
                languageFallbackMode = LanguageFallbackMode.STRICT_ALLOWED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(languageOrder = listOf("VI", "vi"))
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(languageOrder = listOf("en", "  "))
        }
    }

    @Test
    fun languageOrderIsNormalizedAndCopied() {
        val mutable = mutableListOf(" VI_vn ", "EN-us")
        val policy = ReaderRoutingPolicy.v1(languageOrder = mutable)
        mutable.clear()

        assertEquals(listOf("vi-vn", "en-us"), policy.languageOrder)
        assertEquals("zh-hant", normalizeLanguageTag(" ZH_HANT "))
    }

    @Test
    fun zeroRemoteAccessWeightFailsFast() {
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingWeights(
                language = BasisPoints(10_000),
                continuity = BasisPoints(0),
                health = BasisPoints(0),
                reliability = BasisPoints(0),
                completeness = BasisPoints(0),
                latency = BasisPoints(0),
                freshness = BasisPoints(0),
                cacheUtility = BasisPoints(0),
            )
        }
    }

    @Test
    fun invalidWeightsAndBudgetsFailFast() {
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingWeights(language = BasisPoints(2_499))
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(maxRecoveryAttempts = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(maxRecoveryAttempts = 7)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(maxPlannedForegroundRemoteAttempts = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRoutingPolicy.v1(maxPlannedForegroundRemoteAttempts = 5)
        }
        assertFailsWith<IllegalArgumentException> {
            HedgePolicy(delayMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            HedgePolicy(primaryP95ThresholdMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            HedgePolicy(minimumLatencySamples = -1)
        }
    }
}
