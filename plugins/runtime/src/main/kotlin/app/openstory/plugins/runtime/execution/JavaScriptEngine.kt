package app.openstory.plugins.runtime.execution

import app.openstory.plugins.api.protocol.PluginOperation
import kotlinx.serialization.json.JsonElement

fun interface JavaScriptEngine {
    suspend fun execute(
        source: String,
        operation: PluginOperation,
        input: JsonElement,
        limits: RuntimeLimits,
        bridge: suspend (String) -> String,
    ): String
}

class JavaScriptExecutionFailure(val code: String) : RuntimeException(code)
