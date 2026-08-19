package app.openstory.designsystem.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariGlyphGeometry

@Composable
fun HikariBackGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val stroke = MaterialTheme.hikariDimensions.glyphStroke
    val geometry = MaterialTheme.hikariGlyphGeometry.back
    Canvas(modifier) {
        val strokePx = stroke.toPx()
        val turn = Offset(size.width * geometry.innerX, size.height * geometry.centerY)
        drawLine(
            color = color,
            start = Offset(size.width * geometry.outerX, size.height * geometry.upperY),
            end = turn,
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = turn,
            end = Offset(size.width * geometry.outerX, size.height * geometry.lowerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HikariSearchGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val stroke = MaterialTheme.hikariDimensions.glyphStroke
    val geometry = MaterialTheme.hikariGlyphGeometry.search
    Canvas(modifier) {
        val strokePx = stroke.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * geometry.ringRadius,
            center = Offset(
                size.width * geometry.ringCenterX,
                size.height * geometry.ringCenterY,
            ),
            style = Stroke(width = strokePx),
        )
        drawLine(
            color = color,
            start = Offset(
                size.width * geometry.handleStart,
                size.height * geometry.handleStart,
            ),
            end = Offset(
                size.width * geometry.handleEnd,
                size.height * geometry.handleEnd,
            ),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HikariDisclosureGlyph(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    HikariChevronGlyph(
        modifier = modifier.rotate(if (expanded) DISCLOSURE_EXPANDED_ROTATION else DISCLOSURE_COLLAPSED_ROTATION),
        color = color,
    )
}

@Composable
fun HikariChevronGlyph(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val stroke = MaterialTheme.hikariDimensions.glyphStroke
    val geometry = MaterialTheme.hikariGlyphGeometry.back
    Canvas(modifier) {
        val strokePx = stroke.toPx()
        val turn = Offset(
            size.width * (1f - geometry.innerX),
            size.height * geometry.centerY,
        )
        val outerX = size.width * (1f - geometry.outerX)
        drawLine(
            color = color,
            start = Offset(outerX, size.height * geometry.upperY),
            end = turn,
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = turn,
            end = Offset(outerX, size.height * geometry.lowerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun HikariFilterGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val dimensions = MaterialTheme.hikariDimensions
    val geometry = MaterialTheme.hikariGlyphGeometry.filter
    val glyphPadding = (dimensions.minimumTouchTarget - dimensions.iconMedium) / 2f
    Canvas(
        modifier
            .size(dimensions.minimumTouchTarget)
            .padding(glyphPadding),
    ) {
        val stroke = dimensions.glyphStroke.toPx()
        val lines = listOf(
            geometry.firstLineY to geometry.firstKnobX,
            geometry.secondLineY to geometry.secondKnobX,
            geometry.thirdLineY to geometry.thirdKnobX,
        )
        lines.forEach { (y, knob) ->
            drawLine(
                color = color,
                start = Offset(size.width * geometry.lineStartX, size.height * y),
                end = Offset(size.width * geometry.lineEndX, size.height * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = dimensions.glyphDotRadius.toPx(),
                center = Offset(size.width * knob, size.height * y),
            )
        }
    }
}

@Composable
fun HikariGridGlyph(modifier: Modifier = Modifier) = HikariViewGlyph(grid = true, modifier = modifier)

@Composable
fun HikariListGlyph(modifier: Modifier = Modifier) = HikariViewGlyph(grid = false, modifier = modifier)

@Composable
private fun HikariViewGlyph(grid: Boolean, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val dimensions = MaterialTheme.hikariDimensions
    val geometry = MaterialTheme.hikariGlyphGeometry.view
    val glyphPadding = (dimensions.minimumTouchTarget - dimensions.iconMedium) / 2f
    Canvas(
        modifier
            .size(dimensions.minimumTouchTarget)
            .padding(glyphPadding),
    ) {
        val stroke = dimensions.glyphStrokeFine.toPx()
        if (grid) {
            val cell = size.minDimension * geometry.gridCellFraction
            val offsets = listOf(
                geometry.lineStartX to geometry.lineStartX,
                geometry.gridOffsetFraction to geometry.lineStartX,
                geometry.lineStartX to geometry.gridOffsetFraction,
                geometry.gridOffsetFraction to geometry.gridOffsetFraction,
            )
            offsets.forEach { (x, y) ->
                drawRect(
                    color = color,
                    topLeft = Offset(size.width * x, size.height * y),
                    size = Size(cell, cell),
                    style = Stroke(stroke),
                )
            }
        } else {
            val linePositions = listOf(
                geometry.firstListLineY,
                geometry.secondListLineY,
                geometry.thirdListLineY,
            )
            linePositions.forEach { y ->
                drawLine(
                    color = color,
                    start = Offset(size.width * geometry.lineStartX, size.height * y),
                    end = Offset(size.width * geometry.lineEndX, size.height * y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun HikariMoreGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val dimensions = MaterialTheme.hikariDimensions
    val geometry = MaterialTheme.hikariGlyphGeometry.more
    val glyphPadding = (dimensions.minimumTouchTarget - dimensions.iconMedium) / 2f
    Canvas(
        modifier
            .size(dimensions.minimumTouchTarget)
            .padding(glyphPadding),
    ) {
        val radius = dimensions.glyphDotRadius.toPx()
        listOf(geometry.firstDotX, geometry.secondDotX, geometry.thirdDotX).forEach { x ->
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(size.width * x, size.height * geometry.centerY),
            )
        }
    }
}

@Composable
fun HikariUpGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val dimensions = MaterialTheme.hikariDimensions
    val geometry = MaterialTheme.hikariGlyphGeometry.back
    Canvas(modifier) {
        val strokePx = dimensions.glyphStroke.toPx()
        val tip = Offset(size.width * geometry.centerY, size.height * geometry.innerX)
        val baseY = size.height * geometry.outerX
        drawLine(
            color = color,
            start = Offset(size.width * geometry.upperY, baseY),
            end = tip,
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = tip,
            end = Offset(size.width * geometry.lowerY, baseY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

private const val DISCLOSURE_COLLAPSED_ROTATION = 0f
private const val DISCLOSURE_EXPANDED_ROTATION = 90f
