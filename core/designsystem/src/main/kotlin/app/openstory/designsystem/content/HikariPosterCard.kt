package app.openstory.designsystem.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariPosterCard(
    title: String,
    supportingText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    geometry: HikariPosterGeometry? = null,
    artwork: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        HikariArtworkFrame(
            title = title,
            modifier = Modifier
                .fillMaxWidth()
                .then(geometry?.let { Modifier.aspectRatio(it.artworkAspectRatio) } ?: Modifier)
                .then(artworkModifier),
            content = artwork,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        supportingText?.let { supporting ->
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
