package app.openstory.artwork.pipeline

import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.policy.ArtworkPolicy
import app.openstory.artwork.request.ArtworkRequestIdentity
import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.common.execution.WorkAdmissionResult
import app.openstory.common.execution.WorkPriority
import app.openstory.common.execution.WorkResource
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope

data class ArtworkWorkKey(
    val request: ArtworkRequestIdentity,
    val policy: ArtworkPolicy?,
    val decodeSizeKey: String,
)

internal class ArtworkPipelineCoordinator(
    scope: CoroutineScope,
    private val admission: ProcessWorkAdmission,
    private val onActiveDecodeJobsChanged: (Int) -> Unit = {},
) : AutoCloseable {
    private val inFlight = ArtworkInFlight<ArtworkWorkKey, Any?>(scope)
    private val activeDecodeJobs = AtomicInteger()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> run(key: ArtworkWorkKey, block: suspend () -> T): T =
        inFlight.await(key) {
            val admittedBlock: suspend () -> T = {
                onActiveDecodeJobsChanged(activeDecodeJobs.incrementAndGet())
                try {
                    block()
                } finally {
                    onActiveDecodeJobsChanged(activeDecodeJobs.decrementAndGet())
                }
            }
            when (val result = admission.run(WorkResource.DECODE, WorkPriority.VISIBLE_ARTWORK, admittedBlock)) {
                is WorkAdmissionResult.Completed -> result.value
                is WorkAdmissionResult.Rejected -> throw ArtworkFailureException(ArtworkFailureReason.SATURATED)
            }
        } as T

    override fun close() = inFlight.close()
}
