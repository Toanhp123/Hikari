package app.openstory.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class HikariBackGlyphGeometry(
    val outerX: Float = 0.68f,
    val upperY: Float = 0.18f,
    val innerX: Float = 0.32f,
    val centerY: Float = 0.50f,
    val lowerY: Float = 0.82f,
)

@Immutable
data class HikariSearchGlyphGeometry(
    val ringRadius: Float = 0.27f,
    val ringCenterX: Float = 0.43f,
    val ringCenterY: Float = 0.42f,
    val handleStart: Float = 0.61f,
    val handleEnd: Float = 0.82f,
)

@Immutable
data class HikariFilterGlyphGeometry(
    val lineStartX: Float = 0.08f,
    val lineEndX: Float = 0.92f,
    val firstLineY: Float = 0.24f,
    val firstKnobX: Float = 0.66f,
    val secondLineY: Float = 0.50f,
    val secondKnobX: Float = 0.36f,
    val thirdLineY: Float = 0.76f,
    val thirdKnobX: Float = 0.58f,
)

@Immutable
data class HikariViewGlyphGeometry(
    val gridCellFraction: Float = 0.34f,
    val gridOffsetFraction: Float = 0.58f,
    val firstListLineY: Float = 0.20f,
    val secondListLineY: Float = 0.50f,
    val thirdListLineY: Float = 0.80f,
    val lineStartX: Float = 0.0f,
    val lineEndX: Float = 1.0f,
)


@Immutable
data class HikariGlyphGeometry(
    val back: HikariBackGlyphGeometry = HikariBackGlyphGeometry(),
    val search: HikariSearchGlyphGeometry = HikariSearchGlyphGeometry(),
    val filter: HikariFilterGlyphGeometry = HikariFilterGlyphGeometry(),
    val view: HikariViewGlyphGeometry = HikariViewGlyphGeometry(),
)

val HikariDefaultGlyphGeometry = HikariGlyphGeometry()

internal val LocalHikariGlyphGeometry = staticCompositionLocalOf { HikariDefaultGlyphGeometry }

val MaterialTheme.hikariGlyphGeometry: HikariGlyphGeometry
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariGlyphGeometry.current
