package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.RecoveryScope
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderSourceFailureClassifierTest {
    private val releaseId = ChapterReleaseId("release")
    private val sourceId = PluginId("source")

    @Test
    fun exactCurrentCodesHaveLockedSemanticClassification() {
        val cases = listOf(
            Case("plugin.execution_timeout", SourceObservation.TransportFailure.Timeout::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("plugin.http_request_failed", SourceObservation.TransportFailure.Connection::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("plugin.http_read_failed", SourceObservation.TransportFailure.Connection::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("plugin.auth_unavailable", SourceObservation.AuthFailure.CredentialsUnavailable::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.http_credentials_failed", SourceObservation.AuthFailure.CredentialsUnavailable::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.disabled", SourceObservation.SourceStateFailure.DisabledOrNotInstalled::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.not_installed", SourceObservation.SourceStateFailure.DisabledOrNotInstalled::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.operation_unavailable", SourceObservation.SourceStateFailure.OperationUnavailable::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.http_domain_denied", SourceObservation.PluginPolicyFailure.ConfigurationOrCapability::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.capability_denied", SourceObservation.PluginPolicyFailure.ConfigurationOrCapability::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("plugin.http_managed_header_collision", SourceObservation.PluginPolicyFailure.ConfigurationOrCapability::class, RecoveryScope.SOURCE_SCOPED, false),
            Case("reader.document_empty", SourceObservation.ContentFailure.EmptyDocument::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("reader.document_too_large", SourceObservation.ContentFailure.InvalidDocument::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("reader.document_title_invalid", SourceObservation.ContentFailure.InvalidDocument::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("reader.document_block_invalid", SourceObservation.ContentFailure.InvalidDocument::class, RecoveryScope.SOURCE_SCOPED, true),
            Case("reader.source_payload_invalid", SourceObservation.ContentFailure.InvalidDocument::class, RecoveryScope.SOURCE_SCOPED, true),
        )

        cases.forEach { case ->
            val failure = ReaderSourceFailureClassifier.classifyRemote(
                releaseId = releaseId,
                sourceId = sourceId,
                code = case.code,
                retryable = case.code.contains("request_failed") || case.code.contains("read_failed") || case.code.contains("timeout"),
                attemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
            )
            assertEquals(case.observationClass, failure.observation::class, case.code)
            assertEquals(case.scope, failure.recoveryScope, case.code)
            assertEquals(case.penalizing, failure.penalizesSourceHealth, case.code)
            assertEquals(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT, failure.remoteAttemptKind, case.code)
        }
    }

    @Test
    fun policyPackageSandboxAndOutputCodesAreExactNonHeuristicEntries() {
        val policyCodes = setOf(
            "plugin.http_https_required",
            "plugin.http_url_invalid",
            "plugin.http_request_too_large",
            "plugin.http_request_budget_exceeded",
            "plugin.http_redirect_budget_exceeded",
            "plugin.http_redirect_invalid",
            "plugin.http_response_too_large",
            "plugin.http_compressed_response_too_large",
            "plugin.capability_payload_invalid",
            "plugin.bridge_message_invalid",
            "plugin.bridge_message_too_large",
            "plugin.html_document_too_large",
            "plugin.html_selector_invalid",
            "plugin.html_result_budget_invalid",
            "plugin.html_query_invalid",
            "plugin.log_event_invalid",
            "plugin.manifest_invalid",
            "plugin.package_entry_missing",
            "plugin.package_entry_denied",
            "plugin.package_path_invalid",
            "plugin.package_version_conflict",
            "plugin.package_checksum_mismatch",
            "plugin.package_provenance_mismatch",
            "plugin.package_layout_invalid",
            "plugin.package_invalid",
            "plugin.update_needs_review",
            "plugin.activation_failed",
        )

        policyCodes.forEach { code ->
            assertTrue(code in ReaderSourceFailureClassifier.knownCodes, code)
        }
        assertTrue("plugin.javascript_sandbox_unavailable" in ReaderSourceFailureClassifier.knownCodes)
        assertTrue("plugin.javascript_sandbox_unsupported" in ReaderSourceFailureClassifier.knownCodes)
        assertTrue("plugin.output_invalid" in ReaderSourceFailureClassifier.knownCodes)
        assertTrue("plugin.output_too_large" in ReaderSourceFailureClassifier.knownCodes)
        assertTrue("protocol.invalid_payload" in ReaderSourceFailureClassifier.knownCodes)
    }

    @Test
    fun sourceFailedAndUnknownFallbackRequireProvenRemoteOriginBeforePenalizing() {
        val proven = ReaderSourceFailureClassifier.classifyRemote(
            releaseId,
            sourceId,
            "reader.source_failed",
            retryable = true,
            sourceOriginProven = true,
        )
        assertIs<SourceObservation.TransportFailure.Connection>(proven.observation)
        assertEquals(RecoveryScope.SOURCE_SCOPED, proven.recoveryScope)
        assertTrue(proven.penalizesSourceHealth)

        val unproven = ReaderSourceFailureClassifier.classifyRemote(
            releaseId,
            sourceId,
            "reader.source_failed",
            retryable = true,
            sourceOriginProven = false,
        )
        assertIs<SourceObservation.RuntimeFailure.Unexpected>(unproven.observation)
        assertEquals(RecoveryScope.CLIENT_SCOPED, unproven.recoveryScope)
        assertFalse(unproven.penalizesSourceHealth)

        val unknownRetryable = ReaderSourceFailureClassifier.classifyRemote(
            releaseId,
            sourceId,
            "third.party.retryable",
            retryable = true,
            sourceOriginProven = true,
        )
        assertIs<SourceObservation.TransportFailure.Connection>(unknownRetryable.observation)

        val unknownInternal = ReaderSourceFailureClassifier.classifyRemote(
            releaseId,
            sourceId,
            "third.party.internal",
            retryable = false,
            sourceOriginProven = true,
        )
        assertIs<SourceObservation.RuntimeFailure.Unexpected>(unknownInternal.observation)
        assertEquals(RecoveryScope.CLIENT_SCOPED, unknownInternal.recoveryScope)
    }

    @Test
    fun halfOpenAttemptOriginIsPreservedOnTypedFailure() {
        val failure = ReaderSourceFailureClassifier.classifyRemote(
            releaseId,
            sourceId,
            "plugin.execution_timeout",
            retryable = true,
            attemptKind = RemoteAttemptKind.HALF_OPEN_PROBE,
        )

        assertEquals(RemoteAttemptKind.HALF_OPEN_PROBE, failure.remoteAttemptKind)
        assertEquals(
            RemoteAttemptKind.HALF_OPEN_PROBE,
            assertIs<SourceObservation.TransportFailure.Timeout>(failure.observation).kind,
        )
    }

    private data class Case(
        val code: String,
        val observationClass: kotlin.reflect.KClass<out SourceObservation>,
        val scope: RecoveryScope,
        val penalizing: Boolean,
    )
}
