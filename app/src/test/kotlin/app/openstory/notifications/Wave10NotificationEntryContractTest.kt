package app.openstory.notifications

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Wave10NotificationEntryContractTest {
    private val root = File("..").canonicalFile

    @Test
    fun manifestAndApplicationExposeProductionNotificationEntry() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val application = File(root, "app/src/main/kotlin/app/openstory/OpenStoryApplication.kt").readText()
        assertTrue("android.permission.POST_NOTIFICATIONS" in manifest)
        assertTrue("notificationChannels.createAll()" in application)
    }
}
