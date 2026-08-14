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
    val space2: Dp = 2.dp,
    val space3: Dp = 3.dp,
    val space4: Dp = 4.dp,
    val space5: Dp = 5.dp,
    val space6: Dp = 6.dp,
    val space7: Dp = 7.dp,
    val space8: Dp = 8.dp,
    val space10: Dp = 10.dp,
    val space12: Dp = 12.dp,
    val space14: Dp = 14.dp,
    val space16: Dp = 16.dp,
    val space18: Dp = 18.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space28: Dp = 28.dp,
    val space32: Dp = 32.dp,
)

internal val LocalHikariSpacing = staticCompositionLocalOf { HikariSpacing() }

val MaterialTheme.hikariSpacing: HikariSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariSpacing.current
