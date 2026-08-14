package app.openstory.designsystem.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariGlyphGeometry
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariBackGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
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
    Canvas(
        modifier
            .size(dimensions.minimumTouchTarget)
            .padding(MaterialTheme.hikariSpacing.space14),
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
    Canvas(
        modifier
            .size(dimensions.minimumTouchTarget)
            .padding(MaterialTheme.hikariSpacing.space14),
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
fun HikariRefreshGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val dimensions = MaterialTheme.hikariDimensions
    val geometry = MaterialTheme.hikariGlyphGeometry.refresh
    Canvas(
        modifier
            .size(dimensions.iconStandard)
            .padding(MaterialTheme.hikariSpacing.space3),
    ) {
        val inset = size.minDimension * geometry.arcInsetFraction
        val arcBounds = Rect(inset, inset, size.width - inset, size.height - inset)
        drawArc(
            color = color,
            startAngle = geometry.startAngleDegrees,
            sweepAngle = geometry.sweepAngleDegrees,
            useCenter = false,
            topLeft = arcBounds.topLeft,
            size = arcBounds.size,
            style = Stroke(width = dimensions.glyphStroke.toPx(), cap = StrokeCap.Round),
        )
        val tip = Offset(size.width * geometry.arrowTipX, size.height * geometry.arrowTipY)
        drawLine(
            color = color,
            start = Offset(size.width * geometry.arrowUpperX, size.height * geometry.arrowUpperY),
            end = tip,
            strokeWidth = dimensions.glyphStroke.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * geometry.arrowLowerX, size.height * geometry.arrowLowerY),
            end = tip,
            strokeWidth = dimensions.glyphStroke.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
