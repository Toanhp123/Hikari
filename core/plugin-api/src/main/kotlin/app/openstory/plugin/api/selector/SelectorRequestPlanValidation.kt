package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginManifest
import java.util.Locale

internal object SelectorRequestPlanValidation {
    fun validateV1(
        definition: SelectorPluginDefinition,
        allowedHosts: Set<String>,
    ): Result<Unit> = runCatching {
        if (definition.schemaVersion != SelectorPluginDefinition.CURRENT_SCHEMA_VERSION) {
            selectorFail(
                SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                "Unsupported selector schema version ${definition.schemaVersion}.",
            )
        }
        validatePipeline(
            operations = definition.operations,
            context = RequestValidationContext(
                allowedHosts = normalizeHosts(allowedHosts),
                declarativeOrigin = null,
                requireDocumentOutput = false,
                relativeRequiresOrigin = false,
            ),
        )
    }

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
                requireDocumentOutput = true,
                relativeRequiresOrigin = true,
            ),
        )
    }

    private fun validatePipeline(
        operations: List<SelectorOperation>,
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

        var currentType = SelectorValueType.NONE
        operations.forEachIndexed { index, operation ->
            if (operation.inputType != currentType) {
                selectorFail(
                    SelectorValidationErrorCode.TYPE_MISMATCH,
                    "Operation $index expects ${operation.inputType} but received $currentType.",
                )
            }
            validateOperation(operation, context)
            currentType = operation.outputType
        }
        if (context.requireDocumentOutput && currentType != SelectorValueType.DOCUMENT) {
            selectorFail(
                SelectorValidationErrorCode.TYPE_MISMATCH,
                "Selector request plan must finish with a document.",
            )
        }
    }

    private fun validateOperation(
        operation: SelectorOperation,
        context: RequestValidationContext,
    ) {
        when (operation) {
            is HttpGet -> SelectorSyntaxValidation.validateRequestTemplate(
                template = operation.urlTemplate,
                allowedHosts = context.allowedHosts,
                declarativeOrigin = context.declarativeOrigin,
                relativeRequiresOrigin = context.relativeRequiresOrigin,
            )
            is SelectAll -> SelectorSyntaxValidation.validateCss(operation.css)
            is SelectText -> SelectorSyntaxValidation.validateCss(operation.css)
            is SelectAttribute -> {
                SelectorSyntaxValidation.validateCss(operation.css)
                SelectorSyntaxValidation.validateAttribute(operation.attribute)
            }
            is RemoveElements -> SelectorSyntaxValidation.validateCss(operation.css)
            is NormalizeWhitespace -> Unit
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
        val requireDocumentOutput: Boolean,
        val relativeRequiresOrigin: Boolean,
    )

    private const val MAX_OPERATION_COUNT = 64
    private const val MAX_OUTPUT_ITEMS = 100
    private const val MAX_CHAPTER_BLOCKS = 5_000
    private const val MAX_CHAPTER_TEXT_CHARACTERS = 1_000_000
}
