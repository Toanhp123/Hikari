package app.openstory.plugin.host.selector.runtime

import app.openstory.common.AppResult
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.host.selector.HtmlDocument
import app.openstory.plugin.host.selector.SelectorDocumentLoader
import app.openstory.plugin.host.selector.SelectorExecutionContext
import app.openstory.plugin.host.selector.SelectorLimits
import app.openstory.plugin.host.selector.binding.SelectorBindingEvaluator
import app.openstory.plugin.host.selector.binding.SelectorBoundValue
import app.openstory.plugin.host.selector.binding.SelectorEvaluationBudget
import app.openstory.plugin.host.selector.binding.SelectorFieldPath

internal class SelectorEndpointExecutor(
    private val loader: SelectorDocumentLoader,
    private val evaluator: SelectorBindingEvaluator,
    private val context: SelectorExecutionContext,
    private val limits: SelectorLimits,
) {
    suspend fun load(
        request: SelectorRequestPlan,
        input: Map<String, String>,
    ): AppResult<HtmlDocument> = loader.load(request, input, context)

    suspend fun evaluate(
        document: HtmlDocument,
        binding: SelectorBinding,
        path: String,
        budget: SelectorEvaluationBudget,
    ): AppResult<SelectorBoundValue> = evaluator.evaluate(
        binding = binding,
        scope = document,
        path = SelectorFieldPath.root(path),
        budget = budget,
    )

    fun budget(request: SelectorRequestPlan): SelectorEvaluationBudget =
        SelectorEvaluationBudget(
            maxOutputItems = request.limits?.maxOutputItems ?: DEFAULT_OUTPUT_ITEMS,
            maxChapterBlocks = request.limits?.maxChapterBlocks ?: DEFAULT_CHAPTER_BLOCKS,
            maxChapterTextCharacters = request.limits?.maxChapterTextCharacters
                ?: DEFAULT_CHAPTER_CHARACTERS,
            maxWallClockMillis = limits.maxWallClockMillis,
        )

    private companion object {
        const val DEFAULT_OUTPUT_ITEMS = 10_000
        const val DEFAULT_CHAPTER_BLOCKS = 20_000
        const val DEFAULT_CHAPTER_CHARACTERS = 5_000_000
    }
}

internal suspend inline fun <T, R> AppResult<T>.flatMapSuspend(
    crossinline transform: suspend (T) -> AppResult<R>,
): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}
