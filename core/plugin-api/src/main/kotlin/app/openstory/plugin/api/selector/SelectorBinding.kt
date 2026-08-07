package app.openstory.plugin.api.selector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SelectorBinding

@Serializable
sealed interface SelectorTextValueBinding : SelectorBinding

@Serializable
@SerialName("element_text")
data object ElementTextBinding : SelectorTextValueBinding

@Serializable
@SerialName("text")
data class TextBinding(
    val css: String? = null,
    val normalizeWhitespace: Boolean = true,
) : SelectorTextValueBinding

@Serializable
@SerialName("attribute")
data class AttributeBinding(
    val css: String? = null,
    val attribute: String,
    val normalizeWhitespace: Boolean = true,
) : SelectorTextValueBinding

@Serializable
@SerialName("constant")
data class ConstantTextBinding(
    val value: String,
) : SelectorTextValueBinding

@Serializable
@SerialName("optional")
data class OptionalBinding(
    val value: SelectorBinding,
) : SelectorBinding

@Serializable
@SerialName("integer")
data class IntegerBinding(
    val source: SelectorTextValueBinding,
) : SelectorBinding

@Serializable
@SerialName("long")
data class LongBinding(
    val source: SelectorTextValueBinding,
) : SelectorBinding

@Serializable
@SerialName("double")
data class DoubleBinding(
    val source: SelectorTextValueBinding,
) : SelectorBinding

@Serializable
@SerialName("boolean")
data class BooleanBinding(
    val source: SelectorTextValueBinding,
    val trueValues: Set<String> = setOf("true"),
    val falseValues: Set<String> = setOf("false"),
) : SelectorBinding

@Serializable
@SerialName("enum")
data class EnumBinding(
    val source: SelectorTextValueBinding,
    val aliases: Map<String, String> = emptyMap(),
) : SelectorBinding

@Serializable
@SerialName("timestamp")
data class TimestampBinding(
    val source: SelectorTextValueBinding,
    val format: SelectorTimestampFormat,
    val hostPatternId: String? = null,
    val timezoneId: String? = null,
) : SelectorBinding

@Serializable
@SerialName("url")
data class UrlBinding(
    val source: SelectorTextValueBinding,
) : SelectorBinding

@Serializable
@SerialName("text_list")
data class TextListBinding(
    val css: String,
    val value: SelectorTextValueBinding = ElementTextBinding,
    val normalizeWhitespace: Boolean = true,
    val distinct: Boolean = false,
) : SelectorBinding

@Serializable
@SerialName("text_set")
data class TextSetBinding(
    val css: String,
    val value: SelectorTextValueBinding = ElementTextBinding,
    val normalizeWhitespace: Boolean = true,
) : SelectorBinding

@Serializable
@SerialName("object")
data class ObjectBinding(
    val fields: Map<String, SelectorBinding>,
) : SelectorBinding

@Serializable
@SerialName("list")
data class ListBinding(
    val css: String,
    val item: SelectorBinding,
) : SelectorBinding

@Serializable
enum class SelectorTimestampFormat {
    EPOCH_MILLIS,
    EPOCH_SECONDS,
    ISO_8601,
    HOST_PATTERN_ID,
}

@Serializable
enum class SelectorTokenKind {
    OPAQUE,
    URL,
}
