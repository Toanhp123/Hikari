package app.openstory.reader.assets

import app.openstory.reader.routing.ReaderNetworkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderAssetPrefetchPlannerTest {
    private val planner = ReaderAssetPrefetchPlanner()

    @Test
    fun `current horizon is rolling and bounded by network class`() {
        val manifest = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 40)
        val viewport = viewport(manifest, leading = 10, trailing = 12, progress = 4_000)

        val offline = plan(manifest, viewport, ReaderNetworkState.OFFLINE)
        val metered = plan(manifest, viewport, ReaderNetworkState.METERED)
        val unknown = plan(manifest, viewport, ReaderNetworkState.UNKNOWN)
        val unmetered = plan(manifest, viewport, ReaderNetworkState.UNMETERED)

        assertEquals(listOf(10, 11, 12), offline.interactive.map { it.imageOrdinal })
        assertTrue(offline.currentAhead.isEmpty())
        assertEquals(listOf(13, 14), metered.currentAhead.map { it.imageOrdinal })
        assertEquals(listOf(13, 14, 15, 16), unknown.currentAhead.map { it.imageOrdinal })
        assertEquals(listOf(13, 14, 15, 16), unmetered.currentAhead.map { it.imageOrdinal })
    }

    @Test
    fun `backward movement rolls the horizon without queuing the chapter remainder`() {
        val manifest = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 2_000)
        val viewport = viewport(
            manifest = manifest,
            leading = 1_000,
            trailing = 1_002,
            direction = ReaderViewportDirection.BACKWARD,
        )

        val plan = plan(manifest, viewport, ReaderNetworkState.UNMETERED)

        assertEquals(listOf(999, 998, 997, 996), plan.currentAhead.map { it.imageOrdinal })
        assertEquals(4, plan.currentAhead.size)
    }

    @Test
    fun `unmetered transition frontier follows exact progress thresholds`() {
        val current = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 20)
        val next = assetManifest(sessionId = 1, chapter = "chapter-2", pageCount = 10)

        val below = plan(current, viewport(current, 15, 17, progress = 7_999), ReaderNetworkState.UNMETERED, next)
        val approaching = plan(
            current,
            viewport(current, 15, 17, progress = 8_000),
            ReaderNetworkState.UNMETERED,
            next,
        )
        val near = plan(current, viewport(current, 15, 17, progress = 9_000), ReaderNetworkState.UNMETERED, next)

        assertTrue(below.transition.isEmpty())
        assertEquals(listOf(0), approaching.transition.map { it.imageOrdinal })
        assertEquals(listOf(0, 1, 2, 3), near.transition.map { it.imageOrdinal })
    }

    @Test
    fun `transition speculation requires unmetered normal cache and prefetched manifest`() {
        val current = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 20)
        val next = assetManifest(sessionId = 1, chapter = "chapter-2", pageCount = 10)
        val viewport = viewport(current, 15, 17, progress = 9_500)

        assertTrue(plan(current, viewport, ReaderNetworkState.METERED, next).transition.isEmpty())
        assertTrue(plan(current, viewport, ReaderNetworkState.UNKNOWN, next).transition.isEmpty())
        assertTrue(plan(current, viewport, ReaderNetworkState.UNMETERED, null).transition.isEmpty())
        assertTrue(
            planner.plan(
                manifest = current,
                viewport = viewport,
                networkState = ReaderNetworkState.UNMETERED,
                cachePressure = ReaderAssetCachePressure.PRESSURED,
                prefetchedManifest = next,
            ).transition.isEmpty(),
        )
    }

    private fun plan(
        manifest: ReaderAssetChapterManifest,
        viewport: ReaderViewportSnapshot,
        network: ReaderNetworkState,
        next: ReaderAssetChapterManifest? = null,
    ) = planner.plan(
        manifest = manifest,
        viewport = viewport,
        networkState = network,
        cachePressure = ReaderAssetCachePressure.NORMAL,
        prefetchedManifest = next,
    )
}
