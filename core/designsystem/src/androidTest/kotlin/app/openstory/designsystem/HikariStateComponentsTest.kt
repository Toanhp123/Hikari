package app.openstory.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HikariStateComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingExposesItsLabel() {
        compose.setContent {
            HikariTheme { HikariLoadingState(label = "Loading chapters") }
        }

        compose.onNodeWithText("Loading chapters").assertIsDisplayed()
    }

    @Test
    fun skeletonIsStaticAndNonActionable() {
        compose.setContent {
            HikariTheme {
                HikariSkeleton(
                    modifier = Modifier.size(120.dp).testTag("skeleton"),
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }

        compose.onNodeWithTag("skeleton").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick),
        )
    }

    @Test
    fun emptyActionInvokesCallback() {
        var invoked = false
        compose.setContent {
            HikariTheme {
                HikariEmptyState(
                    title = "Nothing saved",
                    actionLabel = "Browse",
                    onAction = { invoked = true },
                )
            }
        }

        compose.onNodeWithText("Browse").performClick()
        compose.runOnIdle { assertTrue(invoked) }
    }

    @Test
    fun errorRetryInvokesCallback() {
        var invoked = false
        compose.setContent {
            HikariTheme {
                HikariErrorState(
                    title = "Could not load",
                    actionLabel = "Retry",
                    onAction = { invoked = true },
                )
            }
        }

        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertTrue(invoked) }
    }
}
