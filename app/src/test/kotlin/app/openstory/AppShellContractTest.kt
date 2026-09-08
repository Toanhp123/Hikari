package app.openstory

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AppShellContractTest {
    @Test
    fun applicationHasNoStartupOverride() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/HikariApplication.kt",
        ).readText()
        assertFalse("override fun onCreate" in source)
    }

    @Test
    fun mainActivityHasNoDomainOrSchedulerOwnership() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/MainActivity.kt",
        ).readText()
        listOf(
            "@AndroidEntryPoint",
            "WorkManager",
            "NotificationIntentParser",
            "Reader",
            "Catalog",
            "PluginRuntime",
            "lifecycleScope",
        ).forEach { forbidden ->
            assertFalse("Forbidden MainActivity ownership: $forbidden", forbidden in source)
        }
    }

    @Test
    fun startupGateDoesNotUseNavigationOrAnimationFramework() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt",
        ).readText()

        listOf(
            "NavDisplay",
            "NavController",
            "AnimatedContent",
            "Crossfade",
            "rememberNavBackStack",
        ).forEach { forbidden ->
            assertFalse(forbidden in source)
        }
    }

    private fun rootFile(relativePath: String): File {
        val userDirectory = checkNotNull(System.getProperty("user.dir"))
        var current = File(userDirectory).canonicalFile
        while (!File(current, "settings.gradle.kts").isFile) {
            current = current.parentFile
                ?: error("Repository root not found from $userDirectory")
        }
        return File(current, relativePath).also { file ->
            check(file.isFile) { "Required repository file is missing: ${file.path}" }
        }
    }
}
