package app.universalmedia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PersistenceRulesTest {
    @Test
    fun checkpointOrderingUsesRevisionAndAllowsRewind() {
        assertEquals(1L, nextProgressRevision(0, 0))
        assertEquals(8L, nextProgressRevision(7, 7))
        assertEquals(null, nextProgressRevision(7, 6))
        assertEquals(null, nextProgressRevision(7, 8))
        assertThrows(IllegalArgumentException::class.java) { nextProgressRevision(0, -1) }
    }

    @Test
    fun representationChangesAdvanceRevisionButRenamingDoesNot() {
        assertEquals(4L, representationRevision(4, 100, 10, 100, 10))
        assertEquals(5L, representationRevision(4, 100, 10, 101, 10))
        assertEquals(5L, representationRevision(4, 100, 10, 100, 11))
        assertEquals(5L, representationRevision(4, 100, 10, null, null))
    }
}
