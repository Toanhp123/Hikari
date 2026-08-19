package app.openstory.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.openstory.designsystem.control.HikariScrollToTopAction
import app.openstory.designsystem.icon.HikariBackGlyph
import app.openstory.designsystem.surface.HikariBottomSeparationShadow
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
fun HikariSafeDestinationViewport(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val topInset = contentPadding.calculateTopPadding()
    val safeBodyPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding(),
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset),
    ) {
        content(safeBodyPadding)
    }
}

@Composable
fun HikariStickyDestinationScaffold(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    headerScrolled: Boolean = false,
    showScrollToTop: Boolean = false,
    onScrollToTop: () -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    HikariSafeDestinationViewport(contentPadding, modifier) { safeBodyPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val startInset = safeBodyPadding.calculateStartPadding(layoutDirection)
        val endInset = safeBodyPadding.calculateEndPadding(layoutDirection)
        val bottomInset = safeBodyPadding.calculateBottomPadding()
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startInset, end = endInset),
            ) {
                header()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MaterialTheme.hikariSpacing.space16),
                contentAlignment = Alignment.TopCenter,
            ) {
                HikariBottomSeparationShadow(enabled = headerScrolled)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content(safeBodyPadding)
                if (showScrollToTop) {
                    HikariScrollToTopAction(
                        onClick = onScrollToTop,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = endInset + MaterialTheme.hikariSpacing.space16,
                                bottom = bottomInset + MaterialTheme.hikariSpacing.space16,
                            ),
                    )
                }
            }
        }
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
fun PaddingValues.withTop(top: Dp): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = top,
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}

@Composable
fun HikariTopLevelHeader(
    title: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp? = null,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
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
        if (action != null || onAction != null) {
            Box(Modifier.padding(start = spacing.space12)) {
                action?.invoke() ?: HikariIconAction(
                    onClick = requireNotNull(onAction),
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
            HikariBackGlyph(Modifier.size(MaterialTheme.hikariDimensions.iconStandard))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
