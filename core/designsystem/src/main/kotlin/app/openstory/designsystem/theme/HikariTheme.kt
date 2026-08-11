package app.openstory.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun HikariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHikariSpacing provides HikariSpacing(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HikariDarkColorScheme else HikariLightColorScheme,
            typography = HikariTypography,
            shapes = HikariShapes,
            content = content,
        )
    }
}
