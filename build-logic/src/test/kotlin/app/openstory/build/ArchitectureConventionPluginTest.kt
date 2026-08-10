package app.openstory.build

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureConventionPluginTest {
    @Test
    fun selfProjectDependencyIsExcludedFromArchitectureSnapshot() {
        assertEquals(
            setOf(":core:common", ":catalog"),
            interModuleDependencyPaths(
                ownerPath = ":app",
                dependencyPaths = listOf(
                    ":app",
                    ":core:common",
                    ":catalog",
                    ":app",
                ),
            ),
        )
    }
}
