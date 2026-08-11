package app.openstory.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.navigation.AppNavHost
import app.openstory.navigation.rememberAppNavigator

@Composable
fun OpenStoryApp(
    modifier: Modifier = Modifier,
) {
    val navigator = rememberAppNavigator()
    HikariTheme {
        AppNavHost(
            navigator = navigator,
            modifier = modifier,
        )
    }
}
