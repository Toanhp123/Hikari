package app.openstory.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Coral = Color(0xFFFF7461)
private val CoralLight = Color(0xFFFFDAD4)
private val Teal = Color(0xFF2E8B80)
private val TealLight = Color(0xFFB4CCC6)
private val Cream = Color(0xFFF6F0E8)
private val Paper = Color(0xFFFFF9F2)
private val WarmSurfaceContainer = Color(0xFFF2EAE1)
private val WarmSurfaceContainerHigh = Color(0xFFEFE6DD)
private val WarmSurfaceContainerHighest = Color(0xFFECE3DA)
private val WarmSurfaceDim = Color(0xFFDED4CB)
private val Ink = Color(0xFF241A17)
private val MutedInk = Color(0xFF665F5B)
private val DarkBackground = Color(0xFF101714)
private val DarkSurface = Color(0xFF18211D)
private val DarkSurfaceRaised = Color(0xFF24302A)
private val DarkSurfaceLow = Color(0xFF141C18)
private val DarkSurfaceHigh = Color(0xFF1E2924)
private val DarkText = Color(0xFFF1EDE6)

internal val HikariLightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = CoralLight,
    onPrimaryContainer = Color(0xFF3B0903),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8E2),
    onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF51734F),
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceContainerHighest,
    onSurfaceVariant = MutedInk,
    surfaceBright = Paper,
    surfaceDim = WarmSurfaceDim,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Cream,
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = WarmSurfaceContainerHigh,
    surfaceContainerHighest = WarmSurfaceContainerHighest,
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C8C1),
    error = Color(0xFFBA1A1A),
)

internal val HikariDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8B79),
    onPrimary = Color(0xFF5D160C),
    primaryContainer = Color(0xFF7C2D20),
    onPrimaryContainer = CoralLight,
    secondary = Color(0xFF8FD5CA),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF145047),
    onSecondaryContainer = Color(0xFFACEFE4),
    tertiary = Color(0xFFA5CFA2),
    onTertiary = Color(0xFF113817),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = TealLight,
    surfaceBright = DarkSurfaceRaised,
    surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceRaised,
    outline = Color(0xFFA9958E),
    outlineVariant = Color(0xFF54443F),
    error = Color(0xFFFFB4AB),
)
