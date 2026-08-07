package app.openstory.plugin.api.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectorDefinitionDecoderTest {
    private val decoder = SelectorDefinitionDecoder()

    @Test
    fun decodesCanonicalSchemaOne() {
        val decoded = decoder.decode(
            """{"schemaVersion":1,"catalog":null,"content":{"search":null,"story":null,"latest":null,"allChapters":null,"sync":null,"chapter":null}}""",
        ).getOrThrow()

        assertEquals(1, decoded.schemaVersion)
    }

    @Test
    fun rejectsEveryUnknownSchemaVersion() {
        val result = decoder.decode("""{"schemaVersion":2}""")

        assertEquals(
            SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
            (result.exceptionOrNull() as SelectorValidationException).code,
        )
    }

    @Test
    fun normalizesNonContractParserFailures() {
        val result = decoder.decode("""{"schemaVersion":1,"content":42}""")

        assertTrue(result.isFailure)
        assertEquals(
            SelectorValidationErrorCode.INVALID_DEFINITION,
            (result.exceptionOrNull() as SelectorValidationException).code,
        )
    }
}
