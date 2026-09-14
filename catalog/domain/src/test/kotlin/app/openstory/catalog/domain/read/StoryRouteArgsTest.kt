package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.common.id.StoryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StoryRouteArgsTest {
    private val sourceKey = CatalogSourceKey("test.source")
    private val sourceStoryId = "story-1"
    private val ref = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(sourceKey, sourceStoryId)),
        catalogSourceKey = sourceKey,
        sourceStoryId = sourceStoryId,
    )

    @Test
    fun validArgsWithoutPreviewSucceeds() {
        val args = StoryRouteArgs(
            ref = ref,
            originMediaContext = CatalogMediaType.MANGA,
            preview = null,
        )
        assertEquals(ref, args.ref)
        assertEquals(CatalogMediaType.MANGA, args.originMediaContext)
    }

    @Test
    fun validArgsWithAlignedLocalPreviewSucceeds() {
        val revision = CoverRevisionV1.local("asset-1", "1.0")
        val key = CoverAssetKey(ref.storyId, revision)
        val locator = CoverLocator.TrustedLocalResource("asset-1", "1.0")
        val preview = StoryRoutePreview(
            title = "A Great Manga",
            coverLocator = locator,
            coverAssetKey = key,
        )
        val args = StoryRouteArgs(
            ref = ref,
            originMediaContext = CatalogMediaType.MANGA,
            preview = preview,
        )
        assertNotNull(args.preview)
        assertEquals("A Great Manga", args.preview?.title)
    }

    @Test
    fun misalignedCoverThrows() {
        val otherStoryId = StoryId("other-story-id")
        val revision = CoverRevisionV1.local("asset-1", "1.0")
        val key = CoverAssetKey(otherStoryId, revision)
        val locator = CoverLocator.TrustedLocalResource("asset-1", "1.0")

        assertThrows(IllegalArgumentException::class.java) {
            StoryRouteArgs(
                ref = ref,
                originMediaContext = CatalogMediaType.MANGA,
                preview = StoryRoutePreview(
                    title = "Title",
                    coverLocator = locator,
                    coverAssetKey = key,
                ),
            )
        }
    }


    @Test
    fun previewTitleBoundCountsUnicodeScalarsRatherThanUtf16CodeUnits() {
        val atLimit = "😀".repeat(CatalogInputLimits.TITLE_UNICODE_SCALARS)
        val preview = StoryRoutePreview(title = atLimit)

        assertEquals(atLimit, preview.title)
        assertThrows(IllegalArgumentException::class.java) {
            StoryRoutePreview(title = atLimit + "😀")
        }
    }

    @Test
    fun blankPreviewTitleThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            StoryRoutePreview(title = "   ")
        }
    }
}
