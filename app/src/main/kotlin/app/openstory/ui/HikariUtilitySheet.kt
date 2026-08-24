package app.openstory.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.navigation.AppRoute
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.layout.HikariModalSheet
import app.openstory.designsystem.layout.HikariSheetContent
import app.openstory.designsystem.theme.hikariDimensions

val utilityDestinations = listOf(
    HikariUtilityDestination(AppRoute.Downloads, "Downloads"),
    HikariUtilityDestination(AppRoute.Updates, "Updates"),
    HikariUtilityDestination(AppRoute.ReconciliationReview(), "Review duplicates"),
)

@Composable
fun HikariUtilitySheet(
    onDismiss: () -> Unit,
    onDestinationSelected: (AppRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HikariModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        HikariSheetContent(title = "Quick access") {
            utilityDestinations.forEach { destination ->
                HikariUtilityAction(
                    onClick = { onDestinationSelected(destination.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text(destination.label, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
