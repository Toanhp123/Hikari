package app.openstory.catalog.feature

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogVariantFixtureTest {
    @Test
    fun nonReleaseBindingProvidesBoundedTypedFixturesForBothMedia() = runTest {
        val variant: CatalogVariantBinding = VariantCatalogBinding
        val binding: CatalogSourceBinding = requireNotNull(variant.binding)
        val source = requireNotNull(binding.acquisitionSource)

        CatalogMediaType.entries.forEach { mediaType ->
            val acquisition = source.acquireDiscover(mediaType)

            assertEquals(
                listOf(
                    CatalogSectionKind.POPULAR,
                    CatalogSectionKind.LATEST_UPDATES,
                    CatalogSectionKind.TOP_RATED,
                ),
                acquisition.sections.map { it.kind },
            )
            assertEquals(listOf(5, 9, 5), acquisition.sections.map { it.items.size })
            assertEquals(19, acquisition.sections.sumOf { it.items.size })
            assertTrue(acquisition.sections.flattenItems().any { item ->
                item.sourceStoryId.any { character -> character.code > 127 }
            })
            acquisition.sections.flattenItems().forEach { item ->
                assertEquals(mediaType, item.contentType)
                val cover = item.cover as AcquisitionCoverInput.TrustedLocal
                assertNotNull(variant.localCoverResource(cover.logicalAssetId, cover.assetVersion))
            }

            val first = acquisition.sections.first().items.first()
            val ref = StorySourceRef(
                storyId = SourceStoryIdV1.derive(
                    SourceStoryKey(binding.catalogSourceKey, first.sourceStoryId),
                ),
                catalogSourceKey = binding.catalogSourceKey,
                sourceStoryId = first.sourceStoryId,
            )
            val detail = source.acquireStoryDetail(ref)

            assertEquals(first.sourceStoryId, detail.sourceStoryId)
            assertEquals(mediaType, detail.contentType)
            assertTrue(detail.description.orEmpty().isNotBlank())
            assertTrue(detail.authors.isNotEmpty())
            assertTrue(detail.artists.isNotEmpty())
            assertTrue(detail.genres.isNotEmpty())
        }

        assertEquals(binding.catalogSourceKey, binding.assetPolicy?.catalogSourceKey)
        assertEquals(null, variant.localCoverResource("unknown", "1"))
    }

    private fun List<app.openstory.catalog.domain.source.DiscoverAcquisitionSection>.flattenItems() =
        flatMap { it.items }

}
