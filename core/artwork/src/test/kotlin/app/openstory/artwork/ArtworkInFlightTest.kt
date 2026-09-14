package app.openstory.artwork

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkInFlightTest {
    @Test
    fun equivalentConsumersShareWorkUntilTheLastConsumerLeaves() = runTest {
        val workStarted = CompletableDeferred<Unit>()
        val releaseWork = CompletableDeferred<String>()
        val executions = AtomicInteger()
        val inFlight = ArtworkInFlight<ArtworkWorkKey, String>(backgroundScope)
        val key = workKey()

        val first = async {
            inFlight.await(key) {
                executions.incrementAndGet()
                workStarted.complete(Unit)
                releaseWork.await()
            }
        }
        workStarted.await()
        val second = async {
            inFlight.await(key) {
                executions.incrementAndGet()
                error("equivalent work must not start twice")
            }
        }
        yield()

        first.cancelAndJoin()
        assertFalse(releaseWork.isCancelled)
        assertEquals(1, executions.get())

        releaseWork.complete("decoded")
        assertEquals("decoded", second.await())
        assertEquals(0, inFlight.activeCount())
    }

    @Test
    fun finalConsumerCancellationCancelsAndReleasesSharedWork() = runTest {
        val workStarted = CompletableDeferred<Unit>()
        val workCancelled = CompletableDeferred<Unit>()
        val inFlight = ArtworkInFlight<ArtworkWorkKey, Unit>(backgroundScope)

        val consumer = async {
            inFlight.await(workKey()) {
                workStarted.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    workCancelled.complete(Unit)
                }
            }
        }
        workStarted.await()

        consumer.cancelAndJoin()
        workCancelled.await()

        assertEquals(0, inFlight.activeCount())
    }

    @Test
    fun requestVaryAndPolicyIdentityPreventUnsafeCoalescing() = runTest {
        val executions = AtomicInteger()
        val inFlight = ArtworkInFlight<ArtworkWorkKey, Int>(backgroundScope)
        val release = CompletableDeferred<Unit>()
        val firstKey = workKey()
        val variedKey = workKey(varyKey = "auth:user-b")
        val policyKey = workKey(policy = policy(allowedHosts = setOf("other.example")))

        val consumers = listOf(firstKey, variedKey, policyKey).map { key ->
            async {
                inFlight.await(key) {
                    val result = executions.incrementAndGet()
                    release.await()
                    result
                }
            }
        }
        while (executions.get() < 3) yield()
        assertEquals(3, executions.get())

        release.complete(Unit)
        assertEquals(setOf(1, 2, 3), consumers.map { it.await() }.toSet())
        assertTrue(inFlight.activeCount() == 0)
    }

    private fun workKey(
        varyKey: String = "public",
        policy: ArtworkPolicy = policy(),
    ) = ArtworkWorkKey(
        request = ArtworkRequestIdentity(
            authority = AUTHORITY,
            stableAssetKey = "asset:1",
            locator = "https://images.example/cover.webp",
            transformKey = "crop:240x360",
            varyKey = varyKey,
        ),
        policy = policy,
        decodeSizeKey = "240x360",
    )

    private fun policy(allowedHosts: Set<String> = setOf("images.example")) = ArtworkPolicy(
        authority = AUTHORITY,
        allowedHttpsHosts = allowedHosts,
    )

    private companion object {
        val AUTHORITY = ArtworkAuthorityKey("catalog:mangaupdates")
    }
}
