package app.openstory.designsystem.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import app.openstory.designsystem.glass.HikariBackdropScope
import app.openstory.designsystem.glass.HikariGlassSurface
import app.openstory.designsystem.theme.hikariColors
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariShapes
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariFloatingNavigation(
    items: List<HikariNavigationItem>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdropScope: HikariBackdropScope? = null,
) {
    validateNavigationSelection(items, selectedKey)
    HikariGlassSurface(
        backdropScope = backdropScope,
        modifier = modifier,
        shape = MaterialTheme.hikariShapes.floatingNavigation,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(MaterialTheme.hikariSpacing.space4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item -> NavigationItem(item, item.key == selectedKey, onSelected) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationItem(
    item: HikariNavigationItem,
    selected: Boolean,
    onSelected: (String) -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.secondary
    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    val color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = MaterialTheme.hikariDimensions.navigationItemMinHeight)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onSelected(item.key) },
            )
            .semantics { this.selected = selected }
            .padding(MaterialTheme.hikariSpacing.space4)
            .background(
                if (selected) selectedContainer else MaterialTheme.hikariColors.transparent,
                MaterialTheme.hikariShapes.navigationSelection,
            )
            .padding(
                horizontal = MaterialTheme.hikariSpacing.space4,
                vertical = MaterialTheme.hikariSpacing.space6,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.hikariSpacing.space2,
            Alignment.CenterVertically,
        ),
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.hikariDimensions.iconMedium),
            tint = color,
        )
        Text(item.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
