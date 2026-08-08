package app.openstory.plugin.host.diagnostics

import app.openstory.common.Clock
import app.openstory.common.SystemClock

enum class PluginHealthState {
    HEALTHY,
    DEGRADED,
    DISABLED_BY_USER,
}

interface PluginDiagnosticStore {
    suspend fun record(
        diagnostic: PluginDiagnostic,
        perPluginLimit: Int,
        globalLimit: Int,
    )

    suspend fun recent(limit: Int): List<PluginDiagnostic>

    suspend fun recent(
        pluginId: String,
        limit: Int,
    ): List<PluginDiagnostic>
}

class PluginDiagnosticsRepository(
    private val store: PluginDiagnosticStore,
    private val clock: Clock = SystemClock,
    private val perPluginLimit: Int = DEFAULT_PER_PLUGIN_LIMIT,
    private val globalLimit: Int = DEFAULT_GLOBAL_LIMIT,
    private val degradedAfterConsecutiveFailures: Int = DEFAULT_DEGRADED_THRESHOLD,
) {
    init {
        require(perPluginLimit > 0)
        require(globalLimit >= perPluginLimit)
        require(degradedAfterConsecutiveFailures in 1..perPluginLimit)
    }

    suspend fun record(diagnostic: PluginDiagnostic) {
        store.record(
            diagnostic = diagnostic,
            perPluginLimit = perPluginLimit,
            globalLimit = globalLimit,
        )
    }

    suspend fun recordFailure(
        pluginId: String,
        version: String,
        operation: String,
        code: String,
        durationMillis: Long,
        responseStatusCategory: ResponseStatusCategory? = null,
        retryAfterMillis: Long? = null,
    ) {
        record(
            PluginDiagnostic.fromFailure(
                pluginId = pluginId,
                version = version,
                operation = operation,
                code = code,
                durationMillis = durationMillis,
                recordedAtEpochMillis = clock.nowEpochMillis(),
                responseStatusCategory = responseStatusCategory,
                retryAfterMillis = retryAfterMillis,
            ),
        )
    }

    suspend fun recent(): List<PluginDiagnostic> =
        store.recent(globalLimit)

    suspend fun recent(pluginId: String): List<PluginDiagnostic> =
        store.recent(pluginId, perPluginLimit)

    suspend fun health(
        pluginId: String,
        disabledByUser: Boolean = false,
    ): PluginHealthState {
        if (disabledByUser) {
            return PluginHealthState.DISABLED_BY_USER
        }

        val consecutiveFailures = recent(pluginId)
            .takeWhile { it.outcome == PluginDiagnosticOutcome.FAILURE }
            .size

        return if (consecutiveFailures >= degradedAfterConsecutiveFailures) {
            PluginHealthState.DEGRADED
        } else {
            PluginHealthState.HEALTHY
        }
    }

    private companion object {
        const val DEFAULT_PER_PLUGIN_LIMIT = 100
        const val DEFAULT_GLOBAL_LIMIT = 1_000
        const val DEFAULT_DEGRADED_THRESHOLD = 3
    }
}
