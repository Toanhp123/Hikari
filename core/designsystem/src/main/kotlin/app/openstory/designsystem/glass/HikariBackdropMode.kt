package app.openstory.designsystem.glass

import androidx.compose.runtime.staticCompositionLocalOf

enum class HikariBackdropMode {
    ENABLED,
    DISABLED_FOR_BENCHMARK,
}

val LocalHikariBackdropMode = staticCompositionLocalOf { HikariBackdropMode.ENABLED }

internal fun shouldUseBackdropBlur(
    sdkInt: Int,
    mode: HikariBackdropMode,
    hasBackdrop: Boolean,
): Boolean = mode == HikariBackdropMode.ENABLED &&
    hasBackdrop &&
    glassRenderingMode(sdkInt) == HikariGlassRenderingMode.BLUR
