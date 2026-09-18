package app.universalmedia.ingestion.local

import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.LocalAccessFailure
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalMediaMaterializer
import app.universalmedia.core.domain.LocalTreeObservationSource
import app.universalmedia.core.domain.ScanCancellation
import app.universalmedia.core.domain.ScanCoverage
import app.universalmedia.core.domain.ScanFinalization
import app.universalmedia.core.domain.ScanJournal
import app.universalmedia.core.domain.ScanOutcome
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.domain.TraversalResult
import app.universalmedia.core.model.RootId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class LocalScanResult { COMPLETE, INCOMPLETE, FAILURE, RETRY, ROOT_NOT_FOUND }

class LocalRootScanRunner(
    private val roots: StorageRootStore,
    private val observations: LocalTreeObservationSource,
    private val journal: ScanJournal,
    private val materializer: LocalMediaMaterializer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        rootId: RootId,
        cancellation: ScanCancellation = ScanCancellation { false },
    ): LocalScanResult {
        checkCancellation(cancellation)
        val root = roots.get(rootId) ?: return LocalScanResult.ROOT_NOT_FOUND
        if (root.access != LocalAccessState.READABLE) return LocalScanResult.FAILURE
        val scope = DeclaredScanScope(root.id, root.configGeneration)
        val run = journal.beginRun(scope, clock())
        val traversal = try {
            observations.observe(root, scope, cancellation) { batch ->
                // Await each short transaction; the adapter bounds batches for backpressure.
                for (observation in batch) {
                    checkCancellation(cancellation)
                    require(observation.locator.rootId == root.id)
                    require(
                        observation.locator.providerAuthority == root.descriptor.providerAuthority,
                    )
                    if (observation.mimeType == "video/mp4") {
                        materializer.commitRecognizedLocalVideo(run.id, observation)
                    }
                }
            }.also {
                checkCancellation(cancellation)
                if (it == TraversalResult.Cancelled) throw CancellationException("Scan cancelled")
            }
        } catch (failure: Exception) {
            // Cleanup is best effort: death or a superseding run may prevent finalization.
            // A nonterminal run remains non-authoritative and beginRun interrupts it on recovery.
            withContext(NonCancellable) {
                try {
                    journal.finalizeRun(
                        run.id,
                        ScanFinalization(
                            if (failure is CancellationException) {
                                ScanOutcome.CANCELLED
                            } else {
                                ScanOutcome.FAILED
                            },
                            ScanCoverage.Unknown,
                            clock(),
                        ),
                    )
                } catch (cleanup: Exception) {
                    failure.addSuppressed(cleanup)
                }
            }
            throw failure
        }
        val finalization = when (traversal) {
            TraversalResult.Complete -> ScanFinalization(
                ScanOutcome.COMPLETE,
                ScanCoverage.Complete,
                clock(),
            )

            is TraversalResult.Incomplete -> ScanFinalization(
                ScanOutcome.PARTIAL,
                ScanCoverage.Incomplete(traversal.gaps),
                clock(),
            )

            is TraversalResult.Failed -> ScanFinalization(
                ScanOutcome.FAILED,
                ScanCoverage.Unknown,
                clock(),
            )

            TraversalResult.Cancelled -> error("Cancellation must propagate")
        }
        journal.finalizeRun(run.id, finalization)
        return when (traversal) {
            TraversalResult.Complete -> LocalScanResult.COMPLETE

            is TraversalResult.Incomplete -> LocalScanResult.INCOMPLETE

            is TraversalResult.Failed -> if (
                traversal.failure == LocalAccessFailure.TRANSIENT_PROVIDER_FAILURE
            ) {
                LocalScanResult.RETRY
            } else {
                LocalScanResult.FAILURE
            }

            TraversalResult.Cancelled -> error("Cancellation must propagate")
        }
    }

    private suspend fun checkCancellation(cancellation: ScanCancellation) {
        currentCoroutineContext().ensureActive()
        if (cancellation.isCancelled()) throw CancellationException("Scan cancelled")
    }
}
