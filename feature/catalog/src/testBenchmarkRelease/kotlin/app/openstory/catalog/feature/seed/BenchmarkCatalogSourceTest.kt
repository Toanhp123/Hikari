package app.openstory.catalog.feature.seed

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.model.CatalogMediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkCatalogSourceTest {
    private val sourceKey = CatalogSourceKey("hikari.benchmark.local")

    @Test
    fun normalScenarioKeepsExactSectionMembershipForBothMedia() = runTest {
        var workerThreadAssertions = 0
        val source = BenchmarkCatalogSource(
            catalogSourceKey = sourceKey,
            scenario = BenchmarkCatalogScenario.NORMAL,
            assertWorkerThread = { workerThreadAssertions += 1 },
        )

        CatalogMediaType.entries.forEach { mediaType ->
            val acquisition = source.acquireDiscover(mediaType)
            assertEquals(
                listOf(5, 9, 5),
                acquisition.sections.map { it.items.size },
            )
            assertTrue(acquisition.sections.first().items.first().cover is AcquisitionCoverInput.RemoteHttps)
        }
        assertEquals(2, workerThreadAssertions)
    }

    @Test
    fun persistedEmptyScenarioPublishesNoSectionsThroughTheImporterInput() = runTest {
        val source = BenchmarkCatalogSource(
            sourceKey,
            BenchmarkCatalogScenario.PERSISTED_EMPTY,
            assertWorkerThread = {},
        )

        CatalogMediaType.entries.forEach { mediaType ->
            assertTrue(source.acquireDiscover(mediaType).sections.isEmpty())
        }
    }

    @Test
    fun oversizedDetailScenarioCreatesAProductionValidatorRejectionFixture() = runTest {
        val source = BenchmarkCatalogSource(
            sourceKey,
            BenchmarkCatalogScenario.OVERSIZED_DETAIL,
            assertWorkerThread = {},
        )
        val normal = BenchmarkCatalogSource(
            sourceKey,
            BenchmarkCatalogScenario.NORMAL,
            assertWorkerThread = {},
        )
        val first = normal.acquireDiscover(CatalogMediaType.MANGA).sections.first().items.first()
        val ref = app.openstory.catalog.domain.identity.StorySourceRef(
            storyId = app.openstory.catalog.domain.identity.SourceStoryIdV1.derive(
                app.openstory.catalog.domain.identity.SourceStoryKey(sourceKey, first.sourceStoryId),
            ),
            catalogSourceKey = sourceKey,
            sourceStoryId = first.sourceStoryId,
        )

        assertTrue(requireNotNull(source.acquireStoryDetail(ref).description).length > 64 * 1024)
    }

    @Test
    fun rotatingGenerationScenarioKeepsOldDetailsResolvableWhileDiscoverIdentityChanges() = runTest {
        val source = BenchmarkCatalogSource(
            sourceKey,
            BenchmarkCatalogScenario.valueOf("ROTATING_GENERATIONS"),
            assertWorkerThread = {},
        )

        val first = source.acquireDiscover(CatalogMediaType.MANGA)
        val second = source.acquireDiscover(CatalogMediaType.MANGA)
        val firstItem = first.sections.first().items.first()
        val secondItem = second.sections.first().items.first()
        val firstRef = app.openstory.catalog.domain.identity.StorySourceRef(
            storyId = app.openstory.catalog.domain.identity.SourceStoryIdV1.derive(
                app.openstory.catalog.domain.identity.SourceStoryKey(sourceKey, firstItem.sourceStoryId),
            ),
            catalogSourceKey = sourceKey,
            sourceStoryId = firstItem.sourceStoryId,
        )

        assertNotEquals(firstItem.sourceStoryId, secondItem.sourceStoryId)
        assertEquals(listOf(5, 9, 5), second.sections.map { it.items.size })
        assertEquals(firstItem.title, source.acquireStoryDetail(firstRef).title)
    }

    @Test
    fun pinPruneSourceHoldsTheArmedMangaRefreshUntilTheFixtureReleasesIt() = runTest {
        val source = BenchmarkPinPruneSource(
            BenchmarkCatalogSource(sourceKey, assertWorkerThread = {}),
        )
        source.armPrune()

        val refresh = async { source.acquireDiscover(CatalogMediaType.MANGA) }
        source.pruneEntered.await()

        assertFalse(refresh.isCompleted)
        source.releasePrune()
        assertTrue(refresh.await().sections.isEmpty())
        assertEquals(
            listOf(5, 9, 5),
            source.acquireDiscover(CatalogMediaType.LIGHT_NOVEL).sections.map { it.items.size },
        )
    }

    @Test
    fun preparationWireValuesResolveExactlyAndRejectUnknownInput() {
        assertEquals(
            BenchmarkCatalogPreparation.PERSISTED_EMPTY,
            BenchmarkCatalogPreparation.fromWireValue("persisted-empty"),
        )
        assertEquals(
            BenchmarkCatalogPreparation.REPEATED_REFRESH,
            BenchmarkCatalogPreparation.fromWireValue("repeated-refresh"),
        )
        assertEquals(
            "orphan-overflow",
            BenchmarkCatalogPreparation.fromWireValue("orphan-overflow").wireValue,
        )
        assertEquals(
            "pin-prune-race",
            BenchmarkCatalogPreparation.fromWireValue("pin-prune-race").wireValue,
        )
        assertEquals(
            "pathological-image-bounds",
            BenchmarkCatalogPreparation.fromWireValue("pathological-image-bounds").wireValue,
        )
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkCatalogPreparation.fromWireValue("unknown")
        }
    }
}
