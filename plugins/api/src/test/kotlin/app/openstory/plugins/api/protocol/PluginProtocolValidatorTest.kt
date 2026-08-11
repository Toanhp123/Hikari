package app.openstory.plugins.api.protocol

import app.openstory.plugins.api.protocol.catalog.WireContentType
import app.openstory.plugins.api.protocol.content.ContentResolveUrlRequestDto
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PluginProtocolValidatorTest {
    @Test
    fun resolveUrlRequestRejectsNonHttpsInput() {
        assertFailsWith<IllegalArgumentException> {
            ContentResolveUrlRequestDto("http://reader.example/story/1")
        }
    }

    @Test
    fun resolveUrlRequestRejectsOversizedInput() {
        val oversized = "https://reader.example/" + "a".repeat(5_000)

        assertFailsWith<IllegalArgumentException> {
            ContentResolveUrlRequestDto(oversized)
        }
    }

    @Test
    fun contentSearchRejectsReturnedUrlOutsideAcceptedHosts() {
        val payload = Json.parseToJsonElement(
            """{"items":[{"sourceStoryId":"1","title":"Story","sourceUrl":"https://evil.example/story/1"}]}""",
        )

        assertEquals(
            listOf("protocol.remote_host_denied"),
            PluginProtocolValidator.validateOutput(
                operation = PluginOperation.CONTENT_SEARCH,
                payload = payload,
                allowedNetworkHosts = setOf("reader.example"),
            ).map(ProtocolViolation::code),
        )
    }

    @Test
    fun resolveUrlAcceptsBoundedCandidateOnAcceptedHost() {
        val candidate = ContentStoryCandidateDto(
            sourceStoryId = "story-1",
            title = "The Story",
            aliases = listOf("Story"),
            authors = listOf("Author"),
            contentType = WireContentType.WEB_NOVEL,
            sourceUrl = "https://reader.example/story/1",
        )
        val payload = Json.parseToJsonElement(Json.encodeToString(candidate))

        assertEquals(
            emptyList(),
            PluginProtocolValidator.validateOutput(
                operation = PluginOperation.CONTENT_RESOLVE_URL,
                payload = payload,
                allowedNetworkHosts = setOf("reader.example"),
            ),
        )
    }
}
