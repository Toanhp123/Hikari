package app.openstory.designsystem.icon

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import app.openstory.designsystem.theme.HikariDefaultDimensions
import app.openstory.designsystem.theme.HikariDefaultSemanticColors

object HikariNavigationGlyphs {
    val discover: ImageVector = navigationGlyph(
        name = "Discover",
        pathData = "M12,2 L15,9 L22,12 L15,15 L12,22 L9,15 L2,12 L9,9 Z",
    )
    val home: ImageVector = navigationGlyph(
        name = "Home",
        pathData = "M3,11 L12,3 L21,11 L19,11 L19,21 L5,21 L5,11 Z",
    )
    val library: ImageVector = navigationGlyph(
        name = "Library",
        pathData = "M3,4 L10,4 L12,6 L14,4 L21,4 L21,20 L14,20 L12,18 L10,20 L3,20 Z",
    )
}

private fun navigationGlyph(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = HikariDefaultDimensions.iconStandard,
    defaultHeight = HikariDefaultDimensions.iconStandard,
    viewportWidth = HikariDefaultDimensions.iconStandard.value,
    viewportHeight = HikariDefaultDimensions.iconStandard.value,
).apply {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(HikariDefaultSemanticColors.onArtwork),
    )
}.build()
