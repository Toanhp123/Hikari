package app.openstory.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.openstory.build.architecture.ModulePlatform
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
            root.tasks.getByName("verifyStep3BuildSurface") in
                aggregate.taskDependencies.getDependencies(aggregate),
        )
        assertTrue(root.tasks.findByName("verifyStep2BuildSurface") == null)
        assertTrue(root.tasks.findByName("verifyStep3FastModules") != null)
        assertTrue(root.tasks.findByName("verifyStep3FullModules") != null)
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

    @Test
    fun stepThreeAggregateTasksCoverEachSupportedLiveModulePlatform() {
        assertEquals(
            setOf(":core:common:test"),
            step3ModuleVerificationTasks(":core:common", ModulePlatform.JVM, full = false),
        )
        assertEquals(
            setOf(":app:testDebugUnitTest", ":app:assembleDebug"),
            step3ModuleVerificationTasks(":app", ModulePlatform.ANDROID_APPLICATION, full = false),
        )
        assertEquals(
            setOf(
                ":feature:catalog:testDebugUnitTest",
                ":feature:catalog:assembleDebug",
                ":feature:catalog:assembleRelease",
                ":feature:catalog:lint",
            ),
            step3ModuleVerificationTasks(
                ":feature:catalog",
                ModulePlatform.ANDROID_LIBRARY,
                full = true,
            ),
        )
        assertTrue(
            step3ModuleVerificationTasks(
                ":benchmark",
                ModulePlatform.ANDROID_TEST,
                full = true,
            ).isEmpty(),
        )
    }

    @Test
    fun packageVerificationFollowsOnlyTheAppProductionDependencyCone() {
        assertEquals(
            setOf(":app", ":core:common", ":feature:catalog"),
            productionReachableModules(
                mapOf(
                    ":app" to setOf(":feature:catalog"),
                    ":feature:catalog" to setOf(":core:common"),
                    ":core:common" to emptySet(),
                    ":plugins:api" to emptySet(),
                    ":benchmark" to emptySet(),
                ),
            ),
        )
    }
}
