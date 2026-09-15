package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.state.HikariSkeleton
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariPosterSkeleton(
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    geometry: HikariPosterGeometry? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        HikariSkeleton(
            modifier = Modifier
                .fillMaxWidth()
                .then(geometry?.let { Modifier.aspectRatio(it.artworkAspectRatio) } ?: Modifier)
                .then(artworkModifier),
            shape = MaterialTheme.shapes.medium,
        )
        HikariSkeleton(
            modifier = Modifier.fillMaxWidth().height(16.dp),
            shape = MaterialTheme.shapes.small,
        )
        HikariSkeleton(
            modifier = Modifier.width(72.dp).height(12.dp),
            shape = MaterialTheme.shapes.small,
        )
    }
}
