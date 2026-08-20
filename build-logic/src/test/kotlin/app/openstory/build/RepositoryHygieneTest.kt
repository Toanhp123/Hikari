package app.openstory.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RepositoryHygieneTest {
    private val root = File("..").canonicalFile

    @Test
    fun ideStateIsNotPartOfRepositoryBaseline() {
        val trackedIdeState = ProcessBuilder(
            "git",
            "ls-files",
            ".idea",
        )
            .directory(root)
            .start()
            .also { process ->
                assertTrue(process.waitFor() == 0)
            }
            .inputStream
            .bufferedReader()
            .readText()

        assertTrue(trackedIdeState.isBlank())
        assertTrue(
            File(root, ".gitignore")
                .readText()
                .lineSequence()
                .any { line -> line.trim() == "/.idea/" },
        )
    }

    @Test
    fun hikariAndOpenStoryRolesRemainIntentional() {
        assertTrue(
            File(root, "settings.gradle.kts")
                .readText()
                .contains("rootProject.name = \"Hikari\""),
        )
        assertTrue(
            File(root, "app/build.gradle.kts")
                .readText()
                .contains("applicationId = \"app.openstory\""),
        )
    }

    @Test
    fun navigationAndInjectionDependenciesRemainExplicit() {
        val versionCatalog = File(root, "gradle/libs.versions.toml").readText()

        listOf(
            "androidx-lifecycle-viewmodel-navigation3",
            "androidx-hilt-lifecycle-viewmodel-compose",
            "javax-inject",
        ).forEach { alias ->
            assertTrue("$alias =" in versionCatalog, "Missing dependency alias: $alias")
        }
    }

    @Test
    fun productUiRenderingToolchainVersionsRemainPinned() {
        val catalog = File(root, "gradle/libs.versions.toml").readText()

        assertTrue(catalog.contains("coil = \"3.5.0\""))
        assertTrue(catalog.contains("backdrop = \"2.0.0\""))
        assertTrue(catalog.contains("roborazzi = \"1.70.0\""))
        assertTrue(catalog.contains("robolectric = \"4.16.1\""))
    }
}
