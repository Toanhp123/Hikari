package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.limits.strictUtf8
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogRating

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

internal fun validateCatalogRating(rating: CatalogRating) {
    requireValidation(
        rating.value.isFinite() && rating.scale.isFinite() &&
            rating.scale > 0.0 && rating.value >= 0.0 && rating.value <= rating.scale,
        "rating",
        CatalogValidationReason.MALFORMED,
    )
}

internal fun validateStoryMetadata(
    alternateTitles: List<String>,
    catalogLanguageTags: List<String>,
) {
    requireValidation(
        alternateTitles.size <= CatalogInputLimits.ALTERNATE_TITLES,
        "alternateTitles",
        CatalogValidationReason.OVER_LIMIT,
    )
    alternateTitles.forEachIndexed { index, value ->
        validateScalars(
            "alternateTitles[$index]",
            value,
            CatalogInputLimits.TITLE_UNICODE_SCALARS,
            true,
        )
    }
    requireValidation(
        alternateTitles.distinct().size == alternateTitles.size,
        "alternateTitles",
        CatalogValidationReason.INVARIANT_VIOLATION,
    )
    requireValidation(
        catalogLanguageTags.size <= CatalogInputLimits.CATALOG_LANGUAGE_TAGS,
        "catalogLanguageTags",
        CatalogValidationReason.OVER_LIMIT,
    )
    catalogLanguageTags.forEachIndexed { index, value ->
        validateUtf8(
            "catalogLanguageTags[$index]",
            value,
            CatalogInputLimits.LANGUAGE_TAG_UTF8_BYTES,
            true,
        )
        requireValidation(
            NORMALIZED_LANGUAGE_TAG.matches(value),
            "catalogLanguageTags[$index]",
            CatalogValidationReason.MALFORMED,
        )
    }
    requireValidation(
        catalogLanguageTags.distinct().size == catalogLanguageTags.size,
        "catalogLanguageTags",
        CatalogValidationReason.INVARIANT_VIOLATION,
    )
}

private val NORMALIZED_LANGUAGE_TAG = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
