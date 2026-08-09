package app.openstory.plugin.host.js

import app.openstory.common.AppResult
import app.openstory.network.PluginHttpGateway
import app.openstory.network.PluginHttpRequest
import app.openstory.network.PluginHttpResponse
import app.openstory.network.RequestBudget
import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsCapabilityDispatcherTest {
    @Test
    fun undeclaredNetworkCapabilityIsDeniedBeforeGatewayDispatch() = runTest {
        val gateway = RecordingGateway()
        val dispatcher = JsCapabilityDispatcher(manifest(network = false), gateway)
        val request = JsBridgeRequest(
            id = "call-1",
            method = "http.execute",
            params = buildJsonObject { put("url", "https://evil.invalid/private?token=secret") },
        )

        val response = dispatcher.dispatch(request, JsOperationBudget())

        assertEquals("call-1", response.id)
        assertEquals("plugin.capability_denied", response.error?.code)
        assertNull(response.result)
        assertEquals(0, gateway.requests.size)
    }

    @Test
    fun requestHeadersAreForwardedToGateway() = runTest {
        val gateway = RecordingGateway()
        val dispatcher = JsCapabilityDispatcher(manifest(network = true), gateway)
        val request = JsBridgeRequest(
            id = "call-headers",
            method = "http.execute",
            params = buildJsonObject {
                put("url", "https://allowed.example/data")
                put(
                    "headers",
                    buildJsonObject {
                        put("Accept", "application/json")
                        put("X-Source-Client-ID", "catalog-client")
                    },
                )
            },
        )

        val response = dispatcher.dispatch(request, JsOperationBudget())

        assertNull(response.error)
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "X-Source-Client-ID" to "catalog-client",
            ),
            gateway.requests.single().headers,
        )
    }

    @Test
    fun invalidRequestHeaderIsRejectedBeforeGatewayDispatch() = runTest {
        val gateway = RecordingGateway()
        val dispatcher = JsCapabilityDispatcher(manifest(network = true), gateway)
        val request = JsBridgeRequest(
            id = "call-invalid-header",
            method = "http.execute",
            params = buildJsonObject {
                put("url", "https://allowed.example/data")
                put(
                    "headers",
                    buildJsonObject { put("X-Test", "ok\r\ninjected: true") },
                )
            },
        )

        val response = dispatcher.dispatch(request, JsOperationBudget())

        assertEquals("plugin.bridge_message_invalid", response.error?.code)
        assertNull(response.result)
        assertEquals(0, gateway.requests.size)
    }

    @Test
    fun responseLargerThanOperationLimitFailsClosed() = runTest {
        val gateway = RecordingGateway("large".encodeToByteArray())
        val dispatcher = JsCapabilityDispatcher(manifest(network = true), gateway)
        val request = JsBridgeRequest(
            id = "call-2",
            method = "http.execute",
            params = buildJsonObject { put("url", "https://allowed.example/data") },
        )

        val response = dispatcher.dispatch(
            request,
            JsOperationBudget(JsRuntimeLimits(maxResponseBytes = 4)),
        )

        assertEquals("plugin.response_too_large", response.error?.code)
        assertNull(response.result)
    }

    private fun manifest(network: Boolean) = PluginManifest(
        id = "community.javascript",
        name = "JavaScript",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://updates.example/manifest.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = if (network) setOf("allowed.example") else emptySet(),
        capabilities = if (network) setOf(PluginCapability.NETWORK) else emptySet(),
        runtime = PluginRuntime.JAVASCRIPT,
        entry = "main.js",
    )

    private class RecordingGateway(
        private val body: ByteArray = byteArrayOf(),
    ) : PluginHttpGateway {
        val requests = mutableListOf<PluginHttpRequest>()

        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> {
            requests += request
            return AppResult.Success(
                PluginHttpResponse(200, emptyMap(), body, body.decodeToString()),
            )
        }
    }
}
