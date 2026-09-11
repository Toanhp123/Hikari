@file:Suppress("MagicNumber")

package app.openstory.catalog.feature.presentation

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
internal fun SearchIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = CatalogIcon(SearchVector, size, tint, modifier)

@Composable
internal fun BackArrowIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = CatalogIcon(BackVector, size, tint, modifier)

@Composable
internal fun HeartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = CatalogIcon(HeartVector, size, tint, modifier)

@Composable
internal fun BookmarkIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = CatalogIcon(BookmarkVector, size, tint, modifier)

@Composable
internal fun ShareIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = CatalogIcon(ShareVector, size, tint, modifier)

@Composable
internal fun MoreIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) = CatalogIcon(MoreVector, size, tint, modifier)

@Composable
private fun CatalogIcon(
    vector: ImageVector,
    size: Dp,
    tint: Color,
    modifier: Modifier,
) {
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

private val SearchVector = strokeIcon("Search") {
    moveTo(10.5f, 4.5f)
    curveTo(7.19f, 4.5f, 4.5f, 7.19f, 4.5f, 10.5f)
    curveTo(4.5f, 13.81f, 7.19f, 16.5f, 10.5f, 16.5f)
    curveTo(13.81f, 16.5f, 16.5f, 13.81f, 16.5f, 10.5f)
    curveTo(16.5f, 7.19f, 13.81f, 4.5f, 10.5f, 4.5f)
    close()
    moveTo(15f, 15f)
    lineTo(20f, 20f)
}

private val BackVector = strokeIcon("Back") {
    moveTo(15f, 5f)
    lineTo(8f, 12f)
    lineTo(15f, 19f)
}

private val HeartVector = strokeIcon("Heart") {
    moveTo(12f, 20.5f)
    curveTo(9f, 17.7f, 4f, 14.2f, 4f, 9.5f)
    curveTo(4f, 6.8f, 6.1f, 4.8f, 8.8f, 4.8f)
    curveTo(10.3f, 4.8f, 11.4f, 5.5f, 12f, 6.5f)
    curveTo(12.6f, 5.5f, 13.7f, 4.8f, 15.2f, 4.8f)
    curveTo(17.9f, 4.8f, 20f, 6.8f, 20f, 9.5f)
    curveTo(20f, 14.2f, 15f, 17.7f, 12f, 20.5f)
    close()
}

private val BookmarkVector = strokeIcon("Bookmark") {
    moveTo(7f, 4f)
    lineTo(17f, 4f)
    lineTo(17f, 20f)
    lineTo(12f, 16.5f)
    lineTo(7f, 20f)
    close()
}

private val ShareVector = strokeIcon("Share") {
    moveTo(12f, 15f)
    lineTo(12f, 4f)
    moveTo(8f, 8f)
    lineTo(12f, 4f)
    lineTo(16f, 8f)
    moveTo(6f, 11f)
    lineTo(6f, 20f)
    lineTo(18f, 20f)
    lineTo(18f, 11f)
}

private val MoreVector = ImageVector.Builder(
    name = "More",
    defaultWidth = VECTOR_SIZE,
    defaultHeight = VECTOR_SIZE,
    viewportWidth = VECTOR_VIEWPORT,
    viewportHeight = VECTOR_VIEWPORT,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 10.5f)
        curveTo(4.17f, 10.5f, 3.5f, 11.17f, 3.5f, 12f)
        curveTo(3.5f, 12.83f, 4.17f, 13.5f, 5f, 13.5f)
        curveTo(5.83f, 13.5f, 6.5f, 12.83f, 6.5f, 12f)
        curveTo(6.5f, 11.17f, 5.83f, 10.5f, 5f, 10.5f)
        close()
        moveTo(12f, 10.5f)
        curveTo(11.17f, 10.5f, 10.5f, 11.17f, 10.5f, 12f)
        curveTo(10.5f, 12.83f, 11.17f, 13.5f, 12f, 13.5f)
        curveTo(12.83f, 13.5f, 13.5f, 12.83f, 13.5f, 12f)
        curveTo(13.5f, 11.17f, 12.83f, 10.5f, 12f, 10.5f)
        close()
        moveTo(19f, 10.5f)
        curveTo(18.17f, 10.5f, 17.5f, 11.17f, 17.5f, 12f)
        curveTo(17.5f, 12.83f, 18.17f, 13.5f, 19f, 13.5f)
        curveTo(19.83f, 13.5f, 20.5f, 12.83f, 20.5f, 12f)
        curveTo(20.5f, 11.17f, 19.83f, 10.5f, 19f, 10.5f)
        close()
    }
}.build()

private fun strokeIcon(
    name: String,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
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
        pathBuilder = pathBuilder,
    )
}.build()
