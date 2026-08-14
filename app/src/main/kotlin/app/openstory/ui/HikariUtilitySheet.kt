package app.openstory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.navigation.AppRoute
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

val utilityDestinations = listOf(
    HikariUtilityDestination(AppRoute.Downloads, "Downloads"),
    HikariUtilityDestination(AppRoute.Updates, "Updates"),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HikariUtilitySheet(
    onDismiss: () -> Unit,
    onDestinationSelected: (AppRoute) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.hikariSpacing.space20,
                    vertical = MaterialTheme.hikariSpacing.space12,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text("Quick access", style = MaterialTheme.typography.titleLarge)
            utilityDestinations.forEach { destination ->
                TextButton(
                    onClick = { onDestinationSelected(destination.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text(destination.label, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
