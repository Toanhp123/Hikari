package app.openstory.plugin.host.js

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

fun interface JsIsolateExecutor {
    suspend fun execute(
        source: String,
        operation: String,
        inputJson: String,
        limits: JsRuntimeLimits,
        bridge: suspend (String) -> String,
    ): String
}

class JavaScriptPluginRuntime(
    private val executor: JsIsolateExecutor,
    private val dispatcher: JsCapabilityDispatcher,
    private val limits: JsRuntimeLimits = JsRuntimeLimits(),
) {
    suspend fun <T> invoke(
        source: String,
        operation: String,
        inputJson: String,
        decodeOutput: (String) -> AppResult<T>,
    ): AppResult<T> {
        val scriptHash = source.sha256()
        validateInput(source, operation, inputJson)?.let { code ->
            return javascriptFailure(code, scriptHash)
        }
        return try {
            val budget = JsOperationBudget(limits)
            val codec = JsBridgeCodec(limits)
            val output = withTimeout(limits.maxDurationMillis) {
                executor.execute(source, operation, inputJson, limits) { message ->
                    val response = when (val decoded = codec.decodeRequest(message)) {
                        is AppResult.Success -> try {
                            dispatcher.dispatch(decoded.value, budget)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Throwable) {
                            JsBridgeResponse.failure(decoded.value.id, BRIDGE_FAILED)
                        }
                        is AppResult.Failure -> JsBridgeResponse.failure(
                            id = INVALID_CALL_ID,
                            code = decoded.error.code,
                        )
                    }
                    codec.encodeResponse(response)
                }
            }
            if (output.encodeToByteArray().size > limits.maxOutputJsonBytes) {
                javascriptFailure(OUTPUT_TOO_LARGE, scriptHash)
            } else {
                decodeOutput(output).withScriptHash(scriptHash)
            }
        } catch (_: TimeoutCancellationException) {
            javascriptFailure(TIMEOUT, scriptHash)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: JsExecutionFailure) {
            javascriptFailure(failure.code, scriptHash)
        } catch (_: Throwable) {
            javascriptFailure(EXECUTION_FAILED, scriptHash)
        }
    }

    private fun validateInput(
        source: String,
        operation: String,
        inputJson: String,
    ): String? = when {
        source.encodeToByteArray().size > limits.maxSourceBytes -> SOURCE_TOO_LARGE
        inputJson.encodeToByteArray().size > limits.maxInputJsonBytes -> INPUT_TOO_LARGE
        !OPERATION.matches(operation) -> INVALID_OPERATION
        !isJsonObject(inputJson) -> INVALID_INPUT
        else -> null
    }

    private fun isJsonObject(source: String): Boolean = runCatching {
        Json.parseToJsonElement(source).jsonObject
    }.isSuccess

    private companion object {
        const val INVALID_CALL_ID = "invalid"
        const val SOURCE_TOO_LARGE = "plugin.javascript_source_too_large"
        const val INPUT_TOO_LARGE = "plugin.javascript_input_too_large"
        const val OUTPUT_TOO_LARGE = "plugin.javascript_output_too_large"
        const val INVALID_OPERATION = "plugin.javascript_operation_invalid"
        const val INVALID_INPUT = "plugin.javascript_input_invalid"
        const val BRIDGE_FAILED = "plugin.bridge_dispatch_failed"
        const val TIMEOUT = "plugin.javascript_timeout"
        const val EXECUTION_FAILED = "plugin.javascript_execution_failed"
        val OPERATION = Regex("[a-z][A-Za-z0-9]{0,63}")
    }
}

internal class JsExecutionFailure(
    val code: String,
) : RuntimeException(null, null, false, false)

private fun javascriptFailure(code: String, scriptHash: String): AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code = code,
            retryable = false,
            diagnostic = AppError.Diagnostic.of("script_hash" to scriptHash),
        ),
    )

private fun <T> AppResult<T>.withScriptHash(scriptHash: String): AppResult<T> = when (this) {
    is AppResult.Success -> this
    is AppResult.Failure -> AppResult.Failure(error.withScriptHash(scriptHash))
}

private fun AppError.withScriptHash(scriptHash: String): AppError {
    val enriched = diagnostic.with("script_hash" to scriptHash)
    return when (this) {
        is AppError.Network -> copy(diagnostic = enriched)
        is AppError.Validation -> copy(diagnostic = enriched)
        is AppError.Storage -> copy(diagnostic = enriched)
        is AppError.Plugin -> copy(diagnostic = enriched)
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
