package app.openstory.catalog.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.common.id.PluginId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.matching.ContentMatchDecision
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MappingScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun linkedSources() = capture(
        MappingUiState(
            mappings = listOf(MappingItemUiModel(PluginId("mangadex"), "moonlit", ContentMappingOrigin.USER_APPROVED)),
        ),
        "sources.png",
    )

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun mappingReview() = capture(
        MappingUiState(
            mappings = listOf(MappingItemUiModel(PluginId("mangadex"), "moonlit", ContentMappingOrigin.USER_APPROVED)),
            candidates = listOf(
                MappingCandidateUiModel(
                    PluginId("reader.example"), "moonlit-en", "The Fox of the Moonlit Archive",
                    "https://reader.example/moonlit", ContentMatchDecision.REVIEW, 0.94,
                    listOf("Title 100%", "Authors 100%", "Language English"), false,
                ),
            ),
        ),
        "mapping.png",
    )

    private fun capture(state: MappingUiState, fileName: String) {
        compose.setContent {
            HikariTheme(darkTheme = true, motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                MappingItemsTestHost(state, MappingActions())
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/mapping/$fileName")
    }
}

@Composable
private fun MappingItemsTestHost(
    state: MappingUiState,
    actions: MappingActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        mappingItems(state, actions)
    }
}
