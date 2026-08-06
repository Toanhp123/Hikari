package app.openstory.build

import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureConventionPluginTest {
    @Test
    fun selfProjectDependencyIsExcludedFromArchitectureSnapshot() {
        assertEquals(
            setOf(":core:common", ":test:fixtures"),
            interModuleDependencyPaths(
                ownerPath = ":app",
                dependencyPaths = listOf(
                    ":app",
                    ":core:common",
                    ":test:fixtures",
                    ":app",
                ),
            ),
        )
    }
}
