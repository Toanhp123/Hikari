package app.openstory.catalog.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.matching.ContentMatchDecision
import java.util.Locale

@Composable
fun MappingSheet(
    state: MappingUiState,
    actions: MappingActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.hikariSpacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.medium),
    ) {
        Text("Reading sources", style = MaterialTheme.typography.titleLarge)
        CurrentMappings(state.mappings)
        Button(onClick = actions.onSearch, enabled = !state.busy) {
            Text("Find reading sources")
        }
        UrlImport(state, actions)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.failures.forEach { failure ->
            Text("Mapping issue: $failure", color = MaterialTheme.colorScheme.error)
        }
        state.candidates.forEach { candidate ->
            MappingCandidateCard(candidate, actions)
        }
    }
}

@Composable
private fun CurrentMappings(mappings: List<MappingItemUiModel>) {
    if (mappings.isEmpty()) {
        Text("No reading source linked yet")
        return
    }
    mappings.forEach { mapping ->
        Text(
            "${mapping.pluginId.value}: ${mapping.sourceStoryId} · ${mapping.origin.displayName()}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UrlImport(
    state: MappingUiState,
    actions: MappingActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small)) {
        OutlinedTextField(
            value = state.urlInput,
            onValueChange = actions.onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reading source URL") },
            singleLine = true,
        )
        Button(
            onClick = actions.onResolveUrl,
            enabled = !state.busy && state.urlInput.isNotBlank(),
        ) {
            Text("Resolve URL")
        }
    }
}

@Composable
private fun MappingCandidateCard(
    candidate: MappingCandidateUiModel,
    actions: MappingActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(candidate.title, style = MaterialTheme.typography.titleMedium)
        Text("${candidate.pluginId.value} · ${candidate.decision.displayName()} · ${candidate.score.asPercent()}")
        candidate.evidenceLabels.forEach { label -> Text(label, style = MaterialTheme.typography.bodySmall) }
        candidate.sourceUrl?.let { url -> Text(url, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.small)) {
            Button(onClick = { actions.onApprove(candidate.pluginId, candidate.sourceStoryId) }) {
                Text(if (candidate.fromUrl) "Use URL source" else "Approve")
            }
            TextButton(onClick = { actions.onReject(candidate.pluginId, candidate.sourceStoryId) }) {
                Text("Reject")
            }
        }
    }
}

private fun ContentMappingOrigin.displayName(): String = when (this) {
    ContentMappingOrigin.AUTOMATED -> "Automatic"
    ContentMappingOrigin.USER_APPROVED -> "Approved"
    ContentMappingOrigin.USER_URL -> "URL import"
}

private fun ContentMatchDecision.displayName(): String = when (this) {
    ContentMatchDecision.AUTO_LINK -> "Auto link"
    ContentMatchDecision.REVIEW -> "Review"
    ContentMatchDecision.REJECT -> "Reject"
}

private const val PERCENT_MULTIPLIER = 100.0

private fun Double.asPercent(): String = String.format(Locale.ROOT, "%.0f%%", this * PERCENT_MULTIPLIER)
