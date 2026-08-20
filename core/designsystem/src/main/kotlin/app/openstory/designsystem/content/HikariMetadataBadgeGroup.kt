package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariMetadataBadgeGroup(
    labels: Collection<String>,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        labels.forEach { label -> HikariMetadataBadge(label) }
    }
}
