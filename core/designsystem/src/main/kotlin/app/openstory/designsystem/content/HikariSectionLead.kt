package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariSpacing

/**
 * Keeps section rhythm semantic inside a lazy list whose regular items already use the shared item-gap role.
 * The header-to-first-content gap uses sectionContentGap, while later sections add only the delta needed
 * to reach sectionGap from the previous regular item.
 */
@Composable
fun HikariSectionLead(
    modifier: Modifier = Modifier,
    separatedFromPreviousSection: Boolean = false,
    header: @Composable () -> Unit,
    firstContent: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.hikariSpacing
    val sectionModifier = if (separatedFromPreviousSection) {
        modifier.padding(top = spacing.sectionGap - spacing.itemGap)
    } else {
        modifier
    }
    Column(
        modifier = sectionModifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sectionContentGap),
    ) {
        header()
        firstContent()
    }
}
