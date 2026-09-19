package app.universalmedia.playback.media3

import android.os.Bundle
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.core.model.UnitId
import app.universalmedia.playback.api.PlaybackProvenance
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.source.api.ResolvedVideo
import java.util.UUID

/** Private same-process session transport, never navigation or saved state. */
internal object PlaybackWire {
    const val START = "app.universalmedia.playback.START"

    fun encode(request: PlaybackRequest): Bundle = Bundle().apply {
        when (val target = request.target) {
            is ConsumptionTargetRef.MediaTarget -> {
                putString("media", target.mediaId.value.toString())
            }

            is ConsumptionTargetRef.UnitTarget -> putString("unit", target.unitId.value.toString())
        }
        putString("uri", request.content.contentUri)
        putString("mime", request.content.mimeType)
        putString("binding", request.provenance.bindingId.value.toString())
        putString("asset", request.provenance.assetId.value.toString())
        putLong("assetRevision", request.provenance.assetRevision)
        putLong("position", request.initialPositionMs)
        putLong("revision", request.expectedProgressRevision)
    }

    fun decode(args: Bundle): PlaybackRequest {
        val media = args.getString("media")
        val unit = args.getString("unit")
        require((media == null) != (unit == null))
        val target = if (media != null) {
            ConsumptionTargetRef.MediaTarget(MediaId(UUID.fromString(media)))
        } else {
            ConsumptionTargetRef.UnitTarget(UnitId(UUID.fromString(requireNotNull(unit))))
        }
        val uri = requireNotNull(args.getString("uri"))
        require(android.net.Uri.parse(uri).scheme == "content")
        require(args.getString("mime") == "video/mp4")
        return PlaybackRequest(
            target,
            ResolvedVideo(uri, "video/mp4"),
            PlaybackProvenance(
                SourceBindingId(UUID.fromString(requireNotNull(args.getString("binding")))),
                AssetId(UUID.fromString(requireNotNull(args.getString("asset")))),
                args.getLong("assetRevision", -1),
            ),
            args.getLong("position", -1),
            args.getLong("revision", -1),
        )
    }
}
