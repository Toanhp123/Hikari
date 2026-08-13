package app.openstory.designsystem.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.glass.HikariBackdropScope
import app.openstory.designsystem.glass.HikariGlassSurface

@Immutable
data class HikariNavigationItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

internal fun validateNavigationSelection(
    items: List<HikariNavigationItem>,
    selectedKey: String,
) {
    require(items.isNotEmpty()) { "Floating navigation requires at least one item" }
    require(items.map(HikariNavigationItem::key).distinct().size == items.size) {
        "Floating navigation item keys must be unique"
    }
    require(items.count { it.key == selectedKey } == 1) {
        "Floating navigation requires exactly one selected item"
    }
}

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
        shape = RoundedCornerShape(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelected(item.key) },
                        )
                        .semantics { this.selected = selected }
                        .padding(4.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(28.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
