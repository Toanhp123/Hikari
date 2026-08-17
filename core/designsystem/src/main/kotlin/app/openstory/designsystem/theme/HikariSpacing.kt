package app.openstory.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class HikariSpacing(
    val space4: Dp = 4.dp,
    val space8: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space32: Dp = 32.dp,
)

val HikariDefaultSpacing = HikariSpacing()

internal val LocalHikariSpacing = staticCompositionLocalOf { HikariDefaultSpacing }

val MaterialTheme.hikariSpacing: HikariSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariSpacing.current
