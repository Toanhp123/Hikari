package app.universalmedia.core.model

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalIdentityTest {
    @Test
    fun generatedIdentitiesAreIndependentEvenForTheSameObservation() {
        assertNotEquals(MediaId.generate(), MediaId.generate())
        assertNotEquals(AssetId.generate(), AssetId.generate())
        assertNotEquals(RootId.generate(), RootId.generate())
        assertNotEquals(UnitId.generate(), UnitId.generate())
        assertNotEquals(SourceId.generate(), SourceId.generate())
        assertNotEquals(SourceBindingId.generate(), SourceBindingId.generate())
        assertNotEquals(ScanRunId.generate(), ScanRunId.generate())
    }

    @Test
    fun targetRoundTripUsesOnlyCanonicalIdentity() {
        val persisted = "b20dd4f1-1674-4357-a4e0-797f11746114"
        val original = ConsumptionTargetRef.MediaTarget(MediaId(UUID.fromString(persisted)))
        val restored = ConsumptionTargetRef.MediaTarget(MediaId(UUID.fromString(original.mediaId.value.toString())))
        assertEquals(original, restored)
        assertNotEquals(original, ConsumptionTargetRef.UnitTarget(UnitId(UUID.fromString(persisted))))
    }
}
