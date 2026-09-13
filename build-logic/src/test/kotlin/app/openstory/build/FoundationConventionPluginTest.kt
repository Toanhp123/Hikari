package app.openstory.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoundationConventionPluginTest {
    @Test
    fun benchmarkDiagnosticsProviderIsAdmittedOnlyForBenchmarkVariants() {
        val expected = setOf("app.openstory.benchmark.BenchmarkDiagnosticsProvider")

        assertEquals(expected, foundationAllowedManifestProviders("benchmarkRelease"))
        assertEquals(expected, foundationAllowedManifestProviders("nonMinifiedRelease"))
        assertTrue(foundationAllowedManifestProviders("debug").isEmpty())
        assertTrue(foundationAllowedManifestProviders("release").isEmpty())
    }

    @Test
    fun everyStep2AppVariantRetainsMergedManifestStartupVerification() {
        assertEquals(
            setOf(
                "verifyDebugMergedManifestStartup",
                "verifyReleaseMergedManifestStartup",
                "verifyBenchmarkReleaseMergedManifestStartup",
                "verifyNonMinifiedReleaseMergedManifestStartup",
            ),
            foundationManifestVerificationTaskNames(
                setOf("debug", "release", "benchmarkRelease", "nonMinifiedRelease"),
            ),
        )
    }
}
