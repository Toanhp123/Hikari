package app.openstory.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val HikariShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Immutable
data class HikariSemanticShapes(
    val compactCard: Shape = RoundedCornerShape(18.dp),
    val contentCard: Shape = RoundedCornerShape(20.dp),
    val prominentCard: Shape = RoundedCornerShape(22.dp),
    val sheetCard: Shape = RoundedCornerShape(24.dp),
    val hero: Shape = RoundedCornerShape(28.dp),
    val floatingNavigation: Shape = RoundedCornerShape(36.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
    val cover: Shape = RoundedCornerShape(percent = 10),
    val circle: Shape = CircleShape,
)

internal val LocalHikariSemanticShapes = staticCompositionLocalOf { HikariSemanticShapes() }

val MaterialTheme.hikariShapes: HikariSemanticShapes
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariSemanticShapes.current
