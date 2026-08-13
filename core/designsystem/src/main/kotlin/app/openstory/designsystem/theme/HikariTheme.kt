package app.openstory.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.motion.LocalHikariMotionPolicy

@Composable
fun HikariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionPolicy: HikariMotionPolicy = HikariMotionPolicy(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHikariSpacing provides HikariSpacing(),
        LocalHikariMotionPolicy provides motionPolicy,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HikariDarkColorScheme else HikariLightColorScheme,
            typography = HikariTypography,
            shapes = HikariShapes,
            content = content,
        )
    }
}
