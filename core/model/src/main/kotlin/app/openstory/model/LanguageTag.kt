package app.openstory.model

import java.util.Locale

@JvmInline
value class LanguageTag private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): LanguageTag {
            val normalized = value
                .replace('_', '-')
                .lowercase(Locale.ROOT)

            require(normalized.isNotBlank()) {
                "Language tag must not be blank"
            }

            return LanguageTag(normalized)
        }
    }
}
