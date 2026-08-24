package app.openstory.catalog.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.identity.StoryMergeReversibility
import app.openstory.catalog.identity.StoryUserStateFootprintReader
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.reconciliation.ProtectedMappingResolution
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationReviewAction
import app.openstory.catalog.reconciliation.ReconciliationReviewCommand
import app.openstory.catalog.reconciliation.ReconciliationReviewResult
import app.openstory.catalog.reconciliation.ReconciliationReviewService
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReconciliationReviewViewModel @Inject constructor(
    private val cases: ReconciliationCaseRepository,
    private val projections: CatalogStoryProjectionRepository,
    private val footprints: StoryUserStateFootprintReader,
    private val review: ReconciliationReviewService,
    private val clock: Clock,
) : ViewModel() {
    private val operation = MutableStateFlow(ReviewOperationState())
    private val resumedCaseRevisions = mutableSetOf<String>()

    private val queueItems = cases.observePending()
        .flatMapLatest { pending -> observeQueueItems(pending) }
        .catch {
            operation.update { state ->
                state.copy(failureMessage = "Couldn't load duplicate reviews right now.")
            }
            emit(emptyList())
        }

    val state = combine(queueItems, operation) { items, operationState ->
        ReconciliationReviewUiState(
            items = items,
            resolvingCaseId = operationState.resolvingCaseId,
            protectedConflict = operationState.protectedConflict,
            domainConflictReasonLabels = operationState.domainConflictReasonLabels,
            failureMessage = operationState.failureMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ReconciliationReviewUiState(),
    )

    fun merge(caseId: String, expectedRevision: Long) {
        val item = currentItem(caseId, expectedRevision) ?: return markStale()
        if (!item.mergeAllowed) {
            operation.update {
                it.copy(
                    domainConflictReasonLabels = listOf("Merge is blocked by an identity invariant"),
                    failureMessage = null,
                )
            }
            return
        }
        resolve(
            ReconciliationReviewCommand(
                caseId = caseId,
                expectedCaseRevision = expectedRevision,
                action = ReconciliationReviewAction.MERGE,
            ),
        )
    }

    fun keepSeparate(caseId: String, expectedRevision: Long) {
        if (currentItem(caseId, expectedRevision) == null) return markStale()
        resolve(
            ReconciliationReviewCommand(
                caseId = caseId,
                expectedCaseRevision = expectedRevision,
                action = ReconciliationReviewAction.KEEP_SEPARATE,
            ),
        )
    }

    fun reverse(caseId: String, expectedRevision: Long) {
        val item = currentItem(caseId, expectedRevision) ?: return markStale()
        if (!item.isPostMergeCorrection || !item.reverseAllowed) {
            operation.update {
                it.copy(
                    domainConflictReasonLabels = item.reversalBlockerLabels.ifEmpty {
                        listOf("Automatic reversal is not safe for the current graph")
                    },
                    failureMessage = null,
                )
            }
            return
        }
        resolve(
            ReconciliationReviewCommand(
                caseId = caseId,
                expectedCaseRevision = expectedRevision,
                action = ReconciliationReviewAction.REVERSE,
            ),
        )
    }

    fun defer(caseId: String, expectedRevision: Long) {
        if (currentItem(caseId, expectedRevision) == null) return markStale()
        val suppressUntil = ReconciliationReviewPresentationPolicy.suppressUntil(clock.nowEpochMillis())
        resolve(
            ReconciliationReviewCommand(
                caseId = caseId,
                expectedCaseRevision = expectedRevision,
                action = ReconciliationReviewAction.DEFER,
                suppressUntilEpochMillis = suppressUntil,
            ),
        )
    }

    fun selectProtectedMapping(pluginId: PluginId, sourceStoryId: String) {
        operation.update { current ->
            val conflict = current.protectedConflict
            val target = conflict?.conflicts?.firstOrNull { it.pluginId == pluginId }
            if (conflict != null && target != null && sourceStoryId in target.candidateSourceStoryIds) {
                current.copy(
                    protectedConflict = conflict.copy(
                        conflicts = conflict.conflicts.map { item ->
                            if (item.pluginId == pluginId) item.copy(selectedSourceStoryId = sourceStoryId) else item
                        },
                    ),
                    failureMessage = null,
                )
            } else {
                current
            }
        }
    }

    fun confirmProtectedMerge() {
        val conflict = operation.value.protectedConflict ?: return
        val resolutions = conflict.conflicts.mapNotNull { item ->
            item.selectedSourceStoryId?.let { selected -> ProtectedMappingResolution(item.pluginId, selected) }
        }
        if (resolutions.size != conflict.conflicts.size) {
            operation.update { it.copy(failureMessage = "Choose one source for every protected mapping.") }
            return
        }
        resolve(
            ReconciliationReviewCommand(
                caseId = conflict.caseId,
                expectedCaseRevision = conflict.expectedCaseRevision,
                action = ReconciliationReviewAction.MERGE,
                protectedMappingResolutions = resolutions,
            ),
        )
    }

    fun dismissProtectedConflict() {
        operation.update { it.copy(protectedConflict = null, failureMessage = null) }
    }

    /** Resume a user-approved contextual MERGE that was handed off after a protected conflict. */
    fun resumeMerge(caseId: String) {
        val item = state.value.items.firstOrNull { it.caseId == caseId } ?: return
        val key = "$caseId:${item.caseRevision}"
        if (resumedCaseRevisions.add(key)) merge(caseId, item.caseRevision)
    }

    private fun observeQueueItems(pending: List<ReconciliationCase>) = if (pending.isEmpty()) {
        flowOf(emptyList())
    } else {
        val storyIds = pending.flatMapTo(linkedSetOf()) { listOf(it.key.left, it.key.right) }
        projections.observeForStories(storyIds).mapLatest { catalog ->
            val footprintByStory = footprints.read(storyIds)
            projectReviewQueue(pending, catalog, footprintByStory).map { item ->
                val reversal = review.reversalOption(item.caseId, item.caseRevision)
                if (reversal == null) {
                    item
                } else {
                    item.copy(
                        isPostMergeCorrection = true,
                        reverseAllowed = reversal.reversibility == StoryMergeReversibility.REVERSIBLE,
                        reversalBlockerLabels = reversal.reasonCodes.sorted().map(String::domainConflictLabel),
                    )
                }
            }
        }
    }

    private fun resolve(command: ReconciliationReviewCommand) {
        if (operation.value.resolvingCaseId != null) return
        operation.update {
            it.copy(
                resolvingCaseId = command.caseId,
                failureMessage = null,
                domainConflictReasonLabels = emptyList(),
            )
        }
        viewModelScope.launch {
            try {
                publishResult(command, review.resolve(command))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                operation.update { it.copy(failureMessage = "Couldn't resolve this review right now.") }
            } finally {
                operation.update { current ->
                    if (current.resolvingCaseId == command.caseId) current.copy(resolvingCaseId = null) else current
                }
            }
        }
    }

    private fun publishResult(command: ReconciliationReviewCommand, result: ReconciliationReviewResult) {
        operation.update { current -> current.withResult(command, result) }
    }

    private fun currentItem(caseId: String, expectedRevision: Long): ReconciliationReviewItemUiModel? =
        state.value.items.firstOrNull { it.caseId == caseId && it.caseRevision == expectedRevision }

    private fun markStale() {
        operation.update { it.copy(failureMessage = "This review changed. Check the latest evidence and try again.") }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private data class ReviewOperationState(
    val resolvingCaseId: String? = null,
    val protectedConflict: ProtectedConflictUiModel? = null,
    val domainConflictReasonLabels: List<String> = emptyList(),
    val failureMessage: String? = null,
)

private fun ReviewOperationState.withResult(
    command: ReconciliationReviewCommand,
    result: ReconciliationReviewResult,
): ReviewOperationState = when (result) {
    is ReconciliationReviewResult.Merged,
    is ReconciliationReviewResult.Reversed,
    ReconciliationReviewResult.KeptSeparate,
    is ReconciliationReviewResult.Deferred,
    -> copy(
        protectedConflict = null,
        domainConflictReasonLabels = emptyList(),
        failureMessage = null,
    )
    is ReconciliationReviewResult.ConflictResolutionRequired -> copy(
        protectedConflict = ProtectedConflictUiModel(
            caseId = command.caseId,
            expectedCaseRevision = command.expectedCaseRevision,
            conflicts = result.conflicts.sortedBy { it.pluginId.value }.map { conflict ->
                ProtectedMappingConflictUiModel(
                    pluginId = conflict.pluginId,
                    candidateSourceStoryIds = conflict.candidateSourceStoryIds.sorted(),
                )
            },
        ),
        domainConflictReasonLabels = emptyList(),
        failureMessage = null,
    )
    is ReconciliationReviewResult.DomainStateChangeRequired -> copy(
        protectedConflict = null,
        domainConflictReasonLabels = result.reasonCodes.sorted().map(String::domainConflictLabel),
        failureMessage = null,
    )
    ReconciliationReviewResult.InvariantBlocked -> copy(
        protectedConflict = null,
        domainConflictReasonLabels = listOf("Merge is blocked by an identity invariant"),
        failureMessage = null,
    )
    ReconciliationReviewResult.StaleCase -> copy(
        protectedConflict = null,
        failureMessage = "This review changed. Check the latest evidence and try again.",
    )
}
