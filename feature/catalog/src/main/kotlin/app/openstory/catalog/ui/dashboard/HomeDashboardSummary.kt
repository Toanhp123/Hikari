package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.surface.HikariContentCardStyle
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun HomeSummary(summary: HomeReadingSummary) {
    HikariContentCard(
        modifier = Modifier.fillMaxWidth(),
        style = HikariContentCardStyle.PROMINENT,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
                Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your library, progress and newest chapters in one place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryMetric(summary.libraryCount, "Library")
                SummaryMetric(summary.readingCount, "Reading")
                SummaryMetric(summary.completedCount, "Completed")
                SummaryMetric(summary.downloadedCount, "Offline")
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: Int, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space4)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ObservationFailure(failure: HomeDashboardFailure, modifier: Modifier = Modifier) {
    HikariInlineFeedback(
        message = "Some reading data could not be refreshed (${failure.code}).",
        modifier = modifier,
    )
}
