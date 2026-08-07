package app.openstory.plugin.host.install

import app.openstory.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PluginVersionPolicyTest {
    private val policy = PluginVersionPolicy()

    @Test
    fun lowerVersionIsRejected() {
        val failure = assertIs<AppResult.Failure>(
            policy.validateInstall("1.5.0", "2.0.0"),
        )

        assertEquals("plugin.package_downgrade_denied", failure.error.code)
    }

    @Test
    fun equalAndHigherVersionsAreAccepted() {
        assertIs<AppResult.Success<Unit>>(policy.validateInstall("2.0.0", "2.0.0"))
        assertIs<AppResult.Success<Unit>>(policy.validateInstall("2.1.0", "2.0.0"))
    }

    @Test
    fun invalidCandidateOrActiveVersionIsRejected() {
        assertEquals(
            "plugin.package_version_invalid",
            assertIs<AppResult.Failure>(policy.validateInstall("invalid", null)).error.code,
        )
        assertEquals(
            "plugin.package_version_invalid",
            assertIs<AppResult.Failure>(policy.validateInstall("2.0.0", "invalid")).error.code,
        )
    }

    @Test
    fun semanticPreReleaseOrderingIsPreserved() {
        assertIs<AppResult.Success<Unit>>(
            policy.validateInstall("2.0.0", "2.0.0-beta.2"),
        )
        assertEquals(
            "plugin.package_downgrade_denied",
            assertIs<AppResult.Failure>(
                policy.validateInstall("2.0.0-beta.2", "2.0.0"),
            ).error.code,
        )
    }
}
