package app.openstory.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.openstory.navigation.TopLevelDestination

internal val TopLevelDestination.navigationIcon: ImageVector
    get() = ImageVector.Builder(
        name = label,
        defaultWidth = ICON_SIZE.dp,
        defaultHeight = ICON_SIZE.dp,
        viewportWidth = ICON_SIZE,
        viewportHeight = ICON_SIZE,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(iconPathData).toNodes(),
            fill = SolidColor(Color.White),
        )
    }.build()

private val TopLevelDestination.iconPathData: String
    get() = when (this) {
        TopLevelDestination.Discover -> "M12,2 L15,9 L22,12 L15,15 L12,22 L9,15 L2,12 L9,9 Z"
        TopLevelDestination.Home -> "M3,11 L12,3 L21,11 L19,11 L19,21 L5,21 L5,11 Z"
        TopLevelDestination.Library -> "M3,4 L10,4 L12,6 L14,4 L21,4 L21,20 L14,20 L12,18 L10,20 L3,20 Z"
    }

private const val ICON_SIZE = 24f
