package app.openstory.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class HikariLayoutPolicy(
    val compactGridColumns: Int = 2,
)

val HikariDefaultLayoutPolicy = HikariLayoutPolicy()

internal val LocalHikariLayoutPolicy = staticCompositionLocalOf { HikariDefaultLayoutPolicy }

val MaterialTheme.hikariLayoutPolicy: HikariLayoutPolicy
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariLayoutPolicy.current
