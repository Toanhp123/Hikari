package app.openstory.designsystem.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight

@Composable
fun HikariArtworkFrame(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val fallbackBrush = remember(tertiaryContainer, primaryContainer, surfaceVariant) {
        Brush.linearGradient(
            listOf(
                tertiaryContainer,
                primaryContainer,
                surfaceVariant,
            ),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fallbackBrush),
        contentAlignment = Alignment.Center,
    ) {
        title.firstOrNull()?.uppercase()?.let { initial ->
            Text(
                text = initial,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            )
        }
        Box(Modifier.fillMaxSize(), content = content)
    }
}
