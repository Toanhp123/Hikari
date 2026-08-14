package app.openstory.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.icon.HikariBackGlyph
import app.openstory.designsystem.theme.HikariDefaultDimensions
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariDestinationScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier.fillMaxSize().background(containerColor),
            content = content,
        )
    }
}

@Composable
fun PaddingValues.plus(
    start: Dp = HikariDefaultDimensions.zero,
    top: Dp = HikariDefaultDimensions.zero,
    end: Dp = HikariDefaultDimensions.zero,
    bottom: Dp = HikariDefaultDimensions.zero,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + start,
        top = calculateTopPadding() + top,
        end = calculateEndPadding(layoutDirection) + end,
        bottom = calculateBottomPadding() + bottom,
    )
}

@Composable
fun HikariTopLevelHeader(
    title: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp? = null,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onAction: () -> Unit = {},
    content: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = MaterialTheme.hikariSpacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.hikariDimensions.topBarMinHeight)
            .padding(
                horizontal = horizontalPadding ?: spacing.space16,
                vertical = spacing.space8,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            when {
                content != null -> content()
                title != null -> Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Box(Modifier.padding(start = spacing.space12)) {
            action?.invoke() ?: HikariIconAction(
                onClick = onAction,
                contentDescription = "Open quick access",
                focusRequester = focusRequester,
                nextFocusRequester = nextFocusRequester,
                traversalIndex = 1f,
            ) {
                Text(
                    text = "HK",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun HikariFocusedHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = MaterialTheme.hikariDimensions.topBarMinHeight)
            .padding(horizontal = MaterialTheme.hikariSpacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(MaterialTheme.hikariDimensions.minimumTouchTarget)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            HikariBackGlyph(Modifier.size(MaterialTheme.hikariDimensions.iconBack))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
