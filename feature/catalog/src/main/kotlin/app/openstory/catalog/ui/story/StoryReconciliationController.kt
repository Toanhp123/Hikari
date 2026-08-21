package app.openstory.catalog.ui.story

import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReviewAction
import app.openstory.catalog.reconciliation.ReconciliationReviewCommand
import app.openstory.catalog.reconciliation.ReconciliationReviewResult
import app.openstory.catalog.reconciliation.ReconciliationReviewService
import app.openstory.catalog.ui.review.ReconciliationReviewPresentationPolicy
import app.openstory.catalog.ui.review.domainConflictLabel
import app.openstory.catalog.ui.review.reviewLabel
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class StoryReconciliationController @Inject constructor(
    private val cases: ReconciliationCaseRepository,
    private val projections: CatalogStoryProjectionRepository,
    private val review: ReconciliationReviewService,
    private val clock: Clock,
) {
    private val operation = MutableStateFlow(StoryReconciliationOperation())

    fun observe(storyIds: Flow<StoryId>): Flow<StoryReconciliationState> {
        val prompt = storyIds.flatMapLatest { storyId ->
            cases.observeForStory(storyId).flatMapLatest { observed -> observePrompt(storyId, observed) }
        }.catch {
            operation.update { it.copy(failureMessage = "Couldn't load duplicate review information.") }
            emit(null)
        }
        return combine(prompt, operation) { currentPrompt, currentOperation ->
            StoryReconciliationState(
                prompt = currentPrompt?.takeUnless { it.caseId == currentOperation.resolvingCaseId },
                resolving = currentOperation.resolvingCaseId != null,
                failureMessage = currentOperation.failureMessage,
            )
        }
    }

    fun merge(
        prompt: StoryReconciliationPromptUiModel?,
        scope: CoroutineScope,
        onProtectedConflict: (String) -> Unit,
    ) {
        resolve(prompt, ReconciliationReviewAction.MERGE, scope, onProtectedConflict)
    }

    fun keepSeparate(prompt: StoryReconciliationPromptUiModel?, scope: CoroutineScope) {
        resolve(prompt, ReconciliationReviewAction.KEEP_SEPARATE, scope)
    }

    fun defer(prompt: StoryReconciliationPromptUiModel?, scope: CoroutineScope) {
        resolve(prompt, ReconciliationReviewAction.DEFER, scope)
    }

    private fun resolve(
        prompt: StoryReconciliationPromptUiModel?,
        action: ReconciliationReviewAction,
        scope: CoroutineScope,
        onProtectedConflict: (String) -> Unit = {},
    ) {
        when {
            prompt == null || operation.value.resolvingCaseId != null -> Unit
            action == ReconciliationReviewAction.MERGE && !prompt.mergeAllowed -> {
                operation.update { it.copy(failureMessage = "Merge is blocked by an identity invariant.") }
            }
            else -> launchResolution(prompt, action, scope, onProtectedConflict)
        }
    }

    private fun launchResolution(
        prompt: StoryReconciliationPromptUiModel,
        action: ReconciliationReviewAction,
        scope: CoroutineScope,
        onProtectedConflict: (String) -> Unit,
    ) {
        operation.value = StoryReconciliationOperation(resolvingCaseId = prompt.caseId)
        scope.launch {
            try {
                publishResult(prompt, review.resolve(command(prompt, action)), onProtectedConflict)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                operation.update { it.copy(failureMessage = "Couldn't resolve this duplicate review right now.") }
            } finally {
                operation.update { current ->
                    if (current.resolvingCaseId == prompt.caseId) current.copy(resolvingCaseId = null) else current
                }
            }
        }
    }

    private fun command(
        prompt: StoryReconciliationPromptUiModel,
        action: ReconciliationReviewAction,
    ) = ReconciliationReviewCommand(
        caseId = prompt.caseId,
        expectedCaseRevision = prompt.caseRevision,
        action = action,
        suppressUntilEpochMillis = if (action == ReconciliationReviewAction.DEFER) {
            ReconciliationReviewPresentationPolicy.suppressUntil(clock.nowEpochMillis())
        } else {
            null
        },
    )

    private fun publishResult(
        prompt: StoryReconciliationPromptUiModel,
        result: ReconciliationReviewResult,
        onProtectedConflict: (String) -> Unit,
    ) {
        operation.update { current ->
            when (result) {
                is ReconciliationReviewResult.ConflictResolutionRequired -> {
                    onProtectedConflict(prompt.caseId)
                    current.copy(failureMessage = null)
                }
                is ReconciliationReviewResult.DomainStateChangeRequired -> current.copy(
                    failureMessage = result.reasonCodes.sorted().map(String::domainConflictLabel)
                        .joinToString(prefix = "Merge needs: "),
                )
                ReconciliationReviewResult.InvariantBlocked ->
                    current.copy(failureMessage = "Merge is blocked by an identity invariant.")
                ReconciliationReviewResult.StaleCase ->
                    current.copy(failureMessage = "This review changed. Check the latest evidence and try again.")
                is ReconciliationReviewResult.Merged,
                ReconciliationReviewResult.KeptSeparate,
                is ReconciliationReviewResult.Deferred,
                -> current.copy(failureMessage = null)
            }
        }
    }

    private fun observePrompt(storyId: StoryId, observed: List<ReconciliationCase>) =
        observeContextualCase(observed).flatMapLatest { case ->
            case?.let { selected -> projectPrompt(storyId, selected) } ?: flowOf(null)
        }

    private fun projectPrompt(storyId: StoryId, case: ReconciliationCase): Flow<StoryReconciliationPromptUiModel?> {
        val otherStoryId = if (case.key.left == storyId) case.key.right else case.key.left
        return projections.observeForStories(setOf(otherStoryId)).map { catalog ->
            val otherTitle = catalog.firstOrNull { it.storyId == otherStoryId }?.title ?: otherStoryId.value
            StoryReconciliationPromptUiModel(
                caseId = case.id,
                caseRevision = case.revision,
                otherStoryId = otherStoryId,
                otherStoryTitle = otherTitle,
                confidence = case.assessment.confidence,
                mergeAllowed = case.assessment.mergeEligibility == ReconciliationMergeEligibility.MERGEABLE,
                reasonLabels = case.assessment.reasons.sortedBy { it.name }.map { it.reviewLabel() },
            )
        }
    }

    private fun observeContextualCase(observed: List<ReconciliationCase>) = flow {
        val now = clock.nowEpochMillis()
        val current = selectContextualCase(observed, now)
        emit(current)
        if (current == null) {
            nextSuppressionExpiry(observed, now)?.let { wakeAt ->
                delay((wakeAt - now).coerceAtLeast(1L))
                emit(selectContextualCase(observed, clock.nowEpochMillis()))
            }
        }
    }

    private fun selectContextualCase(
        observed: List<ReconciliationCase>,
        nowEpochMillis: Long,
    ): ReconciliationCase? = contextualCandidates(observed)
        .filter { case -> case.contextualPromptSuppressedUntilEpochMillis?.let { it > nowEpochMillis } != true }
        .firstOrNull()

    private fun nextSuppressionExpiry(
        observed: List<ReconciliationCase>,
        nowEpochMillis: Long,
    ): Long? = contextualCandidates(observed)
        .mapNotNull(ReconciliationCase::contextualPromptSuppressedUntilEpochMillis)
        .filter { it > nowEpochMillis }
        .minOrNull()

    private fun contextualCandidates(observed: List<ReconciliationCase>): Sequence<ReconciliationCase> =
        observed.asSequence()
            .filter { it.status == ReconciliationCaseStatus.PENDING }
            .filter {
                it.assessment.confidence >= ReconciliationReviewPresentationPolicy.contextualPromptConfidenceThreshold
            }
            .sortedWith(
                compareByDescending<ReconciliationCase> { it.assessment.confidence }
                    .thenByDescending { it.lastEvaluatedAtEpochMillis }
                    .thenBy { it.createdAtEpochMillis }
                    .thenBy { it.id },
            )
}

internal data class StoryReconciliationState(
    val prompt: StoryReconciliationPromptUiModel?,
    val resolving: Boolean,
    val failureMessage: String?,
)

private data class StoryReconciliationOperation(
    val resolvingCaseId: String? = null,
    val failureMessage: String? = null,
)
