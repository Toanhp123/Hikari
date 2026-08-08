package app.openstory.plugin.host.selector.binding

sealed interface SelectorBoundValue {
    data object Null : SelectorBoundValue

    data class Text(val value: String) : SelectorBoundValue

    data class IntegerValue(val value: Int) : SelectorBoundValue

    data class LongValue(val value: Long) : SelectorBoundValue

    data class DoubleValue(val value: Double) : SelectorBoundValue

    data class BooleanValue(val value: Boolean) : SelectorBoundValue

    data class ListValue(
        val values: List<SelectorBoundValue>,
    ) : SelectorBoundValue

    data class ObjectValue(
        val fields: Map<String, SelectorBoundValue>,
    ) : SelectorBoundValue
}
