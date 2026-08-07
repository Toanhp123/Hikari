package app.openstory.plugin.api.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SelectorDefinitionDecoderTest {
    private val decoder = SelectorDefinitionDecoder()

    @Test
    fun decoderPreservesVersionOneDefinition() {
        val result = decoder.decode(
            """
            {
              "schemaVersion": 1,
              "operations": [
                {
                  "type": "http_get",
                  "urlTemplate": "https://allowed.example/index"
                }
              ]
            }
            """.trimIndent(),
        ).getOrThrow()

        assertIs<DecodedSelectorDefinition.V1>(result)
        assertEquals(1, result.definition.schemaVersion)
    }

    @Test
    fun decoderReadsVersionTwoEnvelope() {
        val result = decoder.decode(
            """{"schemaVersion":2,"catalog":null,"content":null}""",
        ).getOrThrow()

        assertIs<DecodedSelectorDefinition.V2>(result)
        assertEquals(2, result.definition.schemaVersion)
    }

    @Test
    fun decoderRejectsUnknownVersionAndVariant() {
        val unknownVersion = decoder.decode("""{"schemaVersion":99}""")
        assertEquals(
            SelectorValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
            (unknownVersion.exceptionOrNull() as SelectorValidationException).code,
        )

        val unknownVariant = decoder.decode(
            """
            {
              "schemaVersion": 2,
              "catalog": {
                "search": {
                  "request": {
                    "operations": [
                      {"type":"http_get","urlTemplate":"/search"}
                    ]
                  },
                  "items": {
                    "type": "executable_callback",
                    "source": "evil"
                  }
                }
              }
            }
            """.trimIndent(),
        )
        assertTrue(unknownVariant.isFailure)
    }

    @Test
    fun versionOneEncodingDoesNotGainVersionTwoFields() {
        val encoded = SELECTOR_JSON.encodeToString(
            SelectorPluginDefinition.serializer(),
            SelectorPluginDefinition(
                operations = listOf(HttpGet("https://allowed.example/index")),
            ),
        )

        assertFalse(encoded.contains("\"catalog\""))
        assertFalse(encoded.contains("\"content\""))
    }
}
