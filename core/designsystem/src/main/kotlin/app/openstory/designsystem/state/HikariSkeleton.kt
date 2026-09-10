package app.openstory.designsystem.state

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme

@Composable
fun HikariSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape),
    )
}
