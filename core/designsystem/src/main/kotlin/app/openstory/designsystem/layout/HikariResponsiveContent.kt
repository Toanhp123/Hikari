package app.openstory.designsystem.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class HikariWindowClass { COMPACT, LARGE_PHONE, MEDIUM }

fun classifyWindow(maxWidth: Dp): HikariWindowClass = when {
    maxWidth >= MediumWidth -> HikariWindowClass.MEDIUM
    maxWidth >= LargePhoneWidth -> HikariWindowClass.LARGE_PHONE
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
            windowClass = classifyWindow(maxWidth),
            maxWidth = maxWidth,
        ).content()
    }
}

private val LargePhoneWidth = 412.dp
private val MediumWidth = 600.dp
