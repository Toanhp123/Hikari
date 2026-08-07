package app.openstory.plugin.api.selector.validation

import app.openstory.plugin.api.selector.AttributeBinding
import app.openstory.plugin.api.selector.BooleanBinding
import app.openstory.plugin.api.selector.ConstantTextBinding
import app.openstory.plugin.api.selector.DoubleBinding
import app.openstory.plugin.api.selector.ElementTextBinding
import app.openstory.plugin.api.selector.EnumBinding
import app.openstory.plugin.api.selector.IntegerBinding
import app.openstory.plugin.api.selector.ListBinding
import app.openstory.plugin.api.selector.LongBinding
import app.openstory.plugin.api.selector.ObjectBinding
import app.openstory.plugin.api.selector.OptionalBinding
import app.openstory.plugin.api.selector.SelectorBinding
import app.openstory.plugin.api.selector.SelectorTextValueBinding
import app.openstory.plugin.api.selector.SelectorTimestampFormat
import app.openstory.plugin.api.selector.SelectorValidationErrorCode
import app.openstory.plugin.api.selector.TextBinding
import app.openstory.plugin.api.selector.TextListBinding
import app.openstory.plugin.api.selector.TextSetBinding
import app.openstory.plugin.api.selector.TimestampBinding
import app.openstory.plugin.api.selector.UrlBinding
import app.openstory.plugin.api.selector.selectorFail

internal object SelectorBindingValidator {
    fun validate(binding: SelectorBinding): Result<Unit> = runCatching {
        Visitor().visit(binding, depth = 1)
    }

    private class Visitor {
        private var bindingCount = 0

        fun visit(binding: SelectorBinding, depth: Int) {
            count(depth)
            when (binding) {
                ElementTextBinding -> Unit
                is TextBinding -> binding.css?.let(SelectorSyntaxValidator::validateCss)
                is AttributeBinding -> validateAttribute(binding)
                is ConstantTextBinding -> validateConstant(binding.value)
                else -> visitStructuredBinding(binding, depth)
            }
        }

        private fun visitStructuredBinding(binding: SelectorBinding, depth: Int) {
            when (binding) {
                is OptionalBinding -> visit(binding.value, depth + 1)
                is IntegerBinding -> visit(binding.source, depth + 1)
                is LongBinding -> visit(binding.source, depth + 1)
                is DoubleBinding -> visit(binding.source, depth + 1)
                is UrlBinding -> visit(binding.source, depth + 1)
                is BooleanBinding -> validateBoolean(binding, depth)
                is EnumBinding -> validateEnum(binding, depth)
                is TimestampBinding -> validateTimestamp(binding, depth)
                else -> visitCompositeBinding(binding, depth)
            }
        }

        private fun visitCompositeBinding(binding: SelectorBinding, depth: Int) {
            when (binding) {
                is TextListBinding -> validateCollection(binding.css, binding.value, depth)
                is TextSetBinding -> validateCollection(binding.css, binding.value, depth)
                is ObjectBinding -> validateObject(binding, depth)
                is ListBinding -> {
                    SelectorSyntaxValidator.validateCss(binding.css)
                    visit(binding.item, depth + 1)
                }
                else -> error("Leaf selector binding reached structured validation.")
            }
        }

        private fun count(depth: Int) {
            bindingCount += 1
            if (bindingCount > MAX_BINDING_COUNT) {
                selectorFail(
                    SelectorValidationErrorCode.EXCESSIVE_BINDING_COUNT,
                    "Selector binding count exceeds the host limit.",
                )
            }
            if (depth > MAX_BINDING_DEPTH) {
                selectorFail(
                    SelectorValidationErrorCode.EXCESSIVE_BINDING_DEPTH,
                    "Selector binding depth exceeds the host limit.",
                )
            }
        }

        private fun validateAttribute(binding: AttributeBinding) {
            binding.css?.let(SelectorSyntaxValidator::validateCss)
            SelectorSyntaxValidator.validateAttribute(binding.attribute)
        }

        private fun validateConstant(value: String) {
            if (value.length > MAX_CONSTANT_LENGTH || value.any(Char::isISOControl)) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_CONSTANT,
                    "Selector constant is invalid.",
                )
            }
        }

        private fun validateBoolean(binding: BooleanBinding, depth: Int) {
            visit(binding.source, depth + 1)
            val aliasesMissing =
                binding.trueValues.isEmpty() || binding.falseValues.isEmpty()
            val aliasBlank =
                binding.trueValues.any(String::isBlank) ||
                        binding.falseValues.any(String::isBlank)
            val aliasesOverlap =
                binding.trueValues.any(binding.falseValues::contains)

            if (aliasesMissing || aliasBlank || aliasesOverlap) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_CONSTANT,
                    "Boolean binding aliases are invalid.",
                )
            }
        }

        private fun validateEnum(binding: EnumBinding, depth: Int) {
            visit(binding.source, depth + 1)
            if (binding.aliases.size > MAX_ENUM_ALIASES ||
                binding.aliases.any { (key, value) -> key.isBlank() || value.isBlank() }
            ) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_CONSTANT,
                    "Enum binding aliases are invalid.",
                )
            }
        }

        private fun validateTimestamp(binding: TimestampBinding, depth: Int) {
            visit(binding.source, depth + 1)
            val hasPattern = !binding.hostPatternId.isNullOrBlank()
            if ((binding.format == SelectorTimestampFormat.HOST_PATTERN_ID) != hasPattern ||
                binding.timezoneId?.isBlank() == true
            ) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_TIMESTAMP_CONFIGURATION,
                    "Timestamp binding configuration is invalid.",
                )
            }
        }

        private fun validateCollection(
            css: String,
            value: SelectorTextValueBinding,
            depth: Int,
        ) {
            SelectorSyntaxValidator.validateCss(css)
            visit(value, depth + 1)
        }

        private fun validateObject(binding: ObjectBinding, depth: Int) {
            if (binding.fields.isEmpty() || binding.fields.size > MAX_OBJECT_FIELDS) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_BINDING_PATH,
                    "Object binding field count is invalid.",
                )
            }
            if (binding.fields.keys.any { !it.matches(FIELD_NAME_PATTERN) }) {
                selectorFail(
                    SelectorValidationErrorCode.INVALID_BINDING_PATH,
                    "Object binding contains an invalid field name.",
                )
            }
            binding.fields.values.forEach { visit(it, depth + 1) }
        }

    }

    private const val MAX_CONSTANT_LENGTH = 4_096
    private const val MAX_ENUM_ALIASES = 128
    private const val MAX_OBJECT_FIELDS = 128
    private const val MAX_BINDING_DEPTH = 12
    private const val MAX_BINDING_COUNT = 512
    private val FIELD_NAME_PATTERN = Regex("""[A-Za-z][A-Za-z0-9_]*""")
}
