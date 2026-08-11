package app.openstory.plugins.runtime.execution

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.PluginProtocolValidator
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.capabilities.CapabilityDispatcher
import app.openstory.plugins.runtime.capabilities.http.PluginRequestPolicy
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class PluginOperationRunner(
    private val engine: JavaScriptEngine,
    private val capabilities: CapabilityDispatcher,
    private val diagnostics: PluginDiagnosticsSink,
    private val limits: RuntimeLimits = RuntimeLimits(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json,
) {
    suspend fun run(
        pluginId: PluginId,
        manifest: PluginManifest,
        script: String,
        operation: PluginOperation,
        input: JsonElement,
    ): PluginCallResult<JsonElement> = try {
        val requestPolicy = PluginRequestPolicy(
            pluginId = pluginId,
            allowedHosts = manifest.capabilities.network?.hosts.orEmpty(),
        )
        val output = withTimeout(limits.timeoutMillis) {
            engine.execute(script, operation, input, limits) { message ->
                dispatchBridge(pluginId, operation, requestPolicy, message)
            }
        }
        if (output.encodeToByteArray().size > limits.maxOutputJsonBytes) {
            fail(pluginId, operation, "plugin.output_too_large")
        } else {
            val payload = runCatching { json.parseToJsonElement(output) }.getOrNull()
                ?: return fail(pluginId, operation, "plugin.output_invalid")
            val violations = PluginProtocolValidator.validateOutput(
                operation,
                payload,
                manifest.capabilities.network?.hosts.orEmpty(),
            )
            if (violations.isEmpty()) PluginCallResult.Success(payload)
            else fail(pluginId, operation, violations.first().code)
        }
    } catch (failure: CancellationException) {
        if (failure is TimeoutCancellationException) {
            fail(pluginId, operation, "plugin.execution_timeout", retryable = true)
        } else {
            throw failure
        }
    } catch (failure: JavaScriptExecutionFailure) {
        fail(pluginId, operation, failure.code, failure.retryable)
    } catch (_: RuntimeException) {
        fail(pluginId, operation, "plugin.execution_failed")
    }

    private suspend fun dispatchBridge(
        pluginId: PluginId,
        operation: PluginOperation,
        policy: PluginRequestPolicy,
        source: String,
    ): String {
        if (source.encodeToByteArray().size > limits.maxBridgeMessageBytes) {
            return json.encodeToString(
                BridgeResponse("call-1", error = BridgeError("plugin.bridge_message_too_large")),
            )
        }
        val request = runCatching { json.decodeFromString(BridgeRequest.serializer(), source) }.getOrNull()
        return if (request == null) {
            json.encodeToString(BridgeResponse("call-1", error = BridgeError("plugin.bridge_message_invalid")))
        } else {
            val result = capabilities.dispatch(
                pluginId,
                operation.wireName,
                request.method,
                request.payload,
                policy,
            )
            json.encodeToString(BridgeResponse.serializer(), result.toBridgeResponse(request.id))
        }
    }

    private suspend fun fail(
        pluginId: PluginId,
        operation: PluginOperation,
        code: String,
        retryable: Boolean = false,
    ): PluginCallResult.Failure {
        diagnostics.record(
            PluginDiagnosticEvent(
                pluginId = pluginId,
                code = code.safeCode(),
                operation = operation.wireName,
                occurredAtEpochMillis = nowEpochMillis(),
            ),
        )
        return PluginCallResult.Failure(code.safeCode(), retryable)
    }

    private fun String.safeCode(): String = takeIf(SAFE_CODE::matches) ?: "plugin.execution_failed"

    private companion object {
        val SAFE_CODE = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
    }
}
