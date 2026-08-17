package app.openstory.catalog.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun HomeSummary(summary: HomeReadingSummary) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MaterialTheme.hikariDimensions.dashboardFeatureHeight)
            .padding(MaterialTheme.hikariSpacing.space20),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            Text("Your library, progress and newest chapters in one place.", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space16)) {
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
    Column {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ObservationFailure(failure: HomeDashboardFailure, modifier: Modifier = Modifier) {
    HikariInlineFeedback(
        message = "Some reading data could not be refreshed (${failure.code}).",
        modifier = modifier,
    )
}
