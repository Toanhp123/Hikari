package app.openstory.home.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import app.openstory.home.domain.SearchCatalogFilters
import app.openstory.home.domain.SearchRangeFilterDefinition
import app.openstory.home.domain.SearchResultPage
import app.openstory.model.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule

class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rangeFilterExposesProgressControlAndWritesSelectedValue() {
        val selectedValues = mutableListOf<List<String>>()
        val state = SearchScreenState(
            page = SearchResultPage(
                filters = listOf(
                    SearchCatalogFilters(
                        pluginId = PluginId("catalog.a"),
                        pluginVersion = "1.0.0",
                        definitions = listOf(
                            SearchRangeFilterDefinition(
                                id = "score",
                                label = "Score",
                                minimum = 0.0,
                                maximum = 10.0,
                                step = 1.0,
                            ),
                        ),
                    ),
                ),
            ),
        )

        composeRule.setContent {
            SearchScreen(
                state = state,
                onQueryChange = {},
                onFilterValuesChange = { _, _, values -> selectedValues += values },
                onStoryClick = {},
            )
        }

        composeRule
            .onNodeWithContentDescription("Score range")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(5.0f)
            }

        assertEquals(listOf("5"), selectedValues.single())
    }
}
