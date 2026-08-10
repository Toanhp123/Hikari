package app.openstory.common.id

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StableIdsTest {
    @Test
    fun storyIdRejectsWhitespace() {
        assertFailsWith<IllegalArgumentException> { StoryId("a b") }
    }

    @Test
    fun pluginIdRetainsStableValue() {
        assertEquals("org.openstory.x", PluginId("org.openstory.x").value)
    }
}
