package app.openstory.plugins.runtime.execution

import app.openstory.plugins.api.protocol.PluginOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class InvocationScriptBuilderTest {
    @Test
    fun `reuses encoded source while keeping input per invocation`() {
        var encodes = 0
        val builder = InvocationScriptBuilder(
            maxCachedSources = 4,
            sourceEncoder = { source ->
                encodes++
                JsonPrimitive(source).toString()
            },
        )

        val first = builder.build(
            "globalThis.openstoryPlugin = {};",
            PluginOperation.CATALOG_HOME,
            JsonObject(mapOf("page" to JsonPrimitive(1))),
        )
        val second = builder.build(
            "globalThis.openstoryPlugin = {};",
            PluginOperation.CATALOG_HOME,
            JsonObject(mapOf("page" to JsonPrimitive(2))),
        )

        assertEquals(1, encodes)
        assertNotEquals(first, second)
        assertTrue("\"page\":1" in first)
        assertTrue("\"page\":2" in second)
    }

    @Test
    fun `source cache is bounded`() {
        var encodes = 0
        val builder = InvocationScriptBuilder(
            maxCachedSources = 1,
            sourceEncoder = { source ->
                encodes++
                JsonPrimitive(source).toString()
            },
        )
        val input = JsonObject(emptyMap())

        builder.build("source-a", PluginOperation.CATALOG_HOME, input)
        builder.build("source-b", PluginOperation.CATALOG_HOME, input)
        builder.build("source-a", PluginOperation.CATALOG_HOME, input)

        assertEquals(3, encodes)
    }
}
