package app.openstory.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SerifDisplay = FontFamily.Serif
private val SansBody = FontFamily.SansSerif

internal val HikariTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

@Immutable
data class HikariSemanticTypography(
    val brandLabel: TextStyle = HikariTypography.labelLarge.copy(
        fontFamily = SansBody,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.2.sp,
    ),
    val categoryLabel: TextStyle = HikariTypography.labelLarge.copy(
        fontFamily = SansBody,
        fontWeight = FontWeight.Black,
    ),
    val sectionTitle: TextStyle = HikariTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
    ),
    val posterTitle: TextStyle = HikariTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
    val heroEyebrow: TextStyle = HikariTypography.labelMedium.copy(fontWeight = FontWeight.Black),
    val heroTitleCompact: TextStyle = HikariTypography.headlineSmall.copy(fontWeight = FontWeight.Black),
    val heroTitleExpanded: TextStyle = HikariTypography.headlineMedium.copy(fontWeight = FontWeight.Black),
    val heroAction: TextStyle = HikariTypography.bodyLarge.copy(fontWeight = FontWeight.Black),
    val readerBody: TextStyle = HikariTypography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 29.sp),
)

val HikariDefaultSemanticTypography = HikariSemanticTypography()

internal val LocalHikariSemanticTypography = staticCompositionLocalOf { HikariDefaultSemanticTypography }

val MaterialTheme.hikariTypography: HikariSemanticTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariSemanticTypography.current
