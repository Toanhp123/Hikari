package app.openstory.plugins.runtime.execution

import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.PluginCapabilities
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.manifest.PluginProtocolVersion
import app.openstory.plugins.api.manifest.PluginService
import app.openstory.plugins.api.manifest.NetworkCapability
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.runtime.PluginCallResult
import app.openstory.plugins.runtime.capabilities.CapabilityDispatcher
import app.openstory.plugins.runtime.capabilities.http.PluginHttpRequest
import app.openstory.plugins.runtime.persistence.PluginDiagnosticEvent
import app.openstory.plugins.runtime.persistence.PluginDiagnosticsSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class PluginOperationRunnerTest {
    @Test
    fun operationWithoutNetworkCapabilityCanExecute() = runTest {
        val diagnostics = MemoryDiagnosticsSink()
        val engine = JavaScriptEngine { _, _, _, _, _ -> "{\"sections\":[]}" }
        val runner = PluginOperationRunner(engine, broker(), diagnostics)

        val result = runner.run(
            PluginId("org.example.plugin"),
            manifestWithoutNetwork(),
            "globalThis.openstoryPlugin = {};",
            PluginOperation.CATALOG_HOME,
            JsonObject(emptyMap()),
        )

        assertIs<PluginCallResult.Success<*>>(result)
    }

    @Test
    fun undeclaredHostCallReturnsDomainDenied() = runTest {
        val diagnostics = MemoryDiagnosticsSink()
        val engine = bridgeFailureEngine(
            "http.execute",
            Json.encodeToJsonElement(
                PluginHttpRequest.serializer(),
                PluginHttpRequest("https://denied.example/resource"),
            ),
        )
        val result = runner(engine, diagnostics).run(
            PluginId("org.example.plugin"),
            manifestWithNetwork(),
            "",
            PluginOperation.CATALOG_HOME,
            JsonObject(emptyMap()),
        )

        assertEquals("plugin.http_domain_denied", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun retryableCapabilityFailureSurvivesBridge() = runTest {
        val diagnostics = MemoryDiagnosticsSink()
        val engine = bridgeFailureEngine(
            "http.execute",
            Json.encodeToJsonElement(
                PluginHttpRequest.serializer(),
                PluginHttpRequest("https://api.example.com/resource"),
            ),
        )
        val capabilities = CapabilityDispatcher { _, _, _, _, _ ->
            PluginCallResult.Failure("plugin.http_request_failed", retryable = true)
        }
        val runner = PluginOperationRunner(engine, capabilities, diagnostics)

        val failure = assertIs<PluginCallResult.Failure>(
            runner.run(
                PluginId("org.example.plugin"),
                manifestWithNetwork(),
                "",
                PluginOperation.CATALOG_HOME,
                JsonObject(emptyMap()),
            ),
        )

        assertEquals("plugin.http_request_failed", failure.code)
        assertTrue(failure.retryable)
    }

    @Test
    fun unknownHostCapabilityMethodReturnsCapabilityDenied() = runTest {
        val diagnostics = MemoryDiagnosticsSink()
        val result = runner(
            bridgeFailureEngine("filesystem.read", JsonObject(emptyMap())),
            diagnostics,
        ).run(
            PluginId("org.example.plugin"),
            manifestWithNetwork(),
            "",
            PluginOperation.CATALOG_HOME,
            JsonObject(emptyMap()),
        )

        assertEquals("plugin.capability_denied", assertIs<PluginCallResult.Failure>(result).code)
    }

    @Test
    fun timeoutDiscardsOnlyCurrentInvocation() = runTest {
        var invocation = 0
        val diagnostics = MemoryDiagnosticsSink()
        val engine = JavaScriptEngine { _, _, _, _, _ ->
            invocation++
            if (invocation == 1) awaitCancellation()
            "{\"sections\":[]}"
        }
        val runner = runner(engine, diagnostics, RuntimeLimits(timeoutMillis = 50))

        val timedOut = runner.run(
            PluginId("org.example.plugin"),
            manifestWithoutNetwork(),
            "",
            PluginOperation.CATALOG_HOME,
            JsonObject(emptyMap()),
        )
        val next = runner.run(
            PluginId("org.example.plugin"),
            manifestWithoutNetwork(),
            "",
            PluginOperation.CATALOG_HOME,
            JsonObject(emptyMap()),
        )

        assertEquals("plugin.execution_timeout", assertIs<PluginCallResult.Failure>(timedOut).code)
        assertIs<PluginCallResult.Success<*>>(next)
    }

    @Test
    fun oversizedOutputIsRejected() = runTest {
        val diagnostics = MemoryDiagnosticsSink()
        val result = runner(
            JavaScriptEngine { _, _, _, _, _ -> "{\"sections\":[]}" },
            diagnostics,
            RuntimeLimits(maxOutputJsonBytes = 4),
        ).run(
            PluginId("org.example.plugin"),
            manifestWithoutNetwork(),
            "",
            PluginOperation.CATALOG_HOME,
            JsonObject(emptyMap()),
        )

        assertEquals("plugin.output_too_large", assertIs<PluginCallResult.Failure>(result).code)
    }

    private fun runner(
        engine: JavaScriptEngine,
        diagnostics: PluginDiagnosticsSink,
        limits: RuntimeLimits = RuntimeLimits(),
    ) = PluginOperationRunner(engine, broker(), diagnostics, limits)

    private fun bridgeFailureEngine(
        method: String,
        payload: kotlinx.serialization.json.JsonElement,
    ) = JavaScriptEngine { _, _, _, _, bridge ->
        val request = BridgeRequest("call-1", method, payload)
        val response = Json.decodeFromString(
            BridgeResponse.serializer(),
            bridge(Json.encodeToString(BridgeRequest.serializer(), request)),
        )
        val error = checkNotNull(response.error)
        throw JavaScriptExecutionFailure(error.code, error.retryable)
    }

    private fun broker(): CapabilityDispatcher =
        CapabilityDispatcher { _, _, method, _, _ ->
            when (method) {
                "http.execute" -> PluginCallResult.Failure("plugin.http_domain_denied", false)
                else -> PluginCallResult.Failure("plugin.capability_denied", false)
            }
        }

    private fun manifestWithoutNetwork() = PluginManifest(
        id = "org.example.plugin",
        name = "Example",
        version = "1.0.0",
        protocol = PluginProtocolVersion(1),
        provides = setOf(PluginService.CATALOG),
        capabilities = PluginCapabilities(),
    )

    private fun manifestWithNetwork() = manifestWithoutNetwork().copy(
        capabilities = PluginCapabilities(NetworkCapability(setOf("api.example.com"))),
    )
}

private class MemoryDiagnosticsSink : PluginDiagnosticsSink {
    private val events = mutableListOf<PluginDiagnosticEvent>()

    override suspend fun record(event: PluginDiagnosticEvent) {
        events += event
    }

    override suspend fun recent(pluginId: PluginId, limit: Int): List<PluginDiagnosticEvent> =
        events.filter { it.pluginId == pluginId }.takeLast(limit)
}
