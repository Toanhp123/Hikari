package app.openstory.library.domain

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LibraryContractsTest {
    @Test
    fun queryRejectsWindowsOutsideThePhysicalLimit() {
        assertFailsWith<IllegalArgumentException> {
            LibraryQuery(text = "", filter = LibraryFilter.ALL, after = null, limit = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LibraryQuery(text = "", filter = LibraryFilter.ALL, after = null, limit = 61)
        }
    }

    @Test
    fun entryRejectsArtworkThatBelongsToAnotherStory() {
        val entryRef = ref("entry")
        val otherRef = ref("other")
        val locator = CoverLocator.TrustedLocalResource("cover", "v1")
        val key = CoverAssetKey(
            storyId = otherRef.storyId,
            coverRevision = CoverRevisionV1.local("cover", "v1"),
        )

        assertFailsWith<IllegalArgumentException> {
            LibraryEntry(
                ref = entryRef,
                originMediaContext = CatalogMediaType.MANGA,
                savedAtEpochMs = 10,
                snapshot = LibraryPresentationSnapshot(
                    title = "Entry",
                    artwork = LibraryArtworkSnapshot(key, locator),
                    supportingText = null,
                ),
            )
        }
    }

    private fun ref(sourceStoryId: String): StorySourceRef {
        val key = CatalogSourceKey("fixture.source")
        return StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(key, sourceStoryId)),
            catalogSourceKey = key,
            sourceStoryId = sourceStoryId,
        )
    }
}
