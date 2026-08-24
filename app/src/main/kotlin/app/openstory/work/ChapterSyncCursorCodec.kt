package app.openstory.work

import app.openstory.chapters.sync.ChapterSyncBatchCursor
import app.openstory.common.id.StoryId
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ChapterSyncCursorCodec(
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    fun encode(cursor: ChapterSyncBatchCursor): String {
        val payload = json.encodeToString(
            CursorPayload.serializer(),
            CursorPayload(
                version = CURRENT_VERSION,
                timestampBucket = cursor.timestampBucket,
                storyId = cursor.storyId.value,
            ),
        ).encodeToByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    fun decode(encoded: String): ChapterSyncBatchCursor {
        require(encoded.isNotBlank()) { "Cursor must not be blank" }
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Cursor is too large" }
        val decoded = runCatching { Base64.getUrlDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Cursor is not valid Base64", it) }
        require(decoded.size <= MAX_DECODED_LENGTH) { "Cursor payload is too large" }
        val payload = runCatching {
            json.decodeFromString(CursorPayload.serializer(), decoded.decodeToString())
        }.getOrElse { throw IllegalArgumentException("Cursor is not valid JSON", it) }
        require(payload.version == CURRENT_VERSION) { "Unsupported cursor version" }
        require(payload.timestampBucket == null || payload.timestampBucket >= 0L) {
            "Cursor timestamp must not be negative"
        }
        return ChapterSyncBatchCursor(payload.timestampBucket, StoryId(payload.storyId))
    }

    @Serializable
    private data class CursorPayload(
        @SerialName("v") val version: Int,
        @SerialName("t") val timestampBucket: Long?,
        @SerialName("s") val storyId: String,
    )

    private companion object {
        const val CURRENT_VERSION = 1
        const val MAX_ENCODED_LENGTH = 512
        const val MAX_DECODED_LENGTH = 256
    }
}
