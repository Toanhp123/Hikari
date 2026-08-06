package app.openstory.plugin.host.selector

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.api.selector.HttpGet
import app.openstory.plugin.api.selector.SelectorPluginDefinition

class SelectorRuntime(
    private val interpreter: SelectorInterpreter,
) {
    suspend fun execute(
        definition: SelectorPluginDefinition,
        input: Map<String, String>,
        context: SelectorExecutionContext =
            SelectorExecutionContext(),
    ): AppResult<SelectorValue> =
        when (
            val resolvedDefinition =
                resolveDefinition(
                    definition = definition,
                    context = context,
                )
        ) {
            is AppResult.Success ->
                interpreter.execute(
                    operations =
                        resolvedDefinition.value
                            .operations,
                    input = input,
                )

            is AppResult.Failure ->
                resolvedDefinition
        }

    private fun resolveDefinition(
        definition: SelectorPluginDefinition,
        context: SelectorExecutionContext,
    ): AppResult<SelectorPluginDefinition> {
        val resolvedOperations =
            definition.operations.mapIndexed {
                    operationIndex,
                    operation,
                ->
                when (operation) {
                    is HttpGet -> {
                        val resolvedTemplate =
                            context.resolveUrlTemplate(
                                operation.urlTemplate,
                            )
                                ?: return originRequired(
                                    operationIndex =
                                        operationIndex,
                                )

                        operation.copy(
                            urlTemplate =
                                resolvedTemplate,
                        )
                    }

                    else ->
                        operation
                }
            }

        return AppResult.Success(
            definition.copy(
                operations =
                    resolvedOperations,
            ),
        )
    }
}

private fun originRequired(
    operationIndex: Int,
): AppResult.Failure =
    AppResult.Failure(
        error =
            AppError.Plugin(
                code =
                    "plugin.selector_origin_required",
                retryable = false,
                diagnostic =
                    AppError.Diagnostic.of(
                        "operation_index" to
                            operationIndex.toString(),
                        "field_path" to
                            "execution_context.origin",
                    ),
            ),
    )
