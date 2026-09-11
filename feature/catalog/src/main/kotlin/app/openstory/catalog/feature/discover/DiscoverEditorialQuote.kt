package app.openstory.catalog.feature.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun EditorialQuoteCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            QUOTE_CARD_BORDER_WIDTH,
            colors.primary.copy(alpha = QUOTE_CARD_BORDER_ALPHA),
        ),
        tonalElevation = QUOTE_CARD_ELEVATION,
        shadowElevation = QUOTE_CARD_SHADOW,
    ) {
        EditorialQuoteContent(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.primaryContainer,
                            colors.surfaceContainer,
                            colors.secondaryContainer,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun EditorialQuoteContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(MaterialTheme.hikariSpacing.space24),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(
                text = "\u201cA good story stays with you.\u201d",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "\u2014  H I K A R I  \u2014",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = QUOTE_LETTER_SPACING,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private val QUOTE_CARD_BORDER_WIDTH = 1.dp
private val QUOTE_CARD_ELEVATION = 2.dp
private val QUOTE_CARD_SHADOW = 4.dp
private val QUOTE_LETTER_SPACING = 3.sp
private const val QUOTE_CARD_BORDER_ALPHA = 0.22f
