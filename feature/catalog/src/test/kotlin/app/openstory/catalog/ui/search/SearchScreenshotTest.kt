package app.openstory.catalog.ui.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.search.CatalogSearchFailure
import app.openstory.catalog.search.CatalogSearchFilterGroup
import app.openstory.catalog.search.CatalogSearchSourceCard
import app.openstory.catalog.search.CatalogSearchStory
import app.openstory.catalog.source.SourceFilterOption
import app.openstory.catalog.source.SourceOptionFilter
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun searchResults() = capture(fixture(), "search.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun partialSourceFailure() = capture(
        fixture().copy(failures = listOf(CatalogSearchFailure(PluginId("catalog.b"), "catalog.offline", true))),
        "partial-source-failure.png",
    )

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun focusedHeaderRespectsProvidedSafeInsetAndNavigatesBack() {
        var backRequested = false
        compose.setContent {
            HikariTheme(darkTheme = true) {
                SearchScreen(
                    state = fixture(),
                    onQueryChange = {},
                    onRecentSelected = {},
                    onFilterValuesChange = { _, _, _ -> },
                    onClearFilters = {},
                    onStorySelected = {},
                    onBack = { backRequested = true },
                    contentPadding = PaddingValues(top = 40.dp),
                )
            }
        }

        val back = compose.onNodeWithContentDescription("Back")
        assertTrue(back.fetchSemanticsNode().boundsInRoot.top >= 40f)
        back.performClick()
        assertTrue(backRequested)
    }

    private fun capture(state: SearchUiState, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                SearchScreen(state, {}, {}, { _, _, _ -> }, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/search/$fileName")
    }
}

private fun fixture(): SearchUiState {
    val plugin = PluginId("catalog.mangadex")
    val source = CatalogSearchSourceCard(
        plugin, "moonlit", "The Fox of the Moonlit Archive", ContentType.MANGA,
        setOf("Mira Hoshino"), null, Score(8.8, 10.0),
    )
    return SearchUiState(
        query = "moonlit archive",
        filterGroups = listOf(
            CatalogSearchFilterGroup(
                plugin,
                listOf(
                    SourceOptionFilter(
                        "language", "Language", true,
                        listOf(SourceFilterOption("en", "English"), SourceFilterOption("vi", "Vietnamese")),
                    ),
                    SourceOptionFilter(
                        "genre", "Genre", true,
                        listOf(SourceFilterOption("fantasy", "Fantasy"), SourceFilterOption("mystery", "Mystery")),
                    ),
                ),
            ),
        ),
        filterValues = mapOf(plugin to mapOf("language" to listOf("en"))),
        stories = listOf(CatalogSearchStory(Story(StoryId("moonlit"), ContentType.MANGA), listOf(source))),
        recentQueries = listOf("quiet stars", "winter index"),
    )
}
