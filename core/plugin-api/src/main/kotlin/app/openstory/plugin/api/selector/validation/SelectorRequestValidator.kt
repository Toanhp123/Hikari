package app.openstory.plugin.api.selector.validation

import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.RemoveElements
import app.openstory.plugin.api.selector.SelectorRequestOperation
import app.openstory.plugin.api.selector.SelectorRequestPlan
import app.openstory.plugin.api.selector.SelectorRequestedLimits
import app.openstory.plugin.api.selector.SelectorValidationErrorCode
import app.openstory.plugin.api.selector.selectorFail
import java.util.Locale

internal object SelectorRequestValidator {
    fun validate(
        request: SelectorRequestPlan,
        manifest: PluginManifest,
    ): Result<Unit> = runCatching {
        validateRequestedLimits(request.limits)
        validatePipeline(
            operations = request.operations,
            context = RequestValidationContext(
                allowedHosts = normalizeHosts(manifest.allowedHosts),
                declarativeOrigin = manifest.declarativeOrigin,
                relativeRequiresOrigin = true,
            ),
        )
    }

    private fun validatePipeline(
        operations: List<SelectorRequestOperation>,
        context: RequestValidationContext,
    ) {
        if (operations.isEmpty()) {
            selectorFail(
                SelectorValidationErrorCode.EMPTY_PIPELINE,
                "Selector pipeline must not be empty.",
            )
        }
        if (operations.size > MAX_OPERATION_COUNT) {
            selectorFail(
                SelectorValidationErrorCode.EXCESSIVE_OPERATION_COUNT,
                "Selector operation count exceeds the host limit.",
            )
        }

        if (operations.first() !is HttpGet) {
            selectorFail(
                SelectorValidationErrorCode.TYPE_MISMATCH,
                "Selector request plan must start with HTTP GET.",
            )
        }
        operations.drop(1).forEach { operation ->
            if (operation !is RemoveElements) {
                selectorFail(
                    SelectorValidationErrorCode.TYPE_MISMATCH,
                    "Only element removal may follow HTTP GET.",
                )
            }
        }
        operations.forEach { operation -> validateOperation(operation, context) }
    }

    private fun validateOperation(
        operation: SelectorRequestOperation,
        context: RequestValidationContext,
    ) {
        when (operation) {
            is HttpGet -> SelectorSyntaxValidator.validateRequestTemplate(
                template = operation.urlTemplate,
                allowedHosts = context.allowedHosts,
                declarativeOrigin = context.declarativeOrigin,
                relativeRequiresOrigin = context.relativeRequiresOrigin,
            )
            is RemoveElements -> SelectorSyntaxValidator.validateCss(operation.css)
        }
    }

    private fun validateRequestedLimits(limits: SelectorRequestedLimits?) {
        limits ?: return
        requireLimit(limits.maxOutputItems, MAX_OUTPUT_ITEMS)
        requireLimit(limits.maxChapterBlocks, MAX_CHAPTER_BLOCKS)
        requireLimit(limits.maxChapterTextCharacters, MAX_CHAPTER_TEXT_CHARACTERS)
    }

    private fun requireLimit(value: Int?, maximum: Int) {
        if (value != null && value !in 1..maximum) {
            selectorFail(
                SelectorValidationErrorCode.INVALID_REQUEST_LIMIT,
                "Requested selector limit is outside host bounds.",
            )
        }
    }

    private fun normalizeHosts(hosts: Set<String>): Set<String> =
        hosts.map { it.lowercase(Locale.ROOT) }.toSet()

    private data class RequestValidationContext(
        val allowedHosts: Set<String>,
        val declarativeOrigin: String?,
        val relativeRequiresOrigin: Boolean,
    )

    private const val MAX_OPERATION_COUNT = 64
    private const val MAX_OUTPUT_ITEMS = 100
    private const val MAX_CHAPTER_BLOCKS = 5_000
    private const val MAX_CHAPTER_TEXT_CHARACTERS = 1_000_000
}
