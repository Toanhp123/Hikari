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
        LocalHikariDimensions provides HikariDimensions(),
        LocalHikariBreakpoints provides HikariBreakpoints(),
        LocalHikariLayoutRatios provides HikariLayoutRatios(),
        LocalHikariLayoutPolicy provides HikariLayoutPolicy(),
        LocalHikariGlyphGeometry provides HikariGlyphGeometry(),
        LocalHikariSemanticShapes provides HikariSemanticShapes(),
        LocalHikariSemanticColors provides HikariSemanticColors(),
        LocalHikariOpacity provides HikariOpacity(),
        LocalHikariSemanticTypography provides HikariSemanticTypography(),
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
