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
}
