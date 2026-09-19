package app.universalmedia

import app.universalmedia.core.domain.ProgressStore
import app.universalmedia.core.domain.VideoResumeDecision
import app.universalmedia.core.domain.selectVideoResume
import app.universalmedia.core.model.ConsumptionTargetRef.MediaTarget
import app.universalmedia.core.model.MediaId
import app.universalmedia.playback.api.PlaybackController
import app.universalmedia.playback.api.PlaybackFailure
import app.universalmedia.playback.api.PlaybackPhase
import app.universalmedia.playback.api.PlaybackProvenance
import app.universalmedia.playback.api.PlaybackRequest
import app.universalmedia.source.api.ResolutionFailure
import app.universalmedia.source.api.SourceResolution
import app.universalmedia.source.api.SourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface PlayerOpenResult {
    data object Ready : PlayerOpenResult
    data class SourceFailed(val failure: ResolutionFailure) : PlayerOpenResult
    data object ProgressFailed : PlayerOpenResult
    data object ConnectionFailed : PlayerOpenResult
}

/** App-lifetime controller; the service remains the only player owner. No runtime data is saved. */
internal class PlaybackCoordinator(
    private val progress: ProgressStore,
    private val sources: SourceResolver,
    private val connect: suspend () -> PlaybackController,
) {
    private val opening = Mutex()
    private var activeMedia: MediaId? = null
    private var leaveGeneration = 0L
    var controller: PlaybackController? = null
        private set

    fun leave() {
        leaveGeneration++
        controller?.pause()
    }

    suspend fun open(mediaId: MediaId): PlayerOpenResult {
        val generation = leaveGeneration
        return opening.withLock {
            val existing = controller
            val snapshot = existing?.state()
            if (activeMedia == mediaId && snapshot != null &&
                snapshot.phase != PlaybackPhase.FAILED
            ) {
                return@withLock PlayerOpenResult.Ready
            }

            val target = MediaTarget(mediaId)
            val saved = try {
                withContext(Dispatchers.IO) { progress.load(target) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock PlayerOpenResult.ProgressFailed
            }
            val resolved = try {
                withContext(Dispatchers.IO) { sources.resolve(target) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock PlayerOpenResult.SourceFailed(
                    ResolutionFailure.TRANSIENT_PROVIDER_FAILURE,
                )
            }
            if (resolved is SourceResolution.Failed) {
                return@withLock PlayerOpenResult.SourceFailed(resolved.failure)
            }
            resolved as SourceResolution.Ready
            if (resolved.target != target) {
                return@withLock PlayerOpenResult.SourceFailed(ResolutionFailure.UNAVAILABLE)
            }
            val resume = selectVideoResume(target, saved, resolved.provenance)
            val request = PlaybackRequest(
                target,
                resolved.content,
                PlaybackProvenance(
                    resolved.provenance.bindingId,
                    resolved.provenance.assetId,
                    resolved.provenance.assetRevision.value,
                ),
                (resume as? VideoResumeDecision.Exact)?.anchor?.positionMs ?: 0,
                saved?.stateRevision ?: 0,
            )
            try {
                if (snapshot?.failure == PlaybackFailure.DISCONNECTED) {
                    existing.close()
                    controller = null
                }
                val connection = controller ?: connect().also { controller = it }
                if (!connection.start(request)) return@withLock PlayerOpenResult.ConnectionFailed
                activeMedia = mediaId
                if (generation != leaveGeneration) connection.pause()
                PlayerOpenResult.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                controller?.close()
                controller = null
                activeMedia = null
                PlayerOpenResult.ConnectionFailed
            }
        }
    }
}
