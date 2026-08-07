package app.openstory.plugin.api.selector

internal sealed interface SelectorOutputShape {
    data object Text : SelectorOutputShape
    data object Url : SelectorOutputShape
    data object Integer : SelectorOutputShape
    data object Long : SelectorOutputShape
    data object Double : SelectorOutputShape
    data object Boolean : SelectorOutputShape
    data object Enum : SelectorOutputShape
    data object Timestamp : SelectorOutputShape
    data object Token : SelectorOutputShape
    data object TextList : SelectorOutputShape
    data object TextSet : SelectorOutputShape

    data class Object(
        val required: Map<String, SelectorOutputShape>,
        val optional: Map<String, SelectorOutputShape> = emptyMap(),
    ) : SelectorOutputShape

    data class List(
        val item: Object,
    ) : SelectorOutputShape
}

internal fun validateOutputObject(
    binding: ObjectBinding,
    shape: SelectorOutputShape.Object,
    path: String,
) {
    val allowedFields = shape.required.keys + shape.optional.keys
    val unknown = binding.fields.keys - allowedFields
    if (unknown.isNotEmpty()) {
        selectorFail(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            "Unknown output field at $path.${unknown.sorted().first()}.",
        )
    }

    val missing = shape.required.keys - binding.fields.keys
    if (missing.isNotEmpty()) {
        selectorFail(
            SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
            "Missing required output field at $path.${missing.sorted().first()}.",
        )
    }

    binding.fields.forEach { (field, value) ->
        val expected = shape.required[field] ?: shape.optional.getValue(field)
        val optional = field in shape.optional
        if (!bindingMatchesShape(value, expected, optional, "$path.$field")) {
            selectorFail(
                SelectorValidationErrorCode.OUTPUT_TYPE_MISMATCH,
                "Output binding type mismatch at $path.$field.",
            )
        }
    }
}

private fun bindingMatchesShape(
    binding: SelectorBinding,
    shape: SelectorOutputShape,
    optional: Boolean,
    path: String,
): Boolean {
    val unwrapped = if (optional && binding is OptionalBinding) {
        binding.value
    } else {
        binding
    }

    return when (shape) {
        is SelectorOutputShape.Object -> objectBindingMatches(unwrapped, shape, path)
        is SelectorOutputShape.List -> listBindingMatches(unwrapped, shape, path)
        else -> scalarBindingMatches(unwrapped, shape)
    }
}

private fun objectBindingMatches(
    binding: SelectorBinding,
    shape: SelectorOutputShape.Object,
    path: String,
): Boolean {
    if (binding !is ObjectBinding) {
        return false
    }
    validateOutputObject(binding, shape, path)
    return true
}

private fun listBindingMatches(
    binding: SelectorBinding,
    shape: SelectorOutputShape.List,
    path: String,
): Boolean {
    val listBinding = binding as? ListBinding
    val item = listBinding?.item as? ObjectBinding

    if (item != null) {
        validateOutputObject(item, shape.item, "$path[]")
    }

    return item != null
}

private fun scalarBindingMatches(
    binding: SelectorBinding,
    shape: SelectorOutputShape,
): Boolean = when (shape) {
    SelectorOutputShape.Text -> binding is SelectorTextValueBinding
    SelectorOutputShape.Url -> binding is UrlBinding
    SelectorOutputShape.Integer -> binding is IntegerBinding
    SelectorOutputShape.Long -> binding is LongBinding
    SelectorOutputShape.Double -> binding is DoubleBinding
    SelectorOutputShape.Boolean -> binding is BooleanBinding
    SelectorOutputShape.Enum -> binding is EnumBinding
    SelectorOutputShape.Timestamp -> binding is TimestampBinding
    SelectorOutputShape.Token -> isTokenBinding(binding)
    SelectorOutputShape.TextList -> binding is TextListBinding
    SelectorOutputShape.TextSet -> binding is TextSetBinding
    is SelectorOutputShape.Object, is SelectorOutputShape.List -> false
}

private fun isTokenBinding(binding: SelectorBinding): Boolean =
    binding is SelectorTextValueBinding || binding is UrlBinding
