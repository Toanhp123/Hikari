package app.openstory.ui

import androidx.compose.runtime.Immutable
import app.openstory.navigation.AppRoute

@Immutable
data class HikariUtilityDestination(
    val route: AppRoute,
    val label: String,
)
