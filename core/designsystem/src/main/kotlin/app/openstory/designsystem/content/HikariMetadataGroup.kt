package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun HikariMetadataGroup(
    title: String,
    values: Collection<String>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        HikariMetadataBadgeGroup(values.sorted())
    }
}
