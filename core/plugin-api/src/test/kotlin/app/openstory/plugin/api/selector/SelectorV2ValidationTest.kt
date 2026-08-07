package app.openstory.plugin.api.selector

import app.openstory.plugin.api.PluginApiVersion
import app.openstory.plugin.api.PluginCapability
import app.openstory.plugin.api.PluginKind
import app.openstory.plugin.api.PluginManifest
import app.openstory.plugin.api.PluginRuntime
import app.openstory.plugin.api.selector.catalog.CatalogSelectorEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorV2ValidationTest {
    @Test
    fun relativeRequestRequiresExplicitDeclarativeOrigin() {
        val definition = SelectorPluginDefinitionV2(
            catalog = CatalogSelectorEndpoints(
                search = null,
            ),
        )
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
        // Keep the root reference live so the test also compiles the V2 envelope.
        assertEquals(2, definition.schemaVersion)
    }

    @Test
    fun requestPlanMustFinishWithDocument() {
        val result = SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(
                operations = listOf(
                    HttpGet("https://allowed.example/search"),
                    SelectAll("article"),
                ),
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

        val result = SelectorValidation.validateBinding(binding)

        assertEquals(
            SelectorValidationErrorCode.EXCESSIVE_BINDING_DEPTH,
            result.validationCode(),
        )
    }

    @Test
    fun requestPlanRejectsMoreThanSixtyFourOperations() {
        val operations = buildList {
            add(HttpGet("https://allowed.example/search"))
            repeat(64) { index ->
                add(RemoveElements(".noise-$index"))
            }
        }

        val result = SelectorValidation.validateRequestPlan(
            request = SelectorRequestPlan(operations),
            manifest = manifest("https://allowed.example/"),
        )

        assertEquals(
            SelectorValidationErrorCode.EXCESSIVE_OPERATION_COUNT,
            result.validationCode(),
        )
    }

    @Test
    fun bindingValidationRejectsMoreThanFiveHundredTwelveNodes() {
        val fields = (0 until 128).associate { index ->
            "field$index" to OptionalBinding(
                OptionalBinding(
                    OptionalBinding(TextBinding()),
                ),
            )
        }

        val result = SelectorValidation.validateBinding(ObjectBinding(fields))

        assertEquals(
            SelectorValidationErrorCode.EXCESSIVE_BINDING_COUNT,
            result.validationCode(),
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
