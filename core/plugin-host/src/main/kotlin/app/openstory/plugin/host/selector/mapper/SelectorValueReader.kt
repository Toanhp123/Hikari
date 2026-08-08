package app.openstory.plugin.host.selector.mapper

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.plugin.host.selector.binding.SelectorBoundValue

internal class SelectorValueReader(
    private val value: SelectorBoundValue,
    val path: String,
) {
    fun field(name: String): SelectorValueReader {
        val fields = objectFields()
        val child = fields[name]
            ?: throw SelectorMappingFailure("plugin.selector_field_missing", "$path.$name")
        return SelectorValueReader(child, "$path.$name")
    }

    fun optionalField(name: String): SelectorValueReader? {
        val child = objectFields()[name] ?: return null
        return if (child == SelectorBoundValue.Null) null else SelectorValueReader(child, "$path.$name")
    }

    fun text(): String = (value as? SelectorBoundValue.Text)?.value
        ?: invalid()

    fun integer(): Int = (value as? SelectorBoundValue.IntegerValue)?.value
        ?: invalid()

    fun long(): Long = (value as? SelectorBoundValue.LongValue)?.value
        ?: invalid()

    fun double(): Double = (value as? SelectorBoundValue.DoubleValue)?.value
        ?: invalid()

    fun boolean(): Boolean = (value as? SelectorBoundValue.BooleanValue)?.value
        ?: invalid()

    fun values(): List<SelectorValueReader> =
        (value as? SelectorBoundValue.ListValue)?.values
            ?.mapIndexed { index, child -> SelectorValueReader(child, "$path.$index") }
            ?: invalid()

    private fun objectFields(): Map<String, SelectorBoundValue> =
        (value as? SelectorBoundValue.ObjectValue)?.fields ?: invalid()

    private fun invalid(): Nothing =
        throw SelectorMappingFailure("plugin.selector_field_invalid", path)
}

internal class SelectorMappingFailure(
    val code: String,
    val path: String,
) : RuntimeException(null, null, false, false)

internal fun SelectorValueReader.optionalTextList(name: String): List<String> =
    optionalField(name)?.values()?.map(SelectorValueReader::text).orEmpty()

internal inline fun <T> mapSelectorValue(
    rootPath: String,
    block: () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (failure: SelectorMappingFailure) {
    selectorMappingFailure(failure.code, failure.path)
} catch (_: IllegalArgumentException) {
    selectorMappingFailure("plugin.selector_field_invalid", rootPath)
}

private fun selectorMappingFailure(
    code: String,
    path: String,
): AppResult.Failure = AppResult.Failure(
    AppError.Plugin(
        code = code,
        retryable = false,
        diagnostic = AppError.Diagnostic.of("field_path" to path),
    ),
)
