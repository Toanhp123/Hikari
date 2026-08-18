package app.openstory.catalog.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.content.HikariSectionTitle
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.surface.HikariContentCardStyle
import app.openstory.designsystem.theme.hikariSpacing
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.matching.ContentMatchDecision
import java.util.Locale

fun LazyListScope.mappingItems(
    state: MappingUiState,
    actions: MappingActions,
) {
    item(key = "mapping-title", contentType = "mapping-header") {
        HikariSectionTitle("Reading sources")
    }
    item(key = "mapping-description", contentType = "mapping-copy") {
        Text(
            "Linked sources stay protected until you explicitly approve a replacement.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "mapping-linked-title", contentType = "mapping-subheader") {
        Text("Linked sources", style = MaterialTheme.typography.titleMedium)
    }
    when {
        state.loading && state.mappings.isEmpty() -> {
            item(key = "mapping-linked-loading", contentType = "mapping-progress") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag("mapping-loading"),
                )
            }
        }
        state.mappings.isEmpty() -> {
            item(key = "mapping-linked-empty", contentType = "mapping-card") {
                HikariContentCard(Modifier.fillMaxWidth()) {
                    Text(
                        "No reading source linked yet",
                        modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> {
            items(
                items = state.mappings,
                key = { mapping -> "mapping-linked:${mapping.pluginId.value}:${mapping.sourceStoryId}" },
                contentType = { "mapping-card" },
            ) { mapping ->
                CurrentMappingCard(mapping)
            }
        }
    }
    item(key = "mapping-search", contentType = "mapping-action") {
        HikariPrimaryAction(
            onClick = actions.onSearch,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Find reading sources") }
    }
    item(key = "mapping-url", contentType = "mapping-form") {
        UrlImport(state, actions)
    }
    if (state.busy) {
        item(key = "mapping-progress", contentType = "mapping-progress") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    itemsIndexed(
        items = state.failures,
        key = { index, failure -> "mapping-failure:$index:$failure" },
        contentType = { _, _ -> "mapping-feedback" },
    ) { _, failure ->
        HikariInlineFeedback(message = "Mapping issue: $failure")
    }
    if (state.candidates.isNotEmpty()) {
        item(key = "mapping-candidates-title", contentType = "mapping-subheader") {
            Text("Candidates", style = MaterialTheme.typography.titleMedium)
        }
    }
    items(
        items = state.candidates,
        key = { candidate -> "mapping-candidate:${candidate.pluginId.value}:${candidate.sourceStoryId}" },
        contentType = { "mapping-candidate" },
    ) { candidate ->
        MappingCandidateCard(candidate, actions)
    }
}

@Composable
private fun CurrentMappingCard(mapping: MappingItemUiModel) {
    HikariContentCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text(mapping.pluginId.value, style = MaterialTheme.typography.titleSmall)
            HikariMetadataBadgeGroup(listOf(mapping.origin.displayName(), mapping.sourceStoryId))
        }
    }
}

@Composable
private fun UrlImport(state: MappingUiState, actions: MappingActions) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
        Text("Resolve a known URL", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.urlInput,
            onValueChange = actions.onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reading source URL") },
            singleLine = true,
        )
        HikariUtilityAction(
            onClick = actions.onResolveUrl,
            enabled = !state.busy && state.urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resolve URL") }
    }
}

@Composable
private fun MappingCandidateCard(candidate: MappingCandidateUiModel, actions: MappingActions) {
    HikariContentCard(
        modifier = Modifier.fillMaxWidth(),
        style = HikariContentCardStyle.PROMINENT,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        ) {
            Text(candidate.title, style = MaterialTheme.typography.titleMedium)
            HikariMetadataBadgeGroup(
                listOf(
                    candidate.pluginId.value,
                    candidate.decision.displayName(),
                    candidate.score.asPercent(),
                ),
            )
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
                HikariPrimaryAction(
                    onClick = { actions.onApprove(candidate.pluginId, candidate.sourceStoryId) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (candidate.fromUrl) "Use URL source" else "Approve") }
                HikariInlineAction(
                    onClick = { actions.onReject(candidate.pluginId, candidate.sourceStoryId) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reject") }
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
