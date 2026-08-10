package app.openstory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.navigation.AppNavHost
import app.openstory.navigation.rememberAppNavigator

@Composable
fun OpenStoryApp(
    modifier: Modifier = Modifier,
) {
    val navigator = rememberAppNavigator()
    MaterialTheme {
        AppNavHost(navigator = navigator, modifier = modifier)
    }
}
