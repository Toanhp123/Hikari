package app.openstory.plugin.api.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectorValidationTest {

    @Test
    fun definitionRejectsCrossHostRequestTemplate() {
        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(
                    urlTemplate = "https://evil.invalid/search?q={query}",
                ),
            ),
        )

        val result = SelectorValidation.validate(
            definition = definition,
            allowedHosts = setOf("allowed.example"),
        )

        assertEquals(
            SelectorValidationErrorCode.UNDECLARED_HOST,
            result.validationCode(),
        )
    }

    @Test
    fun definitionRejectsUnsupportedSchemaVersion() {
        val definition = SelectorPluginDefinition(
            schemaVersion = SelectorPluginDefinition.CURRENT_SCHEMA_VERSION + 1,
            operations = listOf(
                HttpGet(urlTemplate = "/search?q={query}"),
            ),
        )

        val result = SelectorValidation.validate(
            definition = definition,
            allowedHosts = setOf("allowed.example"),
        )

        assertEquals(
            SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
            result.validationCode(),
        )
    }

    @Test
    fun definitionRejectsInsecureAndProtocolRelativeRequests() {
        val insecure = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = listOf(
                    HttpGet(
                        urlTemplate =
                            "http://allowed.example/search?q={query}",
                    ),
                ),
            ),
            allowedHosts = setOf("allowed.example"),
        )

        val protocolRelative = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = listOf(
                    HttpGet(
                        urlTemplate =
                            "//allowed.example/search?q={query}",
                    ),
                ),
            ),
            allowedHosts = setOf("allowed.example"),
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
    fun definitionAcceptsRelativeAndDeclaredHttpsRequests() {
        val relative = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = listOf(
                    HttpGet(urlTemplate = "/search?q={query}"),
                ),
            ),
            allowedHosts = setOf("allowed.example"),
        )

        val absolute = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = listOf(
                    HttpGet(
                        urlTemplate =
                            "https://allowed.example/search?q={query}",
                    ),
                ),
            ),
            allowedHosts = setOf("allowed.example"),
        )

        assertTrue(relative.isSuccess)
        assertTrue(absolute.isSuccess)
    }

    @Test
    fun operationsDeclareExplicitInputAndOutputTypes() {
        assertEquals(
            SelectorValueType.NONE to SelectorValueType.DOCUMENT,
            HttpGet("/story").types(),
        )
        assertEquals(
            SelectorValueType.DOCUMENT to SelectorValueType.DOCUMENT,
            RemoveElements(".advertisement").types(),
        )
        assertEquals(
            SelectorValueType.DOCUMENT to SelectorValueType.ELEMENTS,
            SelectAll(".chapter").types(),
        )
        assertEquals(
            SelectorValueType.ELEMENTS to SelectorValueType.TEXT,
            SelectText(".title").types(),
        )
        assertEquals(
            SelectorValueType.ELEMENTS to SelectorValueType.TEXT,
            SelectAttribute("a", "href").types(),
        )
        assertEquals(
            SelectorValueType.TEXT to SelectorValueType.TEXT,
            NormalizeWhitespace().types(),
        )
    }

    @Test
    fun definitionRejectsTypeMismatchedPipeline() {
        val definition = SelectorPluginDefinition(
            operations = listOf(
                HttpGet(urlTemplate = "/story"),
                NormalizeWhitespace(),
            ),
        )

        val result = SelectorValidation.validate(
            definition = definition,
            allowedHosts = setOf("allowed.example"),
        )

        assertEquals(
            SelectorValidationErrorCode.TYPE_MISMATCH,
            result.validationCode(),
        )
    }

    @Test
    fun definitionRejectsBlankCssAndAttributeNames() {
        val blankCss = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = listOf(
                    HttpGet(urlTemplate = "/story"),
                    SelectAll(css = "   "),
                ),
            ),
            allowedHosts = setOf("allowed.example"),
        )

        val blankAttribute = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = listOf(
                    HttpGet(urlTemplate = "/story"),
                    SelectAll(css = "article"),
                    SelectAttribute(
                        css = "a",
                        attribute = " ",
                    ),
                ),
            ),
            allowedHosts = setOf("allowed.example"),
        )

        assertEquals(
            SelectorValidationErrorCode.BLANK_CSS_SELECTOR,
            blankCss.validationCode(),
        )
        assertEquals(
            SelectorValidationErrorCode.BLANK_ATTRIBUTE_NAME,
            blankAttribute.validationCode(),
        )
    }

    @Test
    fun definitionRejectsEmptyPipeline() {
        val result = SelectorValidation.validate(
            definition = SelectorPluginDefinition(
                operations = emptyList(),
            ),
            allowedHosts = setOf("allowed.example"),
        )

        assertEquals(
            SelectorValidationErrorCode.EMPTY_PIPELINE,
            result.validationCode(),
        )
    }

    private fun SelectorOperation.types():
        Pair<SelectorValueType, SelectorValueType> =
        inputType to outputType

    private fun Result<Unit>.validationCode():
        SelectorValidationErrorCode =
        (exceptionOrNull() as SelectorValidationException).code
}
