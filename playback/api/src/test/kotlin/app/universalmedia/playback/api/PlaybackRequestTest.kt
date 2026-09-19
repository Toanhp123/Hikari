package app.universalmedia.playback.api

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.source.api.ResolvedVideo
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackRequestTest {
    private val request = PlaybackRequest(
        ConsumptionTargetRef.MediaTarget(MediaId(UUID.randomUUID())),
        ResolvedVideo("content://private/movie", "video/mp4"),
        PlaybackProvenance(SourceBindingId(UUID.randomUUID()), AssetId(UUID.randomUUID()), 1),
        initialPositionMs = 1234,
        expectedProgressRevision = 2,
    )

    @Test fun requestKeepsCanonicalTargetAndEphemeralContentSeparate() {
        assertEquals(1234L, request.initialPositionMs)
        assertEquals("content://private/movie", request.content.contentUri)
        assertFalse(request.toString().contains("content://private"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeResumeIsRejected() {
        request.copy(initialPositionMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeRevisionIsRejected() {
        request.copy(expectedProgressRevision = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAssetRevisionIsRejected() {
        request.provenance.copy(assetRevision = -1)
    }
}
