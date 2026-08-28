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
import app.openstory.catalog.ui.feedback.catalogFailureMessage
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
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
import app.openstory.designsystem.state.HikariErrorState
import app.openstory.designsystem.state.HikariLoadingState
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun ReconciliationReviewScreen(
    state: ReconciliationReviewUiState,
    onBack: () -> Unit,
    onMerge: (String, Long) -> Unit,
    onReverse: (String, Long) -> Unit = { _, _ -> },
    onKeepSeparate: (String, Long) -> Unit,
    onDefer: (String, Long) -> Unit,
    onProtectedMappingSelected: (PluginId, String) -> Unit,
    onConfirmProtectedMerge: () -> Unit,
    onDismissProtectedConflict: () -> Unit,
    onRetryContent: () -> Unit = {},
    onRetryObservation: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    HikariDestinationScaffold(modifier) {
        HikariStickyDestinationScaffold(
            contentPadding = contentPadding,
            header = { HikariFocusedHeader("Review duplicates", onBack) },
        ) { bodyPadding ->
            ReviewQueueContent(
                state = state,
                bodyPadding = bodyPadding,
                onMerge = onMerge,
                onReverse = onReverse,
                onKeepSeparate = onKeepSeparate,
                onDefer = onDefer,
                onRetryContent = onRetryContent,
                onRetryObservation = onRetryObservation,
            )
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
    onReverse: (String, Long) -> Unit,
    onKeepSeparate: (String, Long) -> Unit,
    onDefer: (String, Long) -> Unit,
    onRetryContent: () -> Unit,
    onRetryObservation: () -> Unit,
) {
    when (val content = state.content) {
        is ContentState.Pending -> HikariLoadingState(
            label = "Loading duplicate reviews",
            modifier = Modifier.fillMaxSize().padding(bodyPadding.withScreenContentInsets()),
        )
        is ContentState.Failed -> HikariErrorState(
            title = "Duplicate reviews unavailable",
            message = catalogFailureMessage(content.failure.code, "Couldn't load duplicate reviews right now."),
            actionLabel = if (content.failure.retryable) "Retry" else null,
            onAction = if (content.failure.retryable) onRetryContent else null,
            modifier = Modifier.fillMaxSize().padding(bodyPadding.withScreenContentInsets()),
        )
        is ContentState.Ready -> if (content.value.isEmpty()) {
            EmptyReviewQueue(state, bodyPadding, onRetryObservation)
        } else {
            ReviewQueueList(
                state,
                content.value,
                bodyPadding,
                onMerge,
                onReverse,
                onKeepSeparate,
                onDefer,
                onRetryObservation,
            )
        }
    }
}

@Composable
private fun EmptyReviewQueue(
    state: ReconciliationReviewUiState,
    bodyPadding: PaddingValues,
    onRetryObservation: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bodyPadding.withScreenContentInsets()),
    ) {
        HikariEmptyState(
            title = "No duplicates to review",
            message = "Ambiguous matches will appear here when they need your decision.",
        )
        Column(Modifier.align(Alignment.TopCenter)) {
            state.observationIssue?.let { ReviewObservationFeedback(it, onRetryObservation) }
            state.failureMessage?.let { message -> HikariInlineFeedback(message = message) }
        }
    }
}

@Composable
private fun ReviewQueueList(
    state: ReconciliationReviewUiState,
    items: List<ReconciliationReviewItemUiModel>,
    bodyPadding: PaddingValues,
    onMerge: (String, Long) -> Unit,
    onReverse: (String, Long) -> Unit,
    onKeepSeparate: (String, Long) -> Unit,
    onDefer: (String, Long) -> Unit,
    onRetryObservation: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = bodyPadding.withScreenContentInsets(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap),
    ) {
        state.observationIssue?.let { issue ->
            item(key = "review-observation-issue") { ReviewObservationFeedback(issue, onRetryObservation) }
        }
        state.failureMessage?.let { message ->
            item(key = "review-failure") { HikariInlineFeedback(message = message) }
        }
        if (state.domainConflictReasonLabels.isNotEmpty()) {
            item(key = "review-domain-conflict") { DomainConflictCard(state.domainConflictReasonLabels) }
        }
        items(items, key = ReconciliationReviewItemUiModel::caseId) { item ->
            ReconciliationReviewCard(
                item = item,
                resolving = state.resolvingCaseId == item.caseId,
                onMerge = { onMerge(item.caseId, item.caseRevision) },
                onReverse = { onReverse(item.caseId, item.caseRevision) },
                onKeepSeparate = { onKeepSeparate(item.caseId, item.caseRevision) },
                onDefer = { onDefer(item.caseId, item.caseRevision) },
            )
        }
    }
}

@Composable
private fun ReviewObservationFeedback(failure: CatalogUiFailure, onRetry: () -> Unit) {
    HikariInlineFeedback(
        message = catalogFailureMessage(failure.code, "Duplicate reviews may be out of date."),
        actionLabel = if (failure.retryable) "Retry" else null,
        onAction = if (failure.retryable) onRetry else null,
    )
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
    onReverse: () -> Unit,
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
            if (item.isPostMergeCorrection) {
                ReversalStatus(item)
            } else if (!item.mergeAllowed) {
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
                when {
                    item.isPostMergeCorrection && item.reverseAllowed ->
                        HikariPrimaryAction(onClick = onReverse, enabled = !resolving) { Text("Reverse safely") }
                    !item.isPostMergeCorrection && item.mergeAllowed ->
                        HikariPrimaryAction(onClick = onMerge, enabled = !resolving) { Text("Merge") }
                }
                if (!item.isPostMergeCorrection) {
                    HikariInlineAction(onClick = onKeepSeparate, enabled = !resolving) { Text("Keep separate") }
                }
                HikariInlineAction(onClick = onDefer, enabled = !resolving) { Text("Later") }
            }
        }
    }
}

@Composable
private fun ReversalStatus(item: ReconciliationReviewItemUiModel) {
    Text(
        text = if (item.reverseAllowed) {
            "This correction can safely restore the historical split."
        } else {
            "Automatic reversal is blocked because the merged graph changed after the merge."
        },
        color = if (item.reverseAllowed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodySmall,
    )
    if (item.reversalBlockerLabels.isNotEmpty()) {
        HikariMetadataBadgeGroup(item.reversalBlockerLabels)
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
