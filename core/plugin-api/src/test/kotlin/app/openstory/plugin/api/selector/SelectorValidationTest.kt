package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectorValidationTest {
    @Test
    fun requestRejectsCrossHostTemplate() {
        val result = validateRequest(
            HttpGet("https://evil.invalid/search?q={query}"),
        )

        assertEquals(
            SelectorValidationErrorCode.UNDECLARED_HOST,
            result.validationCode(),
        )
    }

    @Test
    fun requestRejectsInsecureAndProtocolRelativeTemplates() {
        val insecure = validateRequest(
            HttpGet("http://allowed.example/search?q={query}"),
        )
        val protocolRelative = validateRequest(
            HttpGet("//allowed.example/search?q={query}"),
        )

        assertEquals(
            SelectorValidationErrorCode.INSECURE_SCHEME,
            insecure.validationCode(),
        )
        assertEquals(
            SelectorValidationErrorCode.PROTOCOL_RELATIVE_URL,
            protocolRelative.validationCode(),
        )
    }

    @Test
    fun requestAcceptsRelativeAndDeclaredHttpsTemplates() {
        assertTrue(validateRequest(HttpGet("/search?q={query}")).isSuccess)
        assertTrue(
            validateRequest(
                HttpGet("https://allowed.example/search?q={query}"),
            ).isSuccess,
        )
    }

    @Test
    fun requestRejectsSecondHttpGet() {
        val result = SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(
                operations = listOf(
                    HttpGet("/story"),
                    HttpGet("/other"),
                ),
            ),
            manifest = manifest(),
        )

        assertEquals(
            SelectorValidationErrorCode.TYPE_MISMATCH,
            result.validationCode(),
        )
    }

    @Test
    fun requestRejectsBlankRemovalSelector() {
        val result = SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(
                operations = listOf(
                    HttpGet("/story"),
                    RemoveElements("   "),
                ),
            ),
            manifest = manifest(),
        )

        assertEquals(
            SelectorValidationErrorCode.BLANK_CSS_SELECTOR,
            result.validationCode(),
        )
    }

    @Test
    fun requestRejectsEmptyPipeline() {
        val result = SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(emptyList()),
            manifest = manifest(),
        )

        assertEquals(
            SelectorValidationErrorCode.EMPTY_PIPELINE,
            result.validationCode(),
        )
    }

    private fun validateRequest(operation: SelectorRequestOperation): Result<Unit> =
        SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(listOf(operation)),
            manifest = manifest(),
        )

    private fun manifest() = PluginManifest(
        id = "community.selector",
        name = "Selector",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://allowed.example/plugin.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("en"),
        allowedHosts = setOf("allowed.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.DECLARATIVE,
        entry = "selector.json",
        declarativeOrigin = "https://allowed.example/",
    )

    private fun Result<Unit>.validationCode() =
        (exceptionOrNull() as SelectorValidationException).code
}
