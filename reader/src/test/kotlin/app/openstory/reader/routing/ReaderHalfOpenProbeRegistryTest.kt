package app.openstory.reader.routing

import app.openstory.common.id.PluginId
import app.openstory.reader.engine.SourceOperationKey
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReaderHalfOpenProbeRegistryTest {
    @Test
    fun leaseIsExclusiveAndReleaseIsIdempotent() {
        val registry = ReaderHalfOpenProbeRegistry()
        val key = SourceOperationKey(PluginId("source"))
        val lease = assertNotNull(registry.tryAcquire(key))

        assertNull(registry.tryAcquire(key))
        lease.release()
        lease.release()
        assertNotNull(registry.tryAcquire(key)).release()
    }
}
