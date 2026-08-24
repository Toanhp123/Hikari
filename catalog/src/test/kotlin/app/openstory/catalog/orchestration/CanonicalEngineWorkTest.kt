package app.openstory.catalog.orchestration

import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalEngineWorkTest {
    @Test
    fun postMergeDerivedReasonRoundTripsExactRequirements() {
        val requirements = PostMergeDerivedRequirements(true, false, true)
        val reason = CanonicalEngineWorkReasons.postMergeDerived(requirements)
        assertEquals(requirements, CanonicalEngineWorkReasons.postMergeDerivedRequirements(reason))
    }

    @Test
    fun legacyOrUnknownPostMergeReasonFallsBackToConservativeFullDerivedWork() {
        val all = PostMergeDerivedRequirements(true, true, true)
        assertEquals(all, CanonicalEngineWorkReasons.postMergeDerivedRequirements("story-merge-derived-state"))
        assertEquals(all, CanonicalEngineWorkReasons.postMergeDerivedRequirements(
            "story-merge-derived:chapter-reaggregation,future-token"))
    }

    @Test
    fun coalescingPostMergeReasonsNeverDropsPreviouslyRequiredWork() {
        val existing = CanonicalEngineWorkReasons.postMergeDerived(PostMergeDerivedRequirements(true, false, false))
        val coalesced = CanonicalEngineWorkReasons.coalescePostMergeDerived(
            existing,
            PostMergeDerivedRequirements(false, true, false),
        )
        assertEquals(
            PostMergeDerivedRequirements(true, true, false),
            CanonicalEngineWorkReasons.postMergeDerivedRequirements(coalesced),
        )
    }
}
