package app.openstory.catalog.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openstory.common.id.PluginId
import app.openstory.designsystem.artwork.HikariArtwork
import app.openstory.designsystem.artwork.HikariArtworkModel
import app.openstory.designsystem.artwork.HikariListArtworkFrame
import app.openstory.designsystem.artwork.rememberHikariArtwork
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.layout.HikariDestinationScaffold
import app.openstory.designsystem.layout.HikariFocusedHeader
import app.openstory.designsystem.layout.HikariModalSheet
import app.openstory.designsystem.layout.HikariSheetContent
import app.openstory.designsystem.layout.HikariStickyDestinationScaffold
import app.openstory.designsystem.layout.withScreenContentInsets
import app.openstory.designsystem.state.HikariEmptyState
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ReconciliationReviewScreen(
    state: ReconciliationReviewUiState,
    onBack: () -> Unit,
    onMerge: (String, Long) -> Unit,
    onKeepSeparate: (String, Long) -> Unit,
    onDefer: (String, Long) -> Unit,
    onProtectedMappingSelected: (PluginId, String) -> Unit,
    onConfirmProtectedMerge: () -> Unit,
    onDismissProtectedConflict: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    HikariDestinationScaffold(modifier) {
        HikariStickyDestinationScaffold(
            contentPadding = contentPadding,
            header = { HikariFocusedHeader("Review duplicates", onBack) },
        ) { bodyPadding ->
            ReviewQueueContent(state, bodyPadding, onMerge, onKeepSeparate, onDefer)
        }
    }
    state.protectedConflict?.let { conflict ->
        ProtectedConflictSheet(
            conflict = conflict,
            resolving = state.resolvingCaseId == conflict.caseId,
            onSelected = onProtectedMappingSelected,
            onConfirm = onConfirmProtectedMerge,
            onDismiss = onDismissProtectedConflict,
        )
    }
}

@Composable
private fun ReviewQueueContent(
    state: ReconciliationReviewUiState,
    bodyPadding: PaddingValues,
    onMerge: (String, Long) -> Unit,
    onKeepSeparate: (String, Long) -> Unit,
    onDefer: (String, Long) -> Unit,
) {
    if (state.items.isEmpty()) {
        EmptyReviewQueue(state.failureMessage, bodyPadding)
    } else {
        ReviewQueueList(state, bodyPadding, onMerge, onKeepSeparate, onDefer)
    }
}

@Composable
private fun EmptyReviewQueue(failureMessage: String?, bodyPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bodyPadding.withScreenContentInsets()),
    ) {
        HikariEmptyState(
            title = "No duplicates to review",
            message = "Ambiguous matches will appear here when they need your decision.",
        )
        failureMessage?.let { message ->
            HikariInlineFeedback(message = message, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ReviewQueueList(
    state: ReconciliationReviewUiState,
    bodyPadding: PaddingValues,
    onMerge: (String, Long) -> Unit,
    onKeepSeparate: (String, Long) -> Unit,
    onDefer: (String, Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = bodyPadding.withScreenContentInsets(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        state.failureMessage?.let { message ->
            item(key = "review-failure") { HikariInlineFeedback(message = message) }
        }
        if (state.domainConflictReasonLabels.isNotEmpty()) {
            item(key = "review-domain-conflict") { DomainConflictCard(state.domainConflictReasonLabels) }
        }
        items(state.items, key = ReconciliationReviewItemUiModel::caseId) { item ->
            ReconciliationReviewCard(
                item = item,
                resolving = state.resolvingCaseId == item.caseId,
                onMerge = { onMerge(item.caseId, item.caseRevision) },
                onKeepSeparate = { onKeepSeparate(item.caseId, item.caseRevision) },
                onDefer = { onDefer(item.caseId, item.caseRevision) },
            )
        }
    }
}

@Composable
private fun DomainConflictCard(reasonLabels: List<String>) {
    HikariContentCard {
        Column(
            Modifier.padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text("Merge needs another change", style = MaterialTheme.typography.titleMedium)
            HikariMetadataBadgeGroup(reasonLabels)
        }
    }
}

@Composable
private fun ReconciliationReviewCard(
    item: ReconciliationReviewItemUiModel,
    resolving: Boolean,
    onMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
    onDefer: () -> Unit,
) {
    HikariContentCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StorySummary(item.leftStoryId.value, item.leftTitle, item.leftCoverUrl, Modifier.weight(1f))
                Text(
                    text = "or",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                StorySummary(item.rightStoryId.value, item.rightTitle, item.rightCoverUrl, Modifier.weight(1f))
            }
            Text(
                text = confidenceLabel(item.confidence),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            HikariMetadataBadgeGroup(item.reasonLabels)
            if (!item.mergeAllowed) {
                Text(
                    "These records cannot be merged until the blocking identity conflict changes.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            ) {
                if (item.mergeAllowed) {
                    HikariPrimaryAction(onClick = onMerge, enabled = !resolving) { Text("Merge") }
                }
                HikariInlineAction(onClick = onKeepSeparate, enabled = !resolving) { Text("Keep separate") }
                HikariInlineAction(onClick = onDefer, enabled = !resolving) { Text("Later") }
            }
        }
    }
}

@Composable
private fun StorySummary(stableKey: String, title: String, coverUrl: String?, modifier: Modifier = Modifier) {
    val artwork = rememberHikariArtwork(HikariArtworkModel(coverUrl, stableKey, title))
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val posterSize = MaterialTheme.hikariDimensions.posterActivity
        HikariListArtworkFrame(
            Modifier.size(width = posterSize.width, height = posterSize.height),
        ) {
            HikariArtwork(artwork, title, Modifier.fillMaxSize())
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
    }
}

@Composable
private fun ProtectedConflictSheet(
    conflict: ProtectedConflictUiModel,
    resolving: Boolean,
    onSelected: (PluginId, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val complete = conflict.conflicts.all { it.selectedSourceStoryId != null }
    HikariModalSheet(onDismissRequest = onDismiss) {
        HikariSheetContent(title = "Choose protected mappings") {
            Text(
                "Pick the source mapping that should survive before Hikari merges these stories.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            conflict.conflicts.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8)) {
                    Text(item.pluginId.value, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
                    ) {
                        item.candidateSourceStoryIds.forEach { sourceStoryId ->
                            HikariFilterChip(
                                selected = item.selectedSourceStoryId == sourceStoryId,
                                onClick = { onSelected(item.pluginId, sourceStoryId) },
                                label = { Text(sourceStoryId) },
                            )
                        }
                    }
                }
            }
            HikariPrimaryAction(
                onClick = onConfirm,
                enabled = complete && !resolving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirm merge") }
        }
    }
}

private fun confidenceLabel(confidence: Double): String = when {
    confidence >= ReconciliationReviewPresentationPolicy.strongDuplicateConfidenceThreshold ->
        "Strong duplicate evidence"
    confidence >= ReconciliationReviewPresentationPolicy.contextualPromptConfidenceThreshold ->
        "High duplicate confidence"
    else -> "Needs manual review"
}
