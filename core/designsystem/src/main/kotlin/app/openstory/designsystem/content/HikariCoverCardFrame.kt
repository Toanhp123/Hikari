package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.openstory.designsystem.theme.hikariLayoutRatios
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariCoverCardFrame(
    modifier: Modifier = Modifier,
    variant: HikariCoverCardVariant = HikariCoverCardVariant.STANDARD,
    content: @Composable BoxScope.() -> Unit,
) {
    val aspectRatio = when (variant) {
        HikariCoverCardVariant.STANDARD -> MaterialTheme.hikariLayoutRatios.coverFrameAspectRatio
        HikariCoverCardVariant.POSTER -> MaterialTheme.hikariLayoutRatios.posterCardAspectRatio
    }
    val shape = when (variant) {
        HikariCoverCardVariant.STANDARD -> MaterialTheme.hikariShapes.cover
        HikariCoverCardVariant.POSTER -> MaterialTheme.hikariShapes.compactCard
    }
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(shape),
        content = content,
    )
}
