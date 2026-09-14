package app.openstory.common.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteEntryIdTest {
    @Test
    fun validRouteEntryIdSucceeds() {
        val id = RouteEntryId.from("valid_route-123")
        assertEquals("valid_route-123", id.value)
    }

    @Test
    fun blankRouteEntryIdFails() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteEntryId.from("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RouteEntryId.from("   ")
        }
    }

    @Test
    fun oversizedRouteEntryIdFails() {
        val longString = "a".repeat(RouteEntryId.MAX_LENGTH + 1)
        assertThrows(IllegalArgumentException::class.java) {
            RouteEntryId.from(longString)
        }
    }

    @Test
    fun maxLengthRouteEntryIdSucceeds() {
        val maxString = "a".repeat(RouteEntryId.MAX_LENGTH)
        val id = RouteEntryId.from(maxString)
        assertEquals(maxString, id.value)
    }

    @Test
    fun unsafeCharactersFail() {
        listOf("hello world", "story/123", "story:abc", "route@home", "id#1").forEach { unsafe ->
            assertThrows(IllegalArgumentException::class.java) {
                RouteEntryId.from(unsafe)
            }
        }
    }
}
