package app.openstory.designsystem.theme

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

val HikariDefaultSemanticColors = HikariSemanticColors()
val HikariDefaultOpacity = HikariOpacity()

internal val LocalHikariSemanticColors = staticCompositionLocalOf { HikariDefaultSemanticColors }
internal val LocalHikariOpacity = staticCompositionLocalOf { HikariDefaultOpacity }

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
    get() = Brush.verticalGradient(
        listOf(
            colorScheme.secondaryContainer.copy(alpha = hikariOpacity.subtleSurface),
            colorScheme.background.copy(alpha = hikariOpacity.surfaceOpaque),
            colorScheme.background,
        ),
    )

val MaterialTheme.hikariHeroHorizontalScrim: Brush
    @Composable
    @ReadOnlyComposable
    get() = Brush.horizontalGradient(
        listOf(
            hikariColors.artworkScrim.copy(alpha = hikariOpacity.heroScrimLight),
            hikariColors.artworkScrim.copy(alpha = hikariOpacity.heroScrimMedium),
            hikariColors.artworkScrim.copy(alpha = hikariOpacity.heroScrimStrong),
        ),
    )

val MaterialTheme.hikariHeroVerticalScrim: Brush
    @Composable
    @ReadOnlyComposable
    get() = Brush.verticalGradient(
        listOf(
            hikariColors.transparent,
            hikariColors.artworkScrim.copy(alpha = hikariOpacity.heroBottomScrim),
        ),
    )

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
