package app.openstory.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Immutable
data class HikariDimensions(
    val zero: Dp = 0.dp,
    val borderThin: Dp = 1.dp,
    val surfaceShadowRadius: Dp = 2.dp,
    val glassBlurRadius: Dp = 8.dp,
    val glyphStrokeFine: Dp = 1.7.dp,
    val glyphStroke: Dp = 1.8.dp,
    val glyphDotRadius: Dp = 2.4.dp,
    val iconMedium: Dp = 20.dp,
    val iconStandard: Dp = 24.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val navigationItemMinHeight: Dp = 56.dp,
    val topBarMinHeight: Dp = 64.dp,
    val floatingNavigationClearance: Dp = 92.dp,
    val searchResultMinHeight: Dp = 120.dp,
    val updateRowMinHeight: Dp = 112.dp,
    val summaryCardMinHeight: Dp = 88.dp,
    val adaptiveGridMinCell: Dp = 144.dp,
    val posterSearchWidth: Dp = 68.dp,
    val posterShelfNarrowWidth: Dp = 76.dp,
    val posterShelfWidth: Dp = 88.dp,
    val posterShelfWideWidth: Dp = 104.dp,
    val posterList: DpSize = DpSize(72.dp, 104.dp),
    val posterActivity: DpSize = DpSize(54.dp, 76.dp),
    val posterUpdate: DpSize = DpSize(58.dp, 82.dp),
    val posterDetail: DpSize = DpSize(112.dp, 164.dp),
    val posterDetailCompact: DpSize = DpSize(88.dp, 128.dp),
    val discoverHeroCompactHeight: Dp = 176.dp,
    val discoverHeroExpandedHeight: Dp = 246.dp,
    val discoverHeroCompactPosterWidth: Dp = 96.dp,
    val discoverHeroExpandedPosterWidth: Dp = 156.dp,
    val detailHeroHeight: Dp = 304.dp,
    val detailHeroNarrowHeight: Dp = 380.dp,
    val dashboardFeatureHeight: Dp = 214.dp,
    val dashboardCardWidth: Dp = 220.dp,
    val readerTopInset: Dp = 104.dp,
    val readerBottomInset: Dp = 96.dp,
)

@Immutable
data class HikariBreakpoints(
    val narrowContent: Dp = 380.dp,
    val largePhone: Dp = 412.dp,
    val expandedContent: Dp = 520.dp,
    val medium: Dp = 600.dp,
)

@Immutable
data class HikariLayoutRatios(
    val coverFrameAspectRatio: Float = 0.6666667f,
    val posterCardAspectRatio: Float = 0.68f,
    val detailSummaryPaneWeight: Float = 0.44f,
    val detailContentPaneWeight: Float = 0.56f,
)

val HikariDefaultDimensions = HikariDimensions()
val HikariDefaultBreakpoints = HikariBreakpoints()
val HikariDefaultLayoutRatios = HikariLayoutRatios()

internal val LocalHikariDimensions = staticCompositionLocalOf { HikariDefaultDimensions }
internal val LocalHikariBreakpoints = staticCompositionLocalOf { HikariDefaultBreakpoints }
internal val LocalHikariLayoutRatios = staticCompositionLocalOf { HikariDefaultLayoutRatios }

val MaterialTheme.hikariDimensions: HikariDimensions
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariDimensions.current

val MaterialTheme.hikariLayoutRatios: HikariLayoutRatios
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariLayoutRatios.current

val MaterialTheme.hikariBreakpoints: HikariBreakpoints
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariBreakpoints.current
