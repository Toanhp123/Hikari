package app.openstory.build

import kotlin.test.Test
import kotlin.test.assertEquals

class FoundationConventionPluginTest {
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
