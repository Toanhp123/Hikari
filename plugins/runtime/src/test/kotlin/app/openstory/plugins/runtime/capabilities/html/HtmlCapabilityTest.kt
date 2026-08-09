package app.openstory.plugins.runtime.capabilities.html

import app.openstory.plugins.runtime.PluginCallResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlCapabilityTest {
    @Test
    fun htmlQueryCapsResultCount() {
        val html = (1..10).joinToString(prefix = "<main>", postfix = "</main>") { "<p>$it</p>" }
        val result = HtmlCapability(maxResults = 3).query(HtmlQueryRequest(html, "p", limit = 3))

        assertEquals(listOf("1", "2", "3"), assertIs<PluginCallResult.Success<HtmlQueryResponse>>(result).value.values)
    }
}
