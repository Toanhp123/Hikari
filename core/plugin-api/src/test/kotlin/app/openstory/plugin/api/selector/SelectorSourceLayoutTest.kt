package app.openstory.plugin.api.selector

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectorSourceLayoutTest {
    @Test
    fun selectorSourceUsesCanonicalGenerationFreeLayout() {
        val selectorRoot = findRepositoryRoot().resolve(
            "core/plugin-api/src/main/kotlin/app/openstory/plugin/api/selector",
        )
        val forbidden = selectorRoot.walkTopDown()
            .filter(File::isFile)
            .map(File::getName)
            .filter { name ->
                Regex("(?i)(v1|v2|legacy|compat)").containsMatchIn(name)
            }
            .toList()
        val rootFiles = selectorRoot.listFiles(File::isFile)
            .orEmpty()
            .map(File::getName)
            .sorted()
        val rootDirectories = selectorRoot.listFiles(File::isDirectory)
            .orEmpty()
            .map(File::getName)
            .sorted()

        assertEquals(emptyList(), forbidden)
        assertEquals(
            listOf(
                "SelectorBinding.kt",
                "SelectorDefinition.kt",
                "SelectorRequest.kt",
                "SelectorValidation.kt",
            ),
            rootFiles,
        )
        assertEquals(
            listOf("catalog", "content", "validation"),
            rootDirectories,
        )
    }

    private fun findRepositoryRoot(): File {
        var current = File(
            requireNotNull(System.getProperty("user.dir")),
        ).absoluteFile

        while (!current.resolve("settings.gradle.kts").isFile) {
            current = requireNotNull(current.parentFile) {
                "Unable to locate repository root"
            }
        }
        return current
    }
}
