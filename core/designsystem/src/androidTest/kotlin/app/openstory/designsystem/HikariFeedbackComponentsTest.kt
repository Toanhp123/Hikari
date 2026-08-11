package app.openstory.designsystem

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import app.openstory.designsystem.feedback.HikariConfirmDialog
import app.openstory.designsystem.feedback.HikariConfirmationStyle
import app.openstory.designsystem.feedback.HikariSnackbarHost
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HikariFeedbackComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun snackbarActionIsRenderedAndClickable() {
        val hostState = SnackbarHostState()
        var result: SnackbarResult? = null
        compose.setContent {
            HikariTheme {
                HikariSnackbarHost(hostState = hostState)
                LaunchedEffect(hostState) {
                    result = hostState.showSnackbar(
                        message = "Story saved",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Indefinite,
                    )
                }
            }
        }

        compose.onNodeWithText("Undo").performClick()
        compose.runOnIdle { assertEquals(SnackbarResult.ActionPerformed, result) }
    }

    @Test
    fun confirmCallsOnConfirm() {
        var confirmed = false
        compose.setContent {
            HikariTheme {
                HikariConfirmDialog(
                    title = "Save source?",
                    message = "Use this source for future chapters.",
                    confirmLabel = "Save",
                    dismissLabel = "Cancel",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun dismissButtonAndRequestCallOnDismiss() {
        var dismissCount = 0
        compose.setContent {
            HikariTheme {
                HikariConfirmDialog(
                    title = "Remove download?",
                    message = "The chapter remains in your library.",
                    confirmLabel = "Remove",
                    dismissLabel = "Keep",
                    onConfirm = {},
                    onDismiss = { dismissCount += 1 },
                )
            }
        }

        compose.onNodeWithText("Keep").performClick()
        compose.runOnIdle { assertEquals(1, dismissCount) }
        pressBack()
        compose.runOnIdle { assertEquals(2, dismissCount) }
    }

    @Test
    fun destructiveStylePreservesConfirmBehavior() {
        var confirmed = false
        compose.setContent {
            HikariTheme {
                HikariConfirmDialog(
                    title = "Delete chapter?",
                    message = "This removes the local copy.",
                    confirmLabel = "Delete",
                    dismissLabel = "Cancel",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                    style = HikariConfirmationStyle.DESTRUCTIVE,
                )
            }
        }

        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
