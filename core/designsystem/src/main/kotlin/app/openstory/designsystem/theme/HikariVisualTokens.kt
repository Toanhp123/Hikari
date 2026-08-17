package app.openstory.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class HikariSemanticColors(
    val onArtwork: Color = Color.White,
    val onArtworkInverse: Color = Color.Black,
    val artworkScrim: Color = Color.Black,
    val transparent: Color = Color.Transparent,
    val surfaceShadow: Color = Color.Black,
)

@Immutable
data class HikariOpacity(
    val glassSurface: Float = 0.8509804f,
    val glassBorder: Float = 0.72f,
    val surfaceStrong: Float = 0.88f,
    val surfaceOpaque: Float = 0.94f,
    val accentBorder: Float = 0.72f,
    val metadataBadge: Float = 0.72f,
    val subtleSurface: Float = 0.72f,
    val selectedSubtle: Float = 0.18f,
    val artworkBackdropMid: Float = 0.54f,
    val artworkBackdropStrong: Float = 0.88f,
    val artworkMonogram: Float = 0.92f,
    val heroScrimLight: Float = 0.18f,
    val heroScrimMedium: Float = 0.68f,
    val heroScrimStrong: Float = 0.92f,
    val heroBottomScrim: Float = 0.30f,
    val onArtworkSecondary: Float = 0.78f,
    val onArtworkMuted: Float = 0.82f,
    val onArtworkBadge: Float = 0.13f,
    val surfaceShadow: Float = 0.14f,
)

@Immutable
class HikariVisualBrushes internal constructor(
    val atmosphere: Brush,
    val artworkBackdrop: Brush,
    val heroHorizontalScrim: Brush,
    val heroVerticalScrim: Brush,
)

val HikariDefaultSemanticColors = HikariSemanticColors()
val HikariDefaultOpacity = HikariOpacity()

internal val LocalHikariSemanticColors = staticCompositionLocalOf { HikariDefaultSemanticColors }
internal val LocalHikariOpacity = staticCompositionLocalOf { HikariDefaultOpacity }
internal val LocalHikariVisualBrushes = staticCompositionLocalOf {
    buildHikariVisualBrushes(HikariLightColorScheme, HikariDefaultSemanticColors, HikariDefaultOpacity)
}

internal val HikariLightVisualBrushes = buildHikariVisualBrushes(
    HikariLightColorScheme,
    HikariDefaultSemanticColors,
    HikariDefaultOpacity,
)
internal val HikariDarkVisualBrushes = buildHikariVisualBrushes(
    HikariDarkColorScheme,
    HikariDefaultSemanticColors,
    HikariDefaultOpacity,
)

private fun buildHikariVisualBrushes(
    colorScheme: ColorScheme,
    colors: HikariSemanticColors,
    opacity: HikariOpacity,
): HikariVisualBrushes = HikariVisualBrushes(
    atmosphere = Brush.verticalGradient(
        listOf(
            colorScheme.secondaryContainer.copy(alpha = opacity.subtleSurface),
            colorScheme.background.copy(alpha = opacity.surfaceOpaque),
            colorScheme.background,
        ),
    ),
    artworkBackdrop = Brush.verticalGradient(
        listOf(
            colors.transparent,
            colors.artworkScrim.copy(alpha = opacity.artworkBackdropMid),
            colors.artworkScrim.copy(alpha = opacity.artworkBackdropStrong),
        ),
    ),
    heroHorizontalScrim = Brush.horizontalGradient(
        listOf(
            colors.artworkScrim.copy(alpha = opacity.heroScrimLight),
            colors.artworkScrim.copy(alpha = opacity.heroScrimMedium),
            colors.artworkScrim.copy(alpha = opacity.heroScrimStrong),
        ),
    ),
    heroVerticalScrim = Brush.verticalGradient(
        listOf(
            colors.transparent,
            colors.artworkScrim.copy(alpha = opacity.heroBottomScrim),
        ),
    ),
)

val MaterialTheme.hikariColors: HikariSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariSemanticColors.current

val MaterialTheme.hikariOpacity: HikariOpacity
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariOpacity.current

val MaterialTheme.hikariAtmosphereBrush: Brush
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariVisualBrushes.current.atmosphere

val MaterialTheme.hikariArtworkBackdropScrim: Brush
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariVisualBrushes.current.artworkBackdrop

val MaterialTheme.hikariHeroHorizontalScrim: Brush
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariVisualBrushes.current.heroHorizontalScrim

val MaterialTheme.hikariHeroVerticalScrim: Brush
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariVisualBrushes.current.heroVerticalScrim

internal val HikariArtworkFallbackPalette = listOf(
    Color(0xFF425B76),
    Color(0xFF6B536F),
    Color(0xFF386A73),
    Color(0xFF765A45),
    Color(0xFF59664A),
    Color(0xFF73515A),
    Color(0xFF4F5E87),
    Color(0xFF7A6040),
)
