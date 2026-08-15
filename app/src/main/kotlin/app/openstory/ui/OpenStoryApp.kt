package app.openstory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.openstory.designsystem.glass.HikariBackdropMode
import app.openstory.designsystem.glass.LocalHikariBackdropMode
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.navigation.AppNavHost
import app.openstory.navigation.rememberAppNavigator

@Composable
fun OpenStoryApp(
    modifier: Modifier = Modifier,
    backdropMode: HikariBackdropMode = HikariBackdropMode.ENABLED,
) {
    val navigator = rememberAppNavigator()
    CompositionLocalProvider(LocalHikariBackdropMode provides backdropMode) {
        HikariTheme {
            AppNavHost(
                navigator = navigator,
                modifier = modifier.semantics { testTagsAsResourceId = true },
            )
        }
    }
}
