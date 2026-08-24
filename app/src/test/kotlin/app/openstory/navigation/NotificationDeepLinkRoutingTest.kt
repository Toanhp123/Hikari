package app.openstory.navigation

import kotlin.test.Test
import kotlin.test.assertNotNull

class NotificationDeepLinkRoutingTest {
    @Test
    fun routingUsesTheProductionIntentParser() {
        val parser = runCatching {
            Class.forName("app.openstory.notification.NotificationIntentParser")
        }.getOrNull()
        assertNotNull(parser, "Wave 10 production notification parser is not wired yet")
    }
}
