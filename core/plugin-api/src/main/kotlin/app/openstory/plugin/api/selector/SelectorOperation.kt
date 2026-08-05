package app.openstory.plugin.api.selector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SelectorValueType {
    NONE,
    DOCUMENT,
    ELEMENTS,
    TEXT,
}

@Serializable
sealed interface SelectorOperation

val SelectorOperation.inputType: SelectorValueType
    get() = when (this) {
        is HttpGet -> SelectorValueType.NONE
        is RemoveElements -> SelectorValueType.DOCUMENT
        is SelectAll -> SelectorValueType.DOCUMENT
        is SelectText -> SelectorValueType.ELEMENTS
        is SelectAttribute -> SelectorValueType.ELEMENTS
        is NormalizeWhitespace -> SelectorValueType.TEXT
    }

val SelectorOperation.outputType: SelectorValueType
    get() = when (this) {
        is HttpGet -> SelectorValueType.DOCUMENT
        is RemoveElements -> SelectorValueType.DOCUMENT
        is SelectAll -> SelectorValueType.ELEMENTS
        is SelectText -> SelectorValueType.TEXT
        is SelectAttribute -> SelectorValueType.TEXT
        is NormalizeWhitespace -> SelectorValueType.TEXT
    }

@Serializable
@SerialName("http_get")
data class HttpGet(
    val urlTemplate: String,
) : SelectorOperation

@Serializable
@SerialName("select_all")
data class SelectAll(
    val css: String,
) : SelectorOperation

@Serializable
@SerialName("select_text")
data class SelectText(
    val css: String,
) : SelectorOperation

@Serializable
@SerialName("select_attribute")
data class SelectAttribute(
    val css: String,
    val attribute: String,
) : SelectorOperation

@Serializable
@SerialName("normalize_whitespace")
data class NormalizeWhitespace(
    val enabled: Boolean = true,
) : SelectorOperation

@Serializable
@SerialName("remove_elements")
data class RemoveElements(
    val css: String,
) : SelectorOperation
