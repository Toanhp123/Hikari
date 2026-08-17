package app.openstory.designsystem.control

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import app.openstory.designsystem.glass.HikariBackdropScope
import app.openstory.designsystem.glass.HikariGlassSurface
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariOpacity
import app.openstory.designsystem.theme.hikariShapes

@Composable
fun HikariIconAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    backdropScope: HikariBackdropScope? = null,
    style: HikariIconActionStyle = HikariIconActionStyle.TONAL,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    traversalIndex: Float? = null,
    content: @Composable () -> Unit,
) {
    val dimensions = MaterialTheme.hikariDimensions
    val shape = MaterialTheme.hikariShapes.circle
    val focusModifier = modifier
        .size(dimensions.minimumTouchTarget)
        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .then(
            nextFocusRequester?.let { next ->
                Modifier.focusProperties { this.next = next; down = next }
            } ?: Modifier,
        )
        .semantics {
            this.contentDescription = contentDescription
            traversalIndex?.let { this.traversalIndex = it }
        }

    when (style) {
        HikariIconActionStyle.GLASS -> HikariGlassSurface(
            backdropScope = backdropScope,
            modifier = focusModifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            shape = shape,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        HikariIconActionStyle.ACCENTED_SURFACE -> Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = focusModifier.border(
                dimensions.borderThin,
                MaterialTheme.colorScheme.primary.copy(alpha = MaterialTheme.hikariOpacity.accentBorder),
                shape,
            ),
            shape = shape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.hikariOpacity.surfaceStrong),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
        HikariIconActionStyle.TONAL -> Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = focusModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}
