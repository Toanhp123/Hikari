package app.openstory.catalog.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.designsystem.content.HikariMetadataBadge
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.matching.ContentMatchDecision
import java.util.Locale
import app.openstory.designsystem.glass.HikariGlassPanel
import app.openstory.designsystem.glass.HikariGlassPanelStyle
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariTypography

@Composable
fun MappingSheet(state: MappingUiState, actions: MappingActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.hikariSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
    ) {
        Text("Reading sources", style = MaterialTheme.hikariTypography.sectionTitle)
        Text(
            "Linked sources stay protected until you explicitly approve a replacement.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CurrentMappings(state.mappings)
        Button(
            onClick = actions.onSearch,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
        ) { Text("Find reading sources") }
        UrlImport(state, actions)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.failures.forEach { failure -> FailureCard(failure) }
        if (state.candidates.isNotEmpty()) {
            Text("Candidates", style = MaterialTheme.hikariTypography.emphasizedTitleMedium)
        }
        state.candidates.forEach { MappingCandidateCard(it, actions) }
    }
}

@Composable
private fun CurrentMappings(mappings: List<MappingItemUiModel>) {
    Text("Linked sources", style = MaterialTheme.hikariTypography.emphasizedTitleMedium)
    if (mappings.isEmpty()) {
        HikariGlassPanel(null, Modifier.fillMaxWidth(), HikariGlassPanelStyle.STANDARD) {
            Text("No reading source linked yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    mappings.forEach { mapping ->
        HikariGlassPanel(null, Modifier.fillMaxWidth(), HikariGlassPanelStyle.STANDARD) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6)) {
                Text(
                    mapping.pluginId.value,
                    style = MaterialTheme.hikariTypography.emphasizedTitleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6)) {
                    HikariMetadataBadge(mapping.origin.displayName())
                    HikariMetadataBadge(mapping.sourceStoryId)
                }
            }
        }
    }
}

@Composable
private fun UrlImport(state: MappingUiState, actions: MappingActions) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        Text("Resolve a known URL", style = MaterialTheme.hikariTypography.emphasizedTitleSmall)
        OutlinedTextField(
            value = state.urlInput,
            onValueChange = actions.onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reading source URL") },
            singleLine = true,
        )
        OutlinedButton(
            onClick = actions.onResolveUrl,
            enabled = !state.busy && state.urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
        ) { Text("Resolve URL") }
    }
}

@Composable
private fun MappingCandidateCard(candidate: MappingCandidateUiModel, actions: MappingActions) {
    HikariGlassPanel(null, Modifier.fillMaxWidth(), HikariGlassPanelStyle.PROMINENT) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space10)) {
            Text(candidate.title, style = MaterialTheme.hikariTypography.emphasizedTitleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space6)) {
                HikariMetadataBadge(candidate.pluginId.value)
                HikariMetadataBadge(candidate.decision.displayName())
                HikariMetadataBadge(candidate.score.asPercent())
            }
            candidate.evidenceLabels.forEach { label ->
                Text(
                    "- $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            candidate.sourceUrl?.let { url ->
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            ) {
                Button(
                    onClick = { actions.onApprove(candidate.pluginId, candidate.sourceStoryId) },
                    modifier = Modifier.weight(1f).heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text(if (candidate.fromUrl) "Use URL source" else "Approve") }
                OutlinedButton(
                    onClick = { actions.onReject(candidate.pluginId, candidate.sourceStoryId) },
                    modifier = Modifier.weight(1f).heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget),
                ) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun FailureCard(failure: String) {
    HikariGlassPanel(null, Modifier.fillMaxWidth(), HikariGlassPanelStyle.COMPACT) {
        Text("Mapping issue: $failure", color = MaterialTheme.colorScheme.error)
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
