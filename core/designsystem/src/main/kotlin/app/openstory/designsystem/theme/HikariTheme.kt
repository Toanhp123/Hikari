package app.openstory.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.openstory.designsystem.motion.HikariDefaultMotionPolicy
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.motion.LocalHikariMotionPolicy

@Composable
fun HikariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionPolicy: HikariMotionPolicy = HikariDefaultMotionPolicy,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHikariSpacing provides HikariDefaultSpacing,
        LocalHikariDimensions provides HikariDefaultDimensions,
        LocalHikariBreakpoints provides HikariDefaultBreakpoints,
        LocalHikariLayoutRatios provides HikariDefaultLayoutRatios,
        LocalHikariLayoutPolicy provides HikariDefaultLayoutPolicy,
        LocalHikariGlyphGeometry provides HikariDefaultGlyphGeometry,
        LocalHikariSemanticShapes provides HikariDefaultSemanticShapes,
        LocalHikariSemanticColors provides HikariDefaultSemanticColors,
        LocalHikariOpacity provides HikariDefaultOpacity,
        LocalHikariSemanticTypography provides HikariDefaultSemanticTypography,
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
