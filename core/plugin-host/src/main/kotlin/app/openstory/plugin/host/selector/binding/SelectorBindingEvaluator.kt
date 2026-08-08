package app.openstory.plugin.host.selector.binding

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.selector.BooleanBinding
import app.openstory.plugin.api.selector.DoubleBinding
import app.openstory.plugin.api.selector.EnumBinding
import app.openstory.plugin.api.selector.IntegerBinding
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.LongBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.OptionalBinding
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorTextValueBinding
import app.openstory.plugin.api.selector.TextListBinding
import app.openstory.plugin.api.selector.TextSetBinding
import app.openstory.plugin.api.selector.TimestampBinding
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.host.selector.HtmlDocumentAdapter
import app.openstory.plugin.host.selector.HtmlScope
import java.time.format.DateTimeFormatter

class SelectorBindingEvaluator(
    html: HtmlDocumentAdapter,
    urlPolicy: PluginUrlPolicy,
    hostTimestampPatterns: Map<String, DateTimeFormatter> = emptyMap(),
) {
    private val scalars = SelectorScalarBindingEvaluator(
        html = html,
        urlPolicy = urlPolicy,
        timestampParser = SelectorTimestampParser(hostTimestampPatterns),
    )
    private val html = html

    suspend fun evaluate(
        binding: SelectorBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): AppResult<SelectorBoundValue> =
        try {
            AppResult.Success(evaluateOrThrow(binding, scope, path, budget))
        } catch (failure: SelectorEvaluationFailure) {
            pluginFailure(failure.code, failure.path)
        } catch (failure: SelectorEvaluationLimitExceeded) {
            pluginFailure(failure.code, path)
        }

    internal suspend fun evaluateOrThrow(
        binding: SelectorBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue {
        budget.consumeField()
        return when (binding) {
            is SelectorTextValueBinding,
            is IntegerBinding,
            is LongBinding,
            is DoubleBinding,
            is BooleanBinding,
            is EnumBinding,
            is TimestampBinding,
            is UrlBinding,
            -> scalars.evaluate(binding, scope, path, budget)

            is OptionalBinding -> optionalValue(binding, scope, path, budget)
            is TextListBinding -> textCollection(
                css = binding.css,
                valueBinding = binding.value,
                normalizeWhitespace = binding.normalizeWhitespace,
                distinct = false,
                scope = scope,
                path = path,
                budget = budget,
            )
            is TextSetBinding -> textCollection(
                css = binding.css,
                valueBinding = binding.value,
                normalizeWhitespace = binding.normalizeWhitespace,
                distinct = true,
                scope = scope,
                path = path,
                budget = budget,
            )
            is ObjectBinding -> objectValue(binding, scope, path, budget)
            is ListBinding -> listValue(binding, scope, path, budget)
        }
    }

    private suspend fun optionalValue(
        binding: OptionalBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue =
        try {
            evaluateOrThrow(binding.value, scope, path, budget)
        } catch (failure: SelectorEvaluationFailure) {
            if (failure.code == FIELD_MISSING) {
                SelectorBoundValue.Null
            } else {
                throw failure
            }
        }

    private suspend fun textCollection(
        css: String,
        valueBinding: SelectorTextValueBinding,
        normalizeWhitespace: Boolean,
        distinct: Boolean,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.ListValue {
        val values = mutableListOf<SelectorBoundValue>()
        val seen = linkedSetOf<String>()
        html.selectAll(scope, css).forEachIndexed { index, element ->
            val value = scalars.sourceText(
                binding = valueBinding,
                scope = element,
                path = path.index(index),
                budget = budget,
            )
            val normalized = normalizeSelectorText(value, normalizeWhitespace)
            if (!distinct || seen.add(normalized)) {
                budget.consumeOutputItem()
                values += SelectorBoundValue.Text(normalized)
            }
        }
        return SelectorBoundValue.ListValue(values)
    }

    private suspend fun objectValue(
        binding: ObjectBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.ObjectValue {
        val fields = linkedMapOf<String, SelectorBoundValue>()
        binding.fields.forEach { (name, fieldBinding) ->
            fields[name] = evaluateOrThrow(
                binding = fieldBinding,
                scope = scope,
                path = path.field(name),
                budget = budget,
            )
        }
        return SelectorBoundValue.ObjectValue(fields)
    }

    private suspend fun listValue(
        binding: ListBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.ListValue {
        val values = html.selectAll(scope, binding.css).mapIndexed { index, element ->
            budget.consumeOutputItem()
            evaluateOrThrow(binding.item, element, path.index(index), budget)
        }
        return SelectorBoundValue.ListValue(values)
    }

    private companion object {
        const val FIELD_MISSING = "plugin.selector_field_missing"
    }
}

internal class SelectorEvaluationFailure(
    val code: String,
    val path: SelectorFieldPath,
) : RuntimeException(null, null, false, false)

internal fun normalizeSelectorText(value: String, enabled: Boolean): String =
    if (enabled) value.trim().replace(Regex("\\s+"), " ") else value

private fun pluginFailure(
    code: String,
    path: SelectorFieldPath,
): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(
        code = code,
        retryable = false,
        diagnostic = AppError.Diagnostic.of("field_path" to path.value),
    ),
)
