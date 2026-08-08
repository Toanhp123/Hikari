package app.openstory.plugin.host.diagnostics

enum class PluginDiagnosticOutcome {
    SUCCESS,
    FAILURE,
}

enum class ResponseStatusCategory {
    INFORMATIONAL,
    SUCCESS,
    REDIRECTION,
    CLIENT_ERROR,
    SERVER_ERROR,
    UNKNOWN,
}

data class PluginDiagnostic(
    val pluginId: String,
    val version: String,
    val operation: String,
    val outcome: PluginDiagnosticOutcome,
    val errorCode: String?,
    val durationMillis: Long,
    val recordedAtEpochMillis: Long,
    val responseStatusCategory: ResponseStatusCategory? = null,
    val retryAfterMillis: Long? = null,
) {
    init {
        require(pluginId.matches(SAFE_TOKEN))
        require(version.matches(SAFE_TOKEN))
        require(operation.matches(SAFE_TOKEN))
        require(errorCode == null || errorCode.matches(SAFE_TOKEN))
        require(durationMillis >= 0L)
        require(recordedAtEpochMillis >= 0L)
        require(retryAfterMillis == null || retryAfterMillis >= 0L)
        require(outcome == PluginDiagnosticOutcome.FAILURE || errorCode == null)
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromFailure(
            pluginId: String,
            operation: String,
            code: String,
            version: String = UNKNOWN_VERSION,
            durationMillis: Long = 0L,
            recordedAtEpochMillis: Long = 0L,
            responseStatusCategory: ResponseStatusCategory? = null,
            retryAfterMillis: Long? = null,
            unsafeDetail: String? = null,
        ): PluginDiagnostic = PluginDiagnostic(
            pluginId = pluginId,
            version = version,
            operation = operation,
            outcome = PluginDiagnosticOutcome.FAILURE,
            errorCode = code,
            durationMillis = durationMillis,
            recordedAtEpochMillis = recordedAtEpochMillis,
            responseStatusCategory = responseStatusCategory,
            retryAfterMillis = retryAfterMillis,
        )

        fun success(
            pluginId: String,
            version: String,
            operation: String,
            durationMillis: Long,
            recordedAtEpochMillis: Long,
            responseStatusCategory: ResponseStatusCategory? = null,
        ): PluginDiagnostic = PluginDiagnostic(
            pluginId = pluginId,
            version = version,
            operation = operation,
            outcome = PluginDiagnosticOutcome.SUCCESS,
            errorCode = null,
            durationMillis = durationMillis,
            recordedAtEpochMillis = recordedAtEpochMillis,
            responseStatusCategory = responseStatusCategory,
        )

        private const val UNKNOWN_VERSION = "unknown"
        private val SAFE_TOKEN = Regex("[A-Za-z0-9._-]+")
    }
}
