package app.openstory.designsystem.artwork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariListArtworkFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clip(MaterialTheme.hikariShapes.cover),
        content = content,
    )
}
