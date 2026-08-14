package app.openstory.designsystem.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        values.sorted().forEach { value ->
            HikariMetadataBadge(
                label = value,
                modifier = Modifier.padding(end = MaterialTheme.hikariSpacing.space6),
            )
        }
    }
}
