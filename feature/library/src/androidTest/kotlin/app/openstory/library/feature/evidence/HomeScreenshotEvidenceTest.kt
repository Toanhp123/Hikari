package app.openstory.library.feature.evidence

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.feature.HomeContentState
import app.openstory.library.feature.HomeScreen
import app.openstory.library.feature.HomeTestTags
import app.openstory.library.feature.HomeUiState
import app.openstory.library.feature.LibraryStoryPosterUi
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenshotEvidenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = mutableStateOf(HomeUiState(content = HomeContentState.LibraryEmpty))
    private val captured = mutableListOf<File>()

    @Test
    fun capturesHomeLibraryStateMatrixWithDeterministicNames() {
        composeRule.setContent {
            HikariTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(
                        state = state.value,
                        onInputQueryChanged = {},
                        onFilterSelected = {},
                        onExploreManga = {},
                        onExploreLightNovels = {},
                        onStorySelected = {},
                        onRetry = {},
                        artwork = { _, modifier ->
                            androidx.compose.foundation.layout.Box(modifier.background(Color.LightGray))
                        },
                    )
                }
            }
        }

        show(HomeUiState(content = HomeContentState.LibraryEmpty))
        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()
        capture("home-library-empty")

        show(
            HomeUiState(
                inputQuery = "missing",
                filter = LibraryFilter.MANGA,
                content = HomeContentState.NoMatches,
            ),
        )
        composeRule.onNodeWithText("No saved stories match").assertIsDisplayed()
        capture("home-library-no-match")

        val story = poster("saved")
        show(HomeUiState(content = HomeContentState.Content(listOf(story))))
        composeRule.onNodeWithTag(HomeTestTags.story(story.ref)).assertIsDisplayed()
        capture("home-library-content")

        assertEquals(3, captured.size)
        assertTrue(captured.all { it.isFile && it.length() > 0L })
    }

    private fun show(next: HomeUiState) {
        composeRule.runOnIdle { state.value = next }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.getExternalFilesDir(EVIDENCE_DIRECTORY)
            ?: context.cacheDir.resolve(EVIDENCE_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs())
        val widthClass = if (context.resources.configuration.screenWidthDp >= WIDE_WIDTH_DP) {
            "wide"
        } else {
            "compact"
        }
        val file = directory.resolve("$widthClass-$name.png")
        val bitmap = composeRule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        file.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        }
        check(file.isFile && file.length() > 0L)
        println("SCREENSHOT_EVIDENCE=${file.absolutePath}")
        captured += file
    }

    private companion object {
        const val EVIDENCE_DIRECTORY = "home-screenshot-evidence"
        const val WIDE_WIDTH_DP = 600
        const val PNG_QUALITY = 100
        val SOURCE = CatalogSourceKey("home-screenshot-evidence")

        fun poster(id: String): LibraryStoryPosterUi {
            val ref = StorySourceRef(
                storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE, id)),
                catalogSourceKey = SOURCE,
                sourceStoryId = id,
            )
            return LibraryStoryPosterUi(
                ref = ref,
                originMediaContext = CatalogMediaType.MANGA,
                savedAtEpochMs = 20,
                title = "The Saved Chronicle",
                coverAssetKey = null,
                coverLocator = null,
                supportingText = "Manga",
            )
        }
    }
}
