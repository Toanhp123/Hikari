package app.openstory.plugin.api.selector

internal fun selectorFail(
    code: SelectorValidationErrorCode,
    message: String,
): Nothing = throw SelectorValidationException(code, message)
