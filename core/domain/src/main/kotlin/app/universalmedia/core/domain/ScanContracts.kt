package app.universalmedia.core.domain

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.RootId
import app.universalmedia.core.model.ScanRunId

/** First slice observes the full registered SAF root, with no exclusions, for provider-declared MP4. */
data class DeclaredScanScope(val rootId: RootId, val configGeneration: Long)

data class ScanRun(val id: ScanRunId, val scope: DeclaredScanScope, val startedAtEpochMs: Long)

enum class ScanOutcome { COMPLETE, PARTIAL, FAILED, CANCELLED, INTERRUPTED }

sealed interface ScanCoverage {
    data object Complete : ScanCoverage

    data object Unknown : ScanCoverage

    data class Incomplete(val gaps: List<CoverageGap>) : ScanCoverage {
        init {
            require(gaps.isNotEmpty())
        }
    }
}

data class ScanFinalization(val outcome: ScanOutcome, val coverage: ScanCoverage, val finalizedAtEpochMs: Long) {
    init {
        require((outcome == ScanOutcome.COMPLETE) == (coverage == ScanCoverage.Complete))
    }
}

data class SeenLocalAsset(val assetId: AssetId, val observation: LocalDocumentObservation)

interface ScanJournal {
    /** Allocates a fresh app-owned run ID; scheduler retry always starts a new attempt. */
    suspend fun beginRun(scope: DeclaredScanScope, startedAtEpochMs: Long): ScanRun

    /** Idempotent positive evidence only; reject observations outside this run's root/scope. */
    suspend fun recordPositiveBatch(runId: ScanRunId, observations: List<SeenLocalAsset>)

    /** Persist outcome/coverage only. No first-slice negative lifecycle transitions, even on COMPLETE. */
    suspend fun finalizeRun(runId: ScanRunId, finalization: ScanFinalization)
}
