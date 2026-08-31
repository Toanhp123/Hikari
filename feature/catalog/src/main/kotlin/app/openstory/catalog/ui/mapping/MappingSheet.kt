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
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.state.ContentState
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionLead
import app.openstory.designsystem.content.HikariSectionTitle
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
    separatedFromPreviousSection: Boolean = false,
) {
    val readyMappings = (state.content as? ContentState.Ready)?.value.orEmpty()
    val commandsEnabled = state.content is ContentState.Ready && !state.busy

    item(key = "mapping-title", contentType = "mapping-header") {
        HikariSectionLead(
            separatedFromPreviousSection = separatedFromPreviousSection,
            header = {
                HikariSectionHeader(
                    title = "Reading sources",
                    subtitle = "Linked sources stay protected until you explicitly approve a replacement.",
                )
            },
            firstContent = { LinkedMappingLeadContent(state, actions) },
        )
    }
    if (readyMappings.isNotEmpty()) {
        items(
            items = readyMappings.drop(1),
            key = { mapping -> "mapping-linked:${mapping.pluginId.value}:${mapping.sourceStoryId}" },
            contentType = { "mapping-card" },
        ) { mapping ->
            CurrentMappingCard(mapping)
        }
    }
    state.observationIssue?.let { failure ->
        item(key = "mapping-observation-issue", contentType = "mapping-feedback") {
            HikariInlineFeedback(
                message = "Reading sources may be outdated",
                supportingText = catalogFailureMessage(
                    failure.code,
                    "Keeping the last linked-source snapshot visible.",
                ),
                actionLabel = "Retry".takeIf { failure.retryable },
                onAction = actions.onRetryObservation.takeIf { failure.retryable },
            )
        }
    }
    item(key = "mapping-search", contentType = "mapping-action") {
        HikariPrimaryAction(
            onClick = actions.onSearch,
            enabled = commandsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Find reading sources") }
    }
    item(key = "mapping-url", contentType = "mapping-form") {
        UrlImport(state, actions, commandsEnabled)
    }
    if (state.busy) {
        item(key = "mapping-progress", contentType = "mapping-progress") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    itemsIndexed(
        items = state.searchFailures,
        key = { index, failure -> "mapping-search-failure:$index:${failure.code}" },
        contentType = { _, _ -> "mapping-feedback" },
    ) { _, failure ->
        HikariInlineFeedback(
            message = "Couldn't find reading sources",
            supportingText = catalogFailureMessage(failure.code, "Try the search again or use another URL."),
        )
    }
    state.actionFailure?.let { failure ->
        item(key = "mapping-action-failure", contentType = "mapping-feedback") {
            HikariInlineFeedback(
                message = "Couldn't update reading sources",
                supportingText = catalogFailureMessage(failure.code, "Try the action again."),
            )
        }
    }
    state.candidates.firstOrNull()?.let { firstCandidate ->
        item(key = "mapping-candidates-title", contentType = "mapping-subheader") {
            HikariSectionLead(
                separatedFromPreviousSection = true,
                header = { HikariSectionTitle("Candidates") },
                firstContent = { MappingCandidateCard(firstCandidate, actions, enabled = commandsEnabled) },
            )
        }
        items(
            items = state.candidates.drop(1),
            key = { candidate -> "mapping-candidate:${candidate.pluginId.value}:${candidate.sourceStoryId}" },
            contentType = { "mapping-candidate" },
        ) { candidate ->
            MappingCandidateCard(candidate, actions, enabled = commandsEnabled)
        }
    }
}

@Composable
private fun LinkedMappingLeadContent(
    state: MappingUiState,
    actions: MappingActions,
) {
    when (val content = state.content) {
        ContentState.Pending -> {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().testTag("mapping-loading"),
            )
        }
        is ContentState.Failed -> {
            HikariInlineFeedback(
                message = "Reading sources unavailable",
                supportingText = catalogFailureMessage(content.failure.code, "Retry the linked-source observation."),
                actionLabel = "Retry".takeIf { content.failure.retryable },
                onAction = actions.onRetryObservation.takeIf { content.failure.retryable },
            )
        }
        is ContentState.Ready -> {
            if (content.value.isEmpty()) {
                HikariContentCard(Modifier.fillMaxWidth()) {
                    Text(
                        "No reading source linked yet",
                        modifier = Modifier.padding(MaterialTheme.hikariSpacing.space16),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CurrentMappingCard(content.value.first())
            }
        }
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
private fun UrlImport(
    state: MappingUiState,
    actions: MappingActions,
    commandsEnabled: Boolean,
) {
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
            enabled = commandsEnabled && state.urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resolve URL") }
    }
}

@Composable
private fun MappingCandidateCard(
    candidate: MappingCandidateUiModel,
    actions: MappingActions,
    enabled: Boolean,
) {
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
                buildList {
                    add(candidate.pluginId.value)
                    add(candidate.decision.displayName())
                    add(candidate.score.asPercent())
                    candidate.replacesSourceStoryId?.let { sourceStoryId ->
                        add("Replaces $sourceStoryId")
                    }
                },
            )
            candidate.evidenceLabels.takeIf { it.isNotEmpty() }?.let { labels ->
                Text(
                    labels.joinToString(separator = " • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text(candidate.approvalLabel()) }
                HikariUtilityAction(
                    onClick = { actions.onReject(candidate.pluginId, candidate.sourceStoryId) },
                    enabled = enabled,
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

private fun MappingCandidateUiModel.approvalLabel(): String = when {
    replacesSourceStoryId != null && fromUrl -> "Replace with URL"
    replacesSourceStoryId != null -> "Replace"
    fromUrl -> "Use URL source"
    else -> "Approve"
}
