package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorDefinitionValidationTest {
    @Test
    fun relativeRequestRequiresExplicitDeclarativeOrigin() {
        val request = SelectorRequestPlan(
            operations = listOf(HttpGet("/search?q={query}")),
        )

        val result = SelectorValidation.validateRequestPlan(
            request = request,
            manifest = manifest(declarativeOrigin = null),
        )

        assertEquals(
            SelectorValidationErrorCode.INVALID_DECLARATIVE_ORIGIN,
            result.validationCode(),
        )
    }

    @Test
    fun requestPlanMustStartWithHttpGet() {
        val result = SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(
                operations = listOf(RemoveElements(".noise")),
            ),
            manifest = manifest("https://allowed.example/"),
        )

        assertEquals(
            SelectorValidationErrorCode.TYPE_MISMATCH,
            result.validationCode(),
        )
    }

    @Test
    fun bindingValidationRejectsExcessiveDepth() {
        var binding: SelectorBinding = TextBinding()
        repeat(13) {
            binding = OptionalBinding(binding)
        }

        assertEquals(
            SelectorValidationErrorCode.EXCESSIVE_BINDING_DEPTH,
            SelectorValidation.validateBinding(binding).validationCode(),
        )
    }

    @Test
    fun requestPlanRejectsMoreThanSixtyFourOperations() {
        val operations = buildList {
            add(HttpGet("https://allowed.example/search"))
            repeat(64) { index -> add(RemoveElements(".noise-$index")) }
        }

        assertEquals(
            SelectorValidationErrorCode.EXCESSIVE_OPERATION_COUNT,
            SelectorValidation.validateRequestPlan(
                request = SelectorRequestPlan(operations),
                manifest = manifest("https://allowed.example/"),
            ).validationCode(),
        )
    }

    @Test
    fun bindingValidationRejectsMoreThanFiveHundredTwelveNodes() {
        val fields = (0 until 128).associate { index ->
            "field$index" to OptionalBinding(
                OptionalBinding(OptionalBinding(TextBinding())),
            )
        }

        assertEquals(
            SelectorValidationErrorCode.EXCESSIVE_BINDING_COUNT,
            SelectorValidation.validateBinding(ObjectBinding(fields)).validationCode(),
        )
    }

    @Test
    fun timestampBindingRequiresCoherentConfiguration() {
        val missingPattern = SelectorValidation.validateBinding(
            TimestampBinding(
                source = TextBinding(),
                format = SelectorTimestampFormat.HOST_PATTERN_ID,
            ),
        )
        val unexpectedPattern = SelectorValidation.validateBinding(
            TimestampBinding(
                source = TextBinding(),
                format = SelectorTimestampFormat.ISO_8601,
                hostPatternId = "unexpected",
            ),
        )

        assertEquals(
            SelectorValidationErrorCode.INVALID_TIMESTAMP_CONFIGURATION,
            missingPattern.validationCode(),
        )
        assertEquals(
            SelectorValidationErrorCode.INVALID_TIMESTAMP_CONFIGURATION,
            unexpectedPattern.validationCode(),
        )
    }

    private fun manifest(declarativeOrigin: String?) = PluginManifest(
        id = "community.selector",
        name = "Selector",
        version = "1.0.0",
        packageChecksumSha256 = "a".repeat(64),
        minimumHostVersion = "1.0.0",
        updateUrl = "https://allowed.example/plugin.json",
        api = PluginApiVersion(1, 0),
        kinds = setOf(PluginKind.CATALOG),
        languages = setOf("vi"),
        allowedHosts = setOf("allowed.example"),
        capabilities = setOf(PluginCapability.NETWORK),
        runtime = PluginRuntime.DECLARATIVE,
        entry = "selector.json",
        declarativeOrigin = declarativeOrigin,
    )

    private fun Result<Unit>.validationCode() =
        (exceptionOrNull() as SelectorValidationException).code
}
