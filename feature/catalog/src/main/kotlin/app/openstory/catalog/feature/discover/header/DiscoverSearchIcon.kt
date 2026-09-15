@file:Suppress("MagicNumber")

package app.openstory.catalog.feature.discover.header

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val VECTOR_SIZE = 24.dp
private const val VECTOR_VIEWPORT = 24f
private const val STROKE_WIDTH = 2f

@Composable
internal fun DiscoverSearchIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = SearchVector,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

private val SearchVector = ImageVector.Builder(
    name = "Search",
    defaultWidth = VECTOR_SIZE,
    defaultHeight = VECTOR_SIZE,
    viewportWidth = VECTOR_VIEWPORT,
    viewportHeight = VECTOR_VIEWPORT,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(10.5f, 4.5f)
        curveTo(7.19f, 4.5f, 4.5f, 7.19f, 4.5f, 10.5f)
        curveTo(4.5f, 13.81f, 7.19f, 16.5f, 10.5f, 16.5f)
        curveTo(13.81f, 16.5f, 16.5f, 13.81f, 16.5f, 10.5f)
        curveTo(16.5f, 7.19f, 13.81f, 4.5f, 10.5f, 4.5f)
        close()
        moveTo(15f, 15f)
        lineTo(20f, 20f)
    }
}.build()
