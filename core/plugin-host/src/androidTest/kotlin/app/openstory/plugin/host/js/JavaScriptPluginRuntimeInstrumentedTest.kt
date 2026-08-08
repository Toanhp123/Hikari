package app.openstory.plugin.host.js

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.AppError
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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class JavaScriptPluginRuntimeInstrumentedTest {
    @Test
    fun scriptCannotCallUndeclaredHost() = runTest {
        assumeTrue(JavaScriptSandbox.isSupported())
        val executor = AndroidxJsIsolateExecutor(
            ApplicationProvider.getApplicationContext(),
        )
        val runtime = JavaScriptPluginRuntime(
            executor,
            JsCapabilityDispatcher(manifest(), AllowlistGateway()),
        )

        val result = try {
            runtime.invoke(
                source = """
                    globalThis.openstoryPlugin = {
                      search: async () => host.http({url: "https://evil.invalid/private"})
                    };
                """.trimIndent(),
                operation = "search",
                inputJson = "{}",
            ) { AppResult.Success(it) }
        } finally {
            executor.close()
        }

        val failure = assertIs<AppResult.Failure>(result)
        assertEquals("plugin.domain_denied", failure.error.code)
    }

    private fun manifest() = PluginManifest(
        id = "community.javascript",
        name = "JavaScript",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://allowed.example/manifest.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = setOf("allowed.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.JAVASCRIPT,
        entry = "main.js",
    )

    private class AllowlistGateway : PluginHttpGateway {
        override suspend fun execute(
            request: PluginHttpRequest,
            budget: RequestBudget,
        ): AppResult<PluginHttpResponse> = if (request.url.startsWith("https://allowed.example/")) {
            AppResult.Success(PluginHttpResponse(200, emptyMap(), byteArrayOf(), "{}"))
        } else {
            AppResult.Failure(AppError.Network("plugin.domain_denied", retryable = false))
        }
    }
}
