package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.limits.strictUtf8

internal fun validationFailure(
    field: String,
    reason: CatalogValidationReason,
    cause: IllegalArgumentException? = null,
): Nothing = throw CatalogFailureException(CatalogFailure.Validation(field, reason), cause)

internal fun validateUtf8(
    field: String,
    value: String,
    maximumBytes: Int,
    requireNonBlank: Boolean = false,
) {
    val bytes = validateMalformed(field) { strictUtf8(value) }
    requireValidation(
        !requireNonBlank || value.isNotBlank(),
        field,
        CatalogValidationReason.MALFORMED,
    )
    requireValidation(bytes.size <= maximumBytes, field, CatalogValidationReason.OVER_LIMIT)
}

internal fun validateScalars(
    field: String,
    value: String,
    maximumScalars: Int,
    requireNonBlank: Boolean = false,
) {
    validateMalformed(field) { strictUtf8(value) }
    requireValidation(
        !requireNonBlank || value.isNotBlank(),
        field,
        CatalogValidationReason.MALFORMED,
    )
    requireValidation(
        value.codePointCount(0, value.length) <= maximumScalars,
        field,
        CatalogValidationReason.OVER_LIMIT,
    )
}

internal inline fun validate(
    field: String,
    reason: CatalogValidationReason = CatalogValidationReason.MALFORMED,
    block: () -> Unit,
) {
    try {
        block()
    } catch (exception: IllegalArgumentException) {
        validationFailure(field, reason, exception)
    }
}

private inline fun <T> validateMalformed(field: String, block: () -> T): T = try {
    block()
} catch (exception: IllegalArgumentException) {
    validationFailure(field, CatalogValidationReason.MALFORMED, exception)
}

internal fun requireValidation(
    condition: Boolean,
    field: String,
    reason: CatalogValidationReason,
) {
    if (!condition) validationFailure(field, reason)
}
