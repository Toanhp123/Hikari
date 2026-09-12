package app.openstory.catalog.feature.story

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StoryHeroUiTest {
    @Test
    fun heroProjectionExcludesDestinationWideDetailAndCapabilityState() {
        val original = state().toHeroUi()
        val changedDestinationState = state().copy(
            detail = StoryDetailUi(
                description = "Changed body",
                authors = listOf("Changed author"),
                artists = listOf("Changed artist"),
                genres = listOf("Changed genre"),
                publicationStatus = "Changed detail status",
                language = "Changed language",
            ),
            detailLoading = true,
            issue = CatalogIssueUi(CatalogIssueKind.ACQUISITION_FAILED, retryable = true),
            destinationActive = false,
        )

        assertEquals(original, changedDestinationState.toHeroUi())
    }

    @Test
    fun heroProjectionIncludesIdentityAndArtworkContinuity() {
        val original = state()
        val changedIdentity = original.copy(
            summary = requireNotNull(original.summary).copy(
                title = "Changed title",
                contentType = CatalogMediaType.LIGHT_NOVEL,
                ratingLabel = "9.0 / 10",
                publicationStatus = "Completed",
                latestUpdateLabel = "Updated Sep 12, 2026",
            ),
        )
        val changedArtwork = original.copy(
            artwork = StoryArtworkUi(
                assetKey = ALTERNATE_COVER_KEY,
                locator = ALTERNATE_COVER_LOCATOR,
            ),
        )

        assertNotEquals(original.toHeroUi(), changedIdentity.toHeroUi())
        assertNotEquals(original.toHeroUi(), changedArtwork.toHeroUi())
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("story-hero-ui-test")
        val REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-17")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-17",
        )
        val COVER_LOCATOR = CoverLocator.TrustedLocalResource("story-cover", "1")
        val COVER_KEY = CoverAssetKey(REF.storyId, CoverRevisionV1.local("story-cover", "1"))
        val ALTERNATE_COVER_LOCATOR = CoverLocator.TrustedLocalResource("story-cover", "2")
        val ALTERNATE_COVER_KEY = CoverAssetKey(
            REF.storyId,
            CoverRevisionV1.local("story-cover", "2"),
        )

        fun state() = StoryDetailUiState(
            ref = REF,
            summary = StorySummaryUi(
                title = "Story 17",
                contentType = CatalogMediaType.MANGA,
                ratingLabel = "8.5 / 10",
                publicationStatus = "Ongoing",
                latestUpdateLabel = "Updated Sep 11, 2026",
            ),
            detail = null,
            detailLoading = false,
            issue = null,
            destinationActive = true,
            artwork = StoryArtworkUi(assetKey = COVER_KEY, locator = COVER_LOCATOR),
        )
    }
}
