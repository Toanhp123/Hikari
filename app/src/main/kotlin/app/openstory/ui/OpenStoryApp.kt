package app.openstory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.navigation.OpenStoryNavDisplay

@Composable
fun OpenStoryApp(
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        OpenStoryNavDisplay(
            modifier = modifier,
        )
    }
}
