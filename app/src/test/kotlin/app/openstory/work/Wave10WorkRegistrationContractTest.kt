package app.openstory.work

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Wave10WorkRegistrationContractTest {
    private val root = File("..").canonicalFile

    @Test
    fun startupOwnsPolicyAndNotificationRecoveryRegistration() {
        val application = File(
            root,
            "app/src/main/kotlin/app/openstory/OpenStoryApplication.kt",
        ).readText()
        assertTrue("backgroundPolicyCoordinator.start()" in application)
        assertTrue("notificationDrainScheduler.ensureRecoveryWork()" in application)
    }

    @Test
    fun periodicNamesAreStableAndBounded() {
        assertTrue(WorkNames.LIBRARY_CHAPTER_PERIODIC == "library-chapter-periodic")
        assertTrue(WorkNames.LIBRARY_CHAPTER_CONTINUATION == "library-chapter-continuation")
    }
}
