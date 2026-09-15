package app.openstory.artwork.pipeline

import app.openstory.artwork.ArtworkFailureException
import app.openstory.artwork.ArtworkFailureReason
import app.openstory.artwork.policy.ArtworkPolicy
import app.openstory.artwork.request.ArtworkAuthorityKey
import app.openstory.artwork.request.ArtworkRequestIdentity

import app.openstory.common.execution.ProcessWorkAdmission
import app.openstory.common.execution.WorkAdmissionResult
import app.openstory.common.execution.WorkPriority
import app.openstory.common.execution.WorkRejectionReason
import app.openstory.common.execution.WorkResource
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkPipelineCoordinatorTest {
    @Test
    fun equivalentWorkCoalescesBeforeVisibleDecodeAdmission() = runTest {
        val admission = RecordingAdmission()
        val activeCounts = mutableListOf<Int>()
        val coordinator = ArtworkPipelineCoordinator(backgroundScope, admission) { count -> activeCounts += count }
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger()
        val key = workKey()

        val first = async { coordinator.run(key) { executions.incrementAndGet(); release.await(); "image" } }
        while (executions.get() == 0) yield()
        val second = async { coordinator.run<String>(key) { error("duplicate pipeline") } }
        yield()

        assertEquals(1, executions.get())
        assertEquals(listOf(WorkResource.DECODE), admission.resources)
        assertEquals(listOf(WorkPriority.VISIBLE_ARTWORK), admission.priorities)
        assertEquals(listOf(1), activeCounts)

        release.complete(Unit)
        assertEquals("image", first.await())
        assertEquals("image", second.await())
        assertEquals(listOf(1, 0), activeCounts)
    }

    @Test
    fun saturatedDecodeAdmissionFailsWithoutRunningPipeline() = runTest {
        val coordinator = ArtworkPipelineCoordinator(
            scope = backgroundScope,
            admission = RejectingAdmission,
        )
        val executions = AtomicInteger()

        val failure = try {
            coordinator.run<Int>(workKey()) { executions.incrementAndGet() }
            error("Expected saturation")
        } catch (failure: ArtworkFailureException) {
            failure
        }

        assertEquals(ArtworkFailureReason.SATURATED, failure.reason)
        assertEquals(0, executions.get())
    }

    private fun workKey() = ArtworkWorkKey(
        request = ArtworkRequestIdentity(
            authority = ArtworkAuthorityKey("catalog:test"),
            stableAssetKey = "asset:1",
            locator = "https://images.example/cover",
            transformKey = "crop:240x360",
            varyKey = "public",
        ),
        policy = ArtworkPolicy(ArtworkAuthorityKey("catalog:test"), setOf("images.example")),
        decodeSizeKey = "240x360",
    )
}

private class RecordingAdmission : ProcessWorkAdmission {
    val resources = mutableListOf<WorkResource>()
    val priorities = mutableListOf<WorkPriority>()

    override suspend fun <T> run(
        resource: WorkResource,
        priority: WorkPriority,
        block: suspend () -> T,
    ): WorkAdmissionResult<T> {
        resources += resource
        priorities += priority
        return WorkAdmissionResult.Completed(block())
    }
}

private object RejectingAdmission : ProcessWorkAdmission {
    override suspend fun <T> run(
        resource: WorkResource,
        priority: WorkPriority,
        block: suspend () -> T,
    ): WorkAdmissionResult<T> = WorkAdmissionResult.Rejected(WorkRejectionReason.SATURATED)
}
