package app.openstory.catalog.ui.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.control.HikariInlineAction
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.feedback.HikariInlineFeedback
import app.openstory.designsystem.surface.HikariContentCard
import app.openstory.designsystem.theme.hikariSpacing

@Composable
internal fun StoryReconciliationPromptHost(
    prompt: StoryReconciliationPromptUiModel?,
    resolving: Boolean,
    failureMessage: String?,
    onMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
    onDefer: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        prompt?.let {
            StoryReconciliationPrompt(
                prompt = it,
                resolving = resolving,
                failureMessage = failureMessage,
                onMerge = onMerge,
                onKeepSeparate = onKeepSeparate,
                onDefer = onDefer,
                modifier = Modifier.padding(
                    start = MaterialTheme.hikariSpacing.screenGutter,
                    end = MaterialTheme.hikariSpacing.screenGutter,
                    bottom = MaterialTheme.hikariSpacing.space8,
                ),
            )
        }
        content()
    }
}

@Composable
internal fun StoryReconciliationPrompt(
    prompt: StoryReconciliationPromptUiModel,
    resolving: Boolean,
    failureMessage: String?,
    onMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
    onDefer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HikariContentCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(MaterialTheme.hikariSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
        ) {
            Text("Possible duplicate", style = MaterialTheme.typography.titleMedium)
            Text(
                "This may be the same work as ${prompt.otherStoryTitle}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HikariMetadataBadgeGroup(prompt.reasonLabels)
            if (!prompt.mergeAllowed) {
                Text(
                    "Hikari found a blocking identity conflict, so merge is unavailable for this evidence.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            failureMessage?.let { HikariInlineFeedback(message = it) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.space8),
            ) {
                if (prompt.mergeAllowed) {
                    HikariPrimaryAction(onClick = onMerge, enabled = !resolving) { Text("Merge") }
                }
                HikariInlineAction(onClick = onKeepSeparate, enabled = !resolving) { Text("Keep separate") }
                HikariInlineAction(onClick = onDefer, enabled = !resolving) { Text("Later") }
            }
        }
    }
}
