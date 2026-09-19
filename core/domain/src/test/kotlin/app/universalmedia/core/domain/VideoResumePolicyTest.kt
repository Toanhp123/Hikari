package app.universalmedia.core.domain

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.SourceBindingId
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoResumePolicyTest {
    private val target = ConsumptionTargetRef.MediaTarget(MediaId.generate())
    private val context =
        VideoResumeContext(SourceBindingId.generate(), AssetId.generate(), AssetRevision(1))
    private val state =
        VideoProgressState(
            target,
            VideoResumeAnchor(4200, 9000),
            CompletionState.COMPLETED,
            context,
            7,
            1,
        )

    @Test fun sameAssetRevisionRestoresExactAnchorEvenWithChangedBinding() {
        assertEquals(
            VideoResumeDecision.Exact(VideoResumeAnchor(4200, 9000)),
            selectVideoResume(target, state, context.copy(bindingId = SourceBindingId.generate())),
        )
    }

    @Test fun missingOrChangedContextStartsFromBeginningWithoutMutatingProgress() {
        for (candidate in listOf(
            null,
            context.copy(assetRevision = AssetRevision(2)),
            context.copy(assetId = AssetId.generate()),
        )) {
            assertEquals(
                VideoResumeDecision.StartFromBeginning,
                selectVideoResume(target, state, candidate),
            )
        }
        assertEquals(4200L, state.anchor?.positionMs)
        assertEquals(CompletionState.COMPLETED, state.completion)
    }

    @Test fun missingAnchorOrDifferentTargetCannotResume() {
        assertEquals(
            VideoResumeDecision.StartFromBeginning,
            selectVideoResume(target, null, context),
        )
        assertEquals(
            VideoResumeDecision.StartFromBeginning,
            selectVideoResume(target, state.copy(anchor = null, context = null), context),
        )
        assertEquals(
            VideoResumeDecision.StartFromBeginning,
            selectVideoResume(ConsumptionTargetRef.MediaTarget(MediaId.generate()), state, context),
        )
    }
}
