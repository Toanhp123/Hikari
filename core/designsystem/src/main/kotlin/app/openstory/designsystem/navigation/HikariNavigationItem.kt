package app.openstory.designsystem.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

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
