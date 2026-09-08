package app.openstory.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun HikariBootTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
        )
    } else {
        lightColorScheme(
            background = Color.White,
            surface = Color.White,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
