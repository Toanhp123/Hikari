package app.openstory.common

sealed interface AppError {
    val code: String
    val retryable: Boolean
    val diagnostic: Diagnostic

    @ConsistentCopyVisibility
    data class Diagnostic private constructor(
        private val tokens: Map<String, String>,
    ) {
        companion object {
            private val safeToken =
                Regex("[A-Za-z0-9._-]+")

            fun empty(): Diagnostic =
                Diagnostic(emptyMap())

            fun of(
                vararg entries: Pair<String, String>,
            ): Diagnostic {
                entries.forEach { (key, value) ->
                    require(key.matches(safeToken)) {
                        "Diagnostic key must be a safe token"
                    }
                    require(value.matches(safeToken)) {
                        "Diagnostic value must be a safe token"
                    }
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
    ) : AppError

    data class Validation(
        override val code: String,
        override val retryable: Boolean = false,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError

    data class Storage(
        override val code: String,
        override val retryable: Boolean,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError

    data class Plugin(
        override val code: String,
        override val retryable: Boolean,
        override val diagnostic: Diagnostic = Diagnostic.empty(),
    ) : AppError
}
