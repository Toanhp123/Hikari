package app.openstory.plugins.runtime

sealed interface PluginCallResult<out T> {
    data class Success<T>(val value: T) : PluginCallResult<T>

    data class Failure(
        val code: String,
        val retryable: Boolean,
        val safeDetail: String? = null,
    ) : PluginCallResult<Nothing> {
        init {
            require(SAFE_CODE.matches(code)) { "Failure code must be safe" }
            requireSafeDetail(safeDetail)
        }
    }
}

internal val SAFE_CODE = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")

internal fun requireSafeDetail(value: String?) {
    require(value == null || value.length <= MAX_SAFE_DETAIL_LENGTH) { "Safe detail is too long" }
    require(value == null || value.none(Char::isISOControl)) { "Safe detail contains control characters" }
    require(value == null || !value.contains("://") && !value.contains('=')) {
        "Safe detail must not contain URL or credential material"
    }
}

private const val MAX_SAFE_DETAIL_LENGTH = 256
