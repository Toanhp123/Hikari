package app.openstory.reader.assets

import app.openstory.reader.routing.ReaderSessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderAssetWorkingSetPolicyTest {
    private val policy = ReaderAssetWorkingSetPolicy()

    @Test
    fun `mixed content viewport protects asset ordinals and exposes only two behind hints`() {
        val manifest = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 12)
        val viewport = ReaderViewportSnapshot(
            sessionId = ReaderSessionId(1),
            manifestRevision = 3,
            leadingVisibleImageOrdinal = 5,
            trailingVisibleImageOrdinal = 7,
            direction = ReaderViewportDirection.FORWARD,
            chapterProgressBasisPoints = 6_000,
        )
        val plan = ReaderAssetPlan(
            interactive = manifest.descriptors.slice(5..7),
            currentAhead = manifest.descriptors.slice(8..11),
            transition = emptyList(),
        )

        val protections = policy.protections(
            manifest = manifest,
            viewport = viewport,
            consumedKeys = setOf(manifest.descriptors[4].key),
            recentManifests = emptyList(),
            plan = plan,
        )

        assertEquals(listOf(3, 4), policy.memoryPrewarmBehind(manifest, viewport).map { it.imageOrdinal })
        assertEquals(
            ReaderAssetProtectionClass.ACTIVE_INTERACTIVE,
            protections.byKey.getValue(manifest.descriptors[5].key.hash),
        )
        assertEquals(
            ReaderAssetProtectionClass.ACTIVE_CONSUMED,
            protections.byKey.getValue(manifest.descriptors[4].key.hash),
        )
        assertEquals(
            ReaderAssetProtectionClass.CURRENT_AHEAD_SPECULATIVE,
            protections.byKey.getValue(manifest.descriptors[8].key.hash),
        )
    }

    @Test
    fun `text only state emits no ricc protections`() {
        val protections = policy.protections(
            manifest = null,
            viewport = null,
            consumedKeys = emptySet(),
            recentManifests = emptyList(),
            plan = ReaderAssetPlan.EMPTY,
        )

        assertEquals(ReaderAssetActiveProtections.EMPTY, protections)
    }

    @Test
    fun `recent history is bounded and union preserves the strongest class across sessions`() {
        val current = assetManifest(sessionId = 1, chapter = "chapter-3", pageCount = 2)
        val recentOne = assetManifest(sessionId = 1, chapter = "chapter-2", pageCount = 2)
        val recentTwo = assetManifest(sessionId = 1, chapter = "chapter-1", pageCount = 2)
        val ignored = assetManifest(sessionId = 1, chapter = "chapter-0", pageCount = 2)
        val first = policy.protections(
            manifest = current,
            viewport = viewport(current, leading = 0, trailing = 0),
            consumedKeys = emptySet(),
            recentManifests = listOf(recentOne, recentTwo, ignored),
            plan = ReaderAssetPlan(interactive = listOf(current.descriptors[0])),
        )
        val second = ReaderAssetActiveProtections(
            mapOf(recentOne.descriptors[0].key.hash to ReaderAssetProtectionClass.ACTIVE_INTERACTIVE),
        )

        val union = policy.union(listOf(first, second))

        assertEquals(
            ReaderAssetProtectionClass.ACTIVE_INTERACTIVE,
            union.byKey.getValue(recentOne.descriptors[0].key.hash),
        )
        assertEquals(
            ReaderAssetProtectionClass.RECENT_HISTORY_2,
            union.byKey.getValue(recentTwo.descriptors[0].key.hash),
        )
        assertTrue(ignored.descriptors.none { it.key.hash in union.byKey })
    }
}
