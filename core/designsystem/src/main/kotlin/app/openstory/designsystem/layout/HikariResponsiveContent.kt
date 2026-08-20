package app.openstory.designsystem.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.openstory.designsystem.theme.HikariBreakpoints
import app.openstory.designsystem.theme.HikariDefaultBreakpoints
import app.openstory.designsystem.theme.hikariBreakpoints

enum class HikariWindowClass { COMPACT, LARGE_PHONE, MEDIUM }

fun classifyWindow(
    maxWidth: Dp,
    breakpoints: HikariBreakpoints = HikariDefaultBreakpoints,
): HikariWindowClass = when {
    maxWidth >= breakpoints.medium -> HikariWindowClass.MEDIUM
    maxWidth >= breakpoints.largePhone -> HikariWindowClass.LARGE_PHONE
    else -> HikariWindowClass.COMPACT
}

@Immutable
data class HikariResponsiveContentScope(
    val windowClass: HikariWindowClass,
    val maxWidth: Dp,
)

@Composable
fun HikariResponsiveContent(
    modifier: Modifier = Modifier,
    content: @Composable HikariResponsiveContentScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        HikariResponsiveContentScope(
            windowClass = classifyWindow(maxWidth, MaterialTheme.hikariBreakpoints),
            maxWidth = maxWidth,
        ).content()
    }
}
