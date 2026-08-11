package app.openstory.plugins.api.protocol

import app.openstory.plugins.api.protocol.catalog.WireContentType
import app.openstory.plugins.api.protocol.content.ContentResolveUrlRequestDto
import app.openstory.plugins.api.protocol.content.ContentChapterListModeDto
import app.openstory.plugins.api.protocol.content.ContentChaptersRequestDto
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PluginProtocolValidatorTest {
    @Test
    fun chapterRequestWithoutModeKeepsProtocolOneFullCompatibility() {
        val request = Json.decodeFromString<ContentChaptersRequestDto>("""{"sourceStoryId":"story"}""")

        assertEquals(ContentChapterListModeDto.FULL, request.mode)
    }

    @Test
    fun chapterRequestRoundTripsEveryListMode() {
        val modes = ContentChapterListModeDto.entries

        assertEquals(
            modes,
            modes.map { mode ->
                Json.decodeFromString<ContentChaptersRequestDto>(
                    Json.encodeToString(ContentChaptersRequestDto("story", mode = mode)),
                ).mode
            },
        )
    }

    @Test
    fun chapterRequestRejectsBlankOrOversizedCheckpointAndToken() {
        assertFailsWith<IllegalArgumentException> { ContentChaptersRequestDto("story", checkpoint = " ") }
        assertFailsWith<IllegalArgumentException> { ContentChaptersRequestDto("story", nextToken = " ") }
        assertFailsWith<IllegalArgumentException> {
            ContentChaptersRequestDto("story", checkpoint = "x".repeat(4_097))
        }
        assertFailsWith<IllegalArgumentException> {
            ContentChaptersRequestDto("story", nextToken = "x".repeat(4_097))
        }
    }

    @Test
    fun contentChaptersRejectsMalformedReleaseOutput() {
        val payload = Json.parseToJsonElement("""{"items":[{"sourceReleaseId":"","rawNumber":"1"}]}""")

        assertEquals(
            listOf("protocol.invalid_payload"),
            PluginProtocolValidator.validateOutput(
                operation = PluginOperation.CONTENT_CHAPTERS,
                payload = payload,
                allowedNetworkHosts = emptySet(),
            ).map(ProtocolViolation::code),
        )
    }

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
