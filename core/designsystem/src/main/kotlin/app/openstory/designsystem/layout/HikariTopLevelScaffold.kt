package app.openstory.designsystem.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HikariTopLevelScaffold(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    headerScrolled: Boolean = false,
    showScrollToTop: Boolean = false,
    onScrollToTop: () -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    HikariStickyDestinationScaffold(
        contentPadding = contentPadding,
        modifier = modifier,
        header = header,
        headerScrolled = headerScrolled,
        showScrollToTop = showScrollToTop,
        onScrollToTop = onScrollToTop,
        content = content,
    )
}
