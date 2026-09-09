package app.openstory.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.openstory.build.architecture.VerifyProductionPackageStructureTask
import org.gradle.testfixtures.ProjectBuilder

class ArchitectureConventionPluginTest {
    @Test
    fun aggregateArchitectureVerificationIncludesTheAppFoundationGate() {
        val root = ProjectBuilder.builder().withName("root").build()
        val app = ProjectBuilder.builder()
            .withName("app")
            .withParent(root)
            .build()
        val foundation = app.tasks.register("verifyFoundation").get()

        root.pluginManager.apply(ArchitectureConventionPlugin::class.java)

        val aggregate = root.tasks.getByName("verifyArchitecture")
        assertTrue(
            foundation in aggregate.taskDependencies.getDependencies(aggregate),
        )
        assertTrue(
            root.tasks.getByName("verifyProductionPackageStructure") in
                aggregate.taskDependencies.getDependencies(aggregate),
        )
        assertTrue(
            root.tasks.getByName("verifyStep2BuildSurface") in
                aggregate.taskDependencies.getDependencies(aggregate),
        )
    }

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

    @Test
    fun packageStructureTaskReceivesItsRootAsAConfigurationCacheSafeInput() {
        val root = ProjectBuilder.builder().withName("root").build()

        root.pluginManager.apply(ArchitectureConventionPlugin::class.java)

        val task = root.tasks.getByName("verifyProductionPackageStructure")
            as VerifyProductionPackageStructureTask
        assertEquals(root.projectDir.canonicalFile, task.rootDirectory.get().asFile.canonicalFile)
    }
}
