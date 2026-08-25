package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.RecoveryScope
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation

/** Exact adapter for all currently Reader-reachable CONTENT_CHAPTER failure strings. */
internal object ReaderSourceFailureClassifier {
    private const val CONTEXTUAL_SOURCE_FAILED = "reader.source_failed"

    private enum class Semantic {
        TIMEOUT,
        CONNECTION,
        CREDENTIALS,
        SOURCE_DISABLED,
        OPERATION_UNAVAILABLE,
        PLUGIN_POLICY,
        CONTENT_EMPTY,
        CONTENT_INVALID,
        SOURCE_RUNTIME,
        CLIENT_RUNTIME,
    }

    private data class Entry(
        val semantic: Semantic,
        val recoveryScope: RecoveryScope,
    )

    private val exactEntries: Map<String, Entry> = buildMap {
        entry("plugin.execution_timeout", Semantic.TIMEOUT, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.http_request_failed", Semantic.CONNECTION, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.http_read_failed", Semantic.CONNECTION, RecoveryScope.SOURCE_SCOPED)

        entry("plugin.auth_unavailable", Semantic.CREDENTIALS, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.http_credentials_failed", Semantic.CREDENTIALS, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.disabled", Semantic.SOURCE_DISABLED, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.not_installed", Semantic.SOURCE_DISABLED, RecoveryScope.SOURCE_SCOPED)
        entry("reader.source_unavailable", Semantic.SOURCE_DISABLED, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.operation_unavailable", Semantic.OPERATION_UNAVAILABLE, RecoveryScope.SOURCE_SCOPED)

        // Capability / URL / bounded-I/O policy failures are configuration facts, not evidence that
        // the remote source itself is unreliable.
        listOf(
            "plugin.http_domain_denied",
            "plugin.capability_denied",
            "plugin.http_managed_header_collision",
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
        ).forEach { entry(it, Semantic.PLUGIN_POLICY, RecoveryScope.SOURCE_SCOPED) }

        // These represent host/runtime capability, not evidence against one source's health.
        entry("plugin.javascript_sandbox_unavailable", Semantic.CLIENT_RUNTIME, RecoveryScope.CLIENT_SCOPED)
        entry("plugin.javascript_sandbox_unsupported", Semantic.CLIENT_RUNTIME, RecoveryScope.CLIENT_SCOPED)

        // A plugin execution failure is source-scoped for recovery, but deliberately non-penalizing:
        // the current code carries no transport-vs-script evidence.
        entry("plugin.execution_failed", Semantic.SOURCE_RUNTIME, RecoveryScope.SOURCE_SCOPED)

        // Invalid materialized output is source-origin content evidence and may lower health.
        entry("plugin.output_invalid", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
        entry("plugin.output_too_large", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
        entry("protocol.invalid_payload", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
        entry("reader.source_payload_invalid", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
        entry("reader.document_empty", Semantic.CONTENT_EMPTY, RecoveryScope.SOURCE_SCOPED)
        entry("reader.document_too_large", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
        entry("reader.document_title_invalid", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
        entry("reader.document_block_invalid", Semantic.CONTENT_INVALID, RecoveryScope.SOURCE_SCOPED)
    }

    /** Includes contextual entries too; inventory equality prevents unreviewed code drift. */
    val knownCodes: Set<String> = (exactEntries.keys + CONTEXTUAL_SOURCE_FAILED).toSet()

    fun classifyRemote(
        releaseId: ChapterReleaseId,
        sourceId: PluginId,
        code: String,
        retryable: Boolean,
        attemptKind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        sourceOriginProven: Boolean = true,
    ): ReaderAttemptFailure {
        val contextual = if (code == CONTEXTUAL_SOURCE_FAILED) {
            if (retryable && sourceOriginProven) {
                Entry(Semantic.CONNECTION, RecoveryScope.SOURCE_SCOPED)
            } else {
                Entry(Semantic.CLIENT_RUNTIME, RecoveryScope.CLIENT_SCOPED)
            }
        } else {
            exactEntries[code]
        }
        val entry = contextual ?: if (retryable && sourceOriginProven) {
            // Compatibility fallback is allowed only at a proven remote invocation boundary.
            Entry(Semantic.CONNECTION, RecoveryScope.SOURCE_SCOPED)
        } else {
            Entry(Semantic.CLIENT_RUNTIME, RecoveryScope.CLIENT_SCOPED)
        }
        return ReaderAttemptFailure(
            releaseId = releaseId,
            sourceId = sourceId,
            accessMode = AccessMode.REMOTE,
            observation = entry.semantic.toObservation(attemptKind),
            recoveryScope = entry.recoveryScope,
            legacyCode = code,
            retryable = retryable,
            remoteAttemptKind = attemptKind,
        )
    }

    private fun Semantic.toObservation(kind: RemoteAttemptKind): SourceObservation = when (this) {
        Semantic.TIMEOUT -> SourceObservation.TransportFailure.Timeout(kind)
        Semantic.CONNECTION -> SourceObservation.TransportFailure.Connection(kind)
        Semantic.CREDENTIALS -> SourceObservation.AuthFailure.CredentialsUnavailable
        Semantic.SOURCE_DISABLED -> SourceObservation.SourceStateFailure.DisabledOrNotInstalled
        Semantic.OPERATION_UNAVAILABLE -> SourceObservation.SourceStateFailure.OperationUnavailable
        Semantic.PLUGIN_POLICY -> SourceObservation.PluginPolicyFailure.ConfigurationOrCapability
        Semantic.CONTENT_EMPTY -> SourceObservation.ContentFailure.EmptyDocument(kind)
        Semantic.CONTENT_INVALID -> SourceObservation.ContentFailure.InvalidDocument(kind)
        Semantic.SOURCE_RUNTIME,
        Semantic.CLIENT_RUNTIME,
        -> SourceObservation.RuntimeFailure.Unexpected
    }

    private fun MutableMap<String, Entry>.entry(
        code: String,
        semantic: Semantic,
        recoveryScope: RecoveryScope,
    ) {
        check(put(code, Entry(semantic, recoveryScope)) == null) { "Duplicate Reader failure code: $code" }
    }

}
