package app.openstory.common

private val safeMachineToken =
    Regex("[A-Za-z0-9._-]+")

private fun requireSafeMachineToken(
    value: String,
    label: String,
) {
    require(value.matches(safeMachineToken)) {
        "$label must be a non-blank machine-readable token"
    }
}

sealed interface AppError {
    val code: String
    val retryable: Boolean
    val diagnostic: Diagnostic

    @ConsistentCopyVisibility
    data class Diagnostic private constructor(
        private val tokens: Map<String, String>,
    ) {
        fun with(
            vararg entries: Pair<String, String>,
        ): Diagnostic {
            val merged =
                linkedMapOf<String, String>()

            merged.putAll(tokens)

            entries.forEach { (key, value) ->
                requireSafeMachineToken(
                    value = key,
                    label = "Diagnostic key",
                )
                requireSafeMachineToken(
                    value = value,
                    label = "Diagnostic value",
                )

                merged[key] = value
            }

            return Diagnostic(merged)
        }

        companion object {
            fun empty(): Diagnostic =
                Diagnostic(emptyMap())

            fun of(
                vararg entries: Pair<String, String>,
            ): Diagnostic {
                entries.forEach { (key, value) ->
                    requireSafeMachineToken(
                        value = key,
                        label = "Diagnostic key",
                    )
                    requireSafeMachineToken(
                        value = value,
                        label = "Diagnostic value",
                    )
                }

                return Diagnostic(
                    linkedMapOf(*entries),
                )
            }
        }
    }

    data class Network(
        override val code: String,
        override val retryable: Boolean,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError {
        init {
            requireSafeMachineToken(
                value = code,
                label = "Error code",
            )
        }
    }

    data class Validation(
        override val code: String,
        override val retryable: Boolean = false,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError {
        init {
            requireSafeMachineToken(
                value = code,
                label = "Error code",
            )
        }
    }

    data class Storage(
        override val code: String,
        override val retryable: Boolean,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError {
        init {
            requireSafeMachineToken(
                value = code,
                label = "Error code",
            )
        }
    }

    data class Plugin(
        override val code: String,
        override val retryable: Boolean,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError {
        init {
            requireSafeMachineToken(
                value = code,
                label = "Error code",
            )
        }
    }
}
