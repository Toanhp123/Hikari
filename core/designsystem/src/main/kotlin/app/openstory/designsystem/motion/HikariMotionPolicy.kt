package app.openstory.designsystem.motion

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class HikariMotionPolicy(
    val reduceMotion: Boolean = false,
)

val LocalHikariMotionPolicy = staticCompositionLocalOf { HikariMotionPolicy() }
