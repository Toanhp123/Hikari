package app.openstory.catalog.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal enum class CatalogScreenState {
    Activating,
    Ready,
    SourceUnavailable,
    Failed,
}

@Composable
internal fun CatalogScreen(state: CatalogScreenState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("catalog-discover"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            modifier = Modifier.testTag(state.testTag),
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val CatalogScreenState.testTag: String
    get() = when (this) {
        CatalogScreenState.Activating -> "catalog-activating"
        CatalogScreenState.Ready -> "catalog-ready"
        CatalogScreenState.SourceUnavailable -> "catalog-source-unavailable"
        CatalogScreenState.Failed -> "catalog-failed"
    }

private val CatalogScreenState.message: String
    get() = when (this) {
        CatalogScreenState.Activating -> "Preparing your catalog"
        CatalogScreenState.Ready -> "Catalog ready"
        CatalogScreenState.SourceUnavailable -> "No catalog source is available in this build"
        CatalogScreenState.Failed -> "Catalog is temporarily unavailable"
    }
