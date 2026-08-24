package app.openstory.catalog.diagnostics

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.identity.SourceKey
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanonicalDiagnosticsTest {
    @Test
    fun sinkFailureNeverEscapesToCanonicalPolicyCaller() {
        val diagnostics = CanonicalDiagnostics(
            CanonicalDiagnosticsSink { error("diagnostic sink failed") },
        )

        diagnostics.record(trace())
    }

    @Test
    fun emittedTraceIsBoundedAndDropsUnsafeFreeFormPayloads() {
        val recorded = mutableListOf<CanonicalDecisionTrace>()
        val diagnostics = CanonicalDiagnostics(recorded::add)
        val secretDescription = "private description body with spaces and https://private.example/query?q=secret"
        val rawPayload = "{\"progress\":123,\"mapping\":\"private\"}"

        diagnostics.record(
            CanonicalDecisionTrace(
                kind = CanonicalTraceKind.FIELD_FUSION,
                storyIds = (1..40).mapTo(linkedSetOf()) { StoryId("story:$it") },
                sourceKeys = (1..40).mapTo(linkedSetOf()) {
                    SourceKey(PluginId("provider.$it"), "source:$it")
                },
                policyVersions = (1..20).associate { "policy.$it" to it },
                reasonCodes = (1..20).map { "reason.$it" } + secretDescription,
                evidenceFingerprints = (1..20).map { "fingerprint:$it" } + rawPayload,
                field = CanonicalFieldKey.DESCRIPTION,
            ),
        )

        val bounded = recorded.single()
        assertEquals(16, bounded.storyIds.size)
        assertEquals(16, bounded.sourceKeys.size)
        assertEquals(8, bounded.policyVersions.size)
        assertEquals(16, bounded.reasonCodes.size)
        assertEquals(16, bounded.evidenceFingerprints.size)
        assertEquals(CanonicalFieldKey.DESCRIPTION, bounded.field)
        assertFalse(bounded.reasonCodes.any { secretDescription in it })
        assertFalse(bounded.evidenceFingerprints.any { rawPayload in it })
        assertTrue(bounded.reasonCodes.all { ' ' !in it })
    }

    @Test
    fun oversizedOrWhitespaceIdentifiersAreDroppedBeforeTheyReachSink() {
        val recorded = mutableListOf<CanonicalDecisionTrace>()
        val diagnostics = CanonicalDiagnostics(recorded::add)

        diagnostics.record(
            trace().copy(
                storyIds = setOf(StoryId("story:" + "x".repeat(200))),
                sourceKeys = setOf(
                    SourceKey(PluginId("provider.safe"), "source " + "x".repeat(20)),
                    SourceKey(PluginId("provider." + "x".repeat(200)), "source:safe"),
                ),
            ),
        )

        val bounded = recorded.single()
        assertTrue(bounded.storyIds.isEmpty())
        assertTrue(bounded.sourceKeys.isEmpty())
    }

    @Test
    fun noOpDiagnosticsAcceptsStructuredTraceWithoutPersistence() {
        CanonicalDiagnostics(NoOpCanonicalDiagnosticsSink).record(trace())
    }

    private fun trace() = CanonicalDecisionTrace(
        kind = CanonicalTraceKind.RECONCILIATION,
        storyIds = setOf(StoryId("story:a"), StoryId("story:b")),
        sourceKeys = setOf(SourceKey(PluginId("provider.a"), "source:a")),
        policyVersions = mapOf("reconciliation" to 1),
        reasonCodes = listOf("TITLE_EXACT", "AUTHOR_MISSING"),
        evidenceFingerprints = listOf("fingerprint:abc"),
    )
}
