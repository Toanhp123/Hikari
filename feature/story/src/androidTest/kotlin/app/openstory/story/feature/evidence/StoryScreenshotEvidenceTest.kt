package app.openstory.story.feature.evidence

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.story.feature.StoryDetailScreen
import app.openstory.story.feature.StoryDetailUi
import app.openstory.story.feature.StoryDetailUiState
import app.openstory.story.feature.StorySummaryUi
import app.openstory.story.feature.StoryTestTags
import app.openstory.story.feature.state.StoryIssueKind
import app.openstory.story.feature.state.StoryIssueUi
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryScreenshotEvidenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = mutableStateOf(storyState(detailLoading = true))
    private val capturedFiles = mutableListOf<File>()

    @Test
    fun capturesAcceptedStorySurfaceMatrixWithDeterministicNames() {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    StoryDetailScreen(
                        state = state.value,
                        onBack = {},
                        onRetry = {},
                    )
                }
            }
        }

        show(storyState(detailLoading = true))
        composeRule.onNodeWithText(STORY_TITLE).assertExists()
        composeRule.onNodeWithTag(STORY_HERO_COVER_TAG).assertExists()
        capture("story-loading-cover")

        scrollTo(hasTestTag(STORY_DETAIL_SKELETON_TAG))
        capture("story-loading-metadata")

        show(storyState(detail = completeStoryDetail()))
        scrollTo(hasText("About"))
        composeRule.onNodeWithText("About").assertExists()
        capture("story-complete")

        show(
            storyState(
                issue = StoryIssueUi(StoryIssueKind.ACQUISITION_FAILED, retryable = true),
            ),
        )
        composeRule.onNodeWithText(STORY_TITLE).assertExists()
        capture("story-metadata-issue-cover")
        scrollTo(hasText("Try again"))
        composeRule.onNodeWithText("Try again").assertExists()
        capture("story-metadata-issue-retry")

        assertEquals(EXPECTED_SCREENSHOT_COUNT, capturedFiles.size)
        assertTrue(capturedFiles.all(File::isFile))
        assertTrue(capturedFiles.all { it.length() > 0L })
        assertEquals(capturedFiles.size, capturedFiles.map(File::getName).toSet().size)
    }

    private fun show(next: StoryDetailUiState) {
        composeRule.runOnIdle { state.value = next }
        composeRule.waitForIdle()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNodeWithTag(StoryTestTags.ROOT).performScrollToNode(matcher)
    }

    private fun capture(scenarioName: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.externalMediaDirs.firstOrNull()
            ?.resolve(EVIDENCE_DIRECTORY)
            ?: context.getExternalFilesDir(EVIDENCE_DIRECTORY)
            ?: context.cacheDir.resolve(EVIDENCE_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs())
        val widthClass = if (context.resources.configuration.screenWidthDp >= WIDE_WIDTH_DP) {
            "wide"
        } else {
            "compact"
        }
        val file = directory.resolve("$widthClass-$scenarioName.png")
        val bitmap = composeRule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        file.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        }
        check(file.isFile && file.length() > 0L)
        println("SCREENSHOT_EVIDENCE=${file.absolutePath}")
        capturedFiles += file
    }

    private companion object {
        const val EXPECTED_SCREENSHOT_COUNT = 5
        const val WIDE_WIDTH_DP = 600
        const val EVIDENCE_DIRECTORY = "story-screenshot-evidence"
        const val PNG_QUALITY = 100
        const val STORY_DETAIL_SKELETON_TAG = "story-detail-skeleton"
        const val STORY_HERO_COVER_TAG = "story-hero-cover"
        const val STORY_TITLE = "The Lantern Archive"
        val SOURCE_KEY = CatalogSourceKey("story-screenshot-evidence")
        val STORY_REF = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, "story-99")),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = "story-99",
        )

        fun storyState(
            detailLoading: Boolean = false,
            detail: StoryDetailUi? = null,
            issue: StoryIssueUi? = null,
        ) = StoryDetailUiState(
            ref = STORY_REF,
            summary = StorySummaryUi(
                title = STORY_TITLE,
                contentType = CatalogMediaType.MANGA,
                ratingLabel = "8.9 / 10",
                publicationStatus = "Ongoing",
                latestUpdateLabel = "Updated Sep 11, 2026",
            ),
            detail = detail,
            detailLoading = detailLoading,
            issue = issue,
        )

        fun completeStoryDetail() = StoryDetailUi(
            description = "A quiet archivist follows a trail of lanterns through a city that forgets its stories.",
            authors = listOf("Aiko Mori"),
            artists = listOf("Ren Ito"),
            genres = listOf("Mystery", "Drama", "Fantasy"),
            publicationStatus = "Ongoing",
            language = "English",
        )
    }
}
