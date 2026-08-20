package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.openstory.designsystem.surface.HikariBottomSeparationShadow
import app.openstory.designsystem.theme.HikariDefaultDimensions
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            MaterialTheme.hikariSpacing.space8,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        action()
    }
}

fun LazyListScope.hikariSectionHeader(
    key: Any,
    title: String,
    subtitle: String? = null,
    sticky: Boolean = false,
    modifier: Modifier = Modifier,
    contentType: Any? = "hikari-section-header",
    topPadding: Dp = HikariDefaultDimensions.zero,
    stickyBottomSeparation: Boolean = false,
    stickyBottomSeparationEnabled: Boolean = false,
    action: @Composable RowScope.() -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HikariSectionHeader(
                    title = title,
                    subtitle = subtitle,
                    modifier = modifier.padding(
                        top = topPadding,
                        bottom = if (stickyBottomSeparation) {
                            MaterialTheme.hikariDimensions.zero
                        } else {
                            MaterialTheme.hikariSpacing.sectionContentGap
                        },
                    ),
                    action = action,
                )
                if (stickyBottomSeparation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MaterialTheme.hikariSpacing.destinationContentGap),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        HikariBottomSeparationShadow(enabled = stickyBottomSeparationEnabled)
                    }
                }
            }
        }
    }

    if (sticky) {
        stickyHeader(
            key = key,
            contentType = contentType,
        ) {
            content()
        }
    } else {
        item(
            key = key,
            contentType = contentType,
        ) {
            content()
        }
    }
}
