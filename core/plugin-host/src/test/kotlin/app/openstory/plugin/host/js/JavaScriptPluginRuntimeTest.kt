package app.openstory.plugin.host.js

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JavaScriptPluginRuntimeTest {
    @Test
    fun timeoutDiscardsOperationAndReportsOnlyScriptHash() = runTest {
        val executor = HangingExecutor()
        val runtime = JavaScriptPluginRuntime(
            executor = executor,
            dispatcher = JsCapabilityDispatcher(manifest(), NoOpGateway()),
            limits = JsRuntimeLimits(maxDurationMillis = 1),
        )

        val result = runtime.invoke(
            source = "globalThis.openstoryPlugin = { search: async () => ({}) };",
            operation = "search",
            inputJson = "{}",
        ) { AppResult.Success(JsonPrimitive(it)) }

        val failure = assertIs<AppResult.Failure>(result)
        val error = assertIs<AppError.Plugin>(failure.error)
        assertEquals("plugin.javascript_timeout", error.code)
        assertEquals(
            AppError.Diagnostic.of(
                "script_hash" to "2ce1d2a9261606b1f66c0dd3e942c137d42a1d8880b734fd80e28104514ab6e3",
            ),
            error.diagnostic,
        )
        assertTrue(executor.closed)
    }

    @Test
    fun outputValidationFailureRetainsFieldPathAndAddsScriptHash() = runTest {
        val source = "globalThis.openstoryPlugin = { search: async () => ({}) };"
        val runtime = JavaScriptPluginRuntime(
            executor = StaticExecutor("{}"),
            dispatcher = JsCapabilityDispatcher(manifest(), NoOpGateway()),
        )

        val result: AppResult<String> = runtime.invoke(source, "search", "{}") {
            AppResult.Failure(
                AppError.Plugin(
                    code = "plugin.output_undeclared_host",
                    retryable = false,
                    diagnostic = AppError.Diagnostic.of("field_path" to "details.image.url"),
                ),
            )
        }

        val failure = assertIs<AppResult.Failure>(result)
        assertEquals(
            AppError.Diagnostic.of(
                "field_path" to "details.image.url",
                "script_hash" to "2ce1d2a9261606b1f66c0dd3e942c137d42a1d8880b734fd80e28104514ab6e3",
            ),
            failure.error.diagnostic,
        )
    }

    @Test
    fun callerCancellationDiscardsOperationWithoutReturningFailure() = runTest {
        val executor = HangingExecutor()
        val runtime = JavaScriptPluginRuntime(
            executor,
            JsCapabilityDispatcher(manifest(), NoOpGateway()),
        )
        val deferred = async {
            runtime.invoke(
                source = "globalThis.openstoryPlugin = { search: async () => ({}) };",
                operation = "search",
                inputJson = "{}",
            ) { AppResult.Success(it) }
        }

        yield()
        deferred.cancel()

        assertFailsWith<kotlinx.coroutines.CancellationException> { deferred.await() }
        assertTrue(executor.closed)
    }

    @Test
    fun gatewayExceptionBecomesSafeBridgeFailure() = runTest {
        val executor = BridgeInvokingExecutor()
        val runtime = JavaScriptPluginRuntime(
            executor,
            JsCapabilityDispatcher(
                manifest().copy(
                    allowedHosts = setOf("allowed.example"),
                    capabilities = setOf(app.openstory.plugin.api.PluginCapability.NETWORK),
                ),
                ThrowingGateway(),
            ),
        )

        runtime.invoke(
            source = "globalThis.openstoryPlugin = { search: async () => ({}) };",
            operation = "search",
            inputJson = "{}",
        ) { AppResult.Success(it) }

        val response = Json.parseToJsonElement(requireNotNull(executor.bridgeResponse)).jsonObject
        assertEquals("call-1", response["id"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "plugin.bridge_dispatch_failed",
            response["error"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun manifest() = PluginManifest(
        id = "community.javascript",
        name = "JavaScript",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://updates.example/manifest.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = emptySet(),
        runtime = PluginRuntime.JAVASCRIPT,
        entry = "main.js",
    )

    private class HangingExecutor : JsIsolateExecutor {
        var closed = false

        override suspend fun execute(
            source: String,
            operation: String,
            inputJson: String,
            limits: JsRuntimeLimits,
            bridge: suspend (String) -> String,
        ): String = try {
            awaitCancellation()
        } finally {
            closed = true
        }
    }

    private class StaticExecutor(
        private val output: String,
    ) : JsIsolateExecutor {
        override suspend fun execute(
            source: String,
            operation: String,
            inputJson: String,
            limits: JsRuntimeLimits,
            bridge: suspend (String) -> String,
        ): String = output
    }

    private class BridgeInvokingExecutor : JsIsolateExecutor {
        var bridgeResponse: String? = null

        override suspend fun execute(
            source: String,
            operation: String,
            inputJson: String,
            limits: JsRuntimeLimits,
            bridge: suspend (String) -> String,
        ): String {
            bridgeResponse = bridge(
                """{"id":"call-1","method":"http.execute","params":{"url":"https://allowed.example/data"}}""",
            )
            return "{}"
        }
    }

    private class NoOpGateway : PluginHttpGateway {
        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> = error("HTTP must not run")
    }

    private class ThrowingGateway : PluginHttpGateway {
        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> = error("private gateway detail")
    }
}
