package app.openstory.plugin.host.selector.binding

import app.openstory.common.AppResult
import app.openstory.network.PluginUrlPolicy
import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.BooleanBinding
import app.openstory.plugin.api.selector.ConstantTextBinding
import app.openstory.plugin.api.selector.DoubleBinding
import app.openstory.plugin.api.selector.ElementTextBinding
import app.openstory.plugin.api.selector.EnumBinding
import app.openstory.plugin.api.selector.IntegerBinding
import app.openstory.plugin.api.selector.LongBinding
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorTextValueBinding
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.TimestampBinding
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.host.selector.HtmlDocumentAdapter
import app.openstory.plugin.host.selector.HtmlScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal class SelectorScalarBindingEvaluator(
    private val html: HtmlDocumentAdapter,
    private val urlPolicy: PluginUrlPolicy,
    private val timestampParser: SelectorTimestampParser,
) {
    suspend fun evaluate(
        binding: SelectorBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue = when (binding) {
        is SelectorTextValueBinding -> textValue(binding, scope, path, budget)
        is IntegerBinding -> SelectorBoundValue.IntegerValue(
            converted(path) { sourceText(binding.source, scope, path, budget).toInt() },
        )
        is LongBinding -> SelectorBoundValue.LongValue(
            converted(path) { sourceText(binding.source, scope, path, budget).toLong() },
        )
        is DoubleBinding -> SelectorBoundValue.DoubleValue(
            converted(path) {
                sourceText(binding.source, scope, path, budget).toDouble()
                    .also { require(it.isFinite()) }
            },
        )
        is BooleanBinding -> booleanValue(binding, scope, path, budget)
        is EnumBinding -> enumValue(binding, scope, path, budget)
        is TimestampBinding -> timestampValue(binding, scope, path, budget)
        is UrlBinding -> urlValue(binding, scope, path, budget)
        else -> error("Unsupported scalar selector binding.")
    }

    suspend fun sourceText(
        binding: SelectorTextValueBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): String = textValue(binding, scope, path, budget).value

    private suspend fun textValue(
        binding: SelectorTextValueBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.Text {
        val raw = when (binding) {
            ElementTextBinding -> html.text(scope)
            is TextBinding -> html.text(scope, binding.css)
                ?.let { normalizeSelectorText(it, binding.normalizeWhitespace) }
            is AttributeBinding -> html.attribute(scope, binding.css, binding.attribute)
                .takeIf { it.present }
                ?.value
                ?.let { normalizeSelectorText(it, binding.normalizeWhitespace) }
            is ConstantTextBinding -> binding.value
        }
        val value = raw?.takeUnless(String::isBlank)
            ?: throw SelectorEvaluationFailure(FIELD_MISSING, path)
        budget.consumeTextCharacters(value.length)
        return SelectorBoundValue.Text(value)
    }

    private suspend fun booleanValue(
        binding: BooleanBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.BooleanValue {
        val value = sourceText(binding.source, scope, path, budget)
        return when (value) {
            in binding.trueValues -> SelectorBoundValue.BooleanValue(true)
            in binding.falseValues -> SelectorBoundValue.BooleanValue(false)
            else -> throw SelectorEvaluationFailure(FIELD_INVALID, path)
        }
    }

    private suspend fun enumValue(
        binding: EnumBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.Text {
        val value = sourceText(binding.source, scope, path, budget)
        return SelectorBoundValue.Text(binding.aliases[value] ?: value)
    }

    private suspend fun timestampValue(
        binding: TimestampBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.LongValue = SelectorBoundValue.LongValue(
        converted(path) {
            timestampParser.parse(
                value = sourceText(binding.source, scope, path, budget),
                binding = binding,
            )
        },
    )

    private suspend fun urlValue(
        binding: UrlBinding,
        scope: HtmlScope,
        path: SelectorFieldPath,
        budget: SelectorEvaluationBudget,
    ): SelectorBoundValue.Text {
        val value = sourceText(binding.source, scope, path, budget)
        return when (
            val resolved = urlPolicy.resolve(
                candidate = value,
                documentBaseUrl = html.baseUri(scope),
            )
        ) {
            is AppResult.Success -> SelectorBoundValue.Text(resolved.value.value)
            is AppResult.Failure -> throw SelectorEvaluationFailure(resolved.error.code, path)
        }
    }

    private inline fun <T> converted(
        path: SelectorFieldPath,
        block: () -> T,
    ): T = try {
        block()
    } catch (_: IllegalArgumentException) {
        throw SelectorEvaluationFailure(FIELD_INVALID, path)
    } catch (_: ArithmeticException) {
        throw SelectorEvaluationFailure(FIELD_INVALID, path)
    }

    private companion object {
        const val FIELD_MISSING = "plugin.selector_field_missing"
        const val FIELD_INVALID = "plugin.selector_field_invalid"
    }
}

internal class SelectorTimestampParser(
    private val hostPatterns: Map<String, DateTimeFormatter>,
) {
    fun parse(
        value: String,
        binding: TimestampBinding,
    ): Long = when (binding.format) {
        app.openstory.plugin.api.selector.SelectorTimestampFormat.EPOCH_MILLIS ->
            value.toLong()
        app.openstory.plugin.api.selector.SelectorTimestampFormat.EPOCH_SECONDS ->
            Math.multiplyExact(value.toLong(), MILLIS_PER_SECOND)
        app.openstory.plugin.api.selector.SelectorTimestampFormat.ISO_8601 ->
            Instant.parse(value).toEpochMilli()
        app.openstory.plugin.api.selector.SelectorTimestampFormat.HOST_PATTERN_ID ->
            parseHostPattern(value, binding)
    }

    private fun parseHostPattern(
        value: String,
        binding: TimestampBinding,
    ): Long {
        val formatter = hostPatterns[binding.hostPatternId]
            ?: throw DateTimeParseException("Unknown host timestamp pattern.", value, 0)
        return if (binding.timezoneId != null) {
            LocalDateTime.parse(value, formatter)
                .atZone(ZoneId.of(binding.timezoneId))
                .toInstant()
                .toEpochMilli()
        } else {
            OffsetDateTime.parse(value, formatter).toInstant().toEpochMilli()
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
