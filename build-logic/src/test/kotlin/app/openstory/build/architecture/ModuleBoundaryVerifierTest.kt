package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder

class ModuleBoundaryVerifierTest {
    @Test
    fun designSystemHasNoProjectDependencies() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        assertTrue(
            policy.modules.getValue(":core:designsystem")
                .productionDependencies
                .isEmpty(),
        )
    }

    @Test
    fun capabilityModulesDoNotDependOnDesignSystem() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        val capabilityModules = listOf(
            ":catalog",
            ":library",
            ":chapters",
            ":reader",
            ":downloads",
            ":storage:room",
            ":storage:files",
            ":plugins:api",
            ":plugins:runtime",
        )

        capabilityModules.forEach { module ->
            assertTrue(
                ":core:designsystem" !in
                    policy.modules.getValue(module).productionDependencies,
                "$module must not depend on :core:designsystem",
            )
        }
    }

    @Test
    fun designSystemDomainImportIsRejected() {
        val root = createTempDirectory("designsystem-boundary").toFile()
        try {
            val policyJson = File(root, "config/architecture/module-boundaries.json")
            policyJson.parentFile.mkdirs()
            policyJson.writeText(
                """
                {
                  "schemaVersion": 2,
                  "modules": {
                    ":core:designsystem": {
                      "path": "core/designsystem",
                      "platform": "android-library",
                      "dependencyMode": "exact",
                      "productionDependencies": [],
                      "testDependencies": [],
                      "forbiddenProductionImports": ["app.openstory.catalog."]
                    }
                  }
                }
                """.trimIndent(),
            )
            val source = File(
                root,
                "core/designsystem/src/main/kotlin/app/openstory/designsystem/Fixture.kt",
            )
            source.parentFile.mkdirs()
            source.writeText(
                """
                package app.openstory.designsystem

                import app.openstory.catalog.model.CatalogEntry
                """.trimIndent(),
            )
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val task = project.tasks.create(
                "verifyDesignSystemBoundary",
                VerifyModuleBoundariesTask::class.java,
            ).apply {
                policyFile.set(policyJson)
                moduleDirectories.set(mapOf(":core:designsystem" to "core/designsystem"))
                modulePlatforms.set(mapOf(":core:designsystem" to "android-library"))
                productionDependencies.set(mapOf(":core:designsystem" to ""))
                testDependencies.set(mapOf(":core:designsystem" to ""))
                unknownProjectDependencyConfigurations.set(mapOf(":core:designsystem" to ""))
                productionSources.from(source)
            }

            val error = assertFailsWith<GradleException> { task.verifyBoundaries() }

            assertTrue("module_policy.platform_import_denied" in error.message.orEmpty())
            assertTrue("app.openstory.catalog.model.CatalogEntry" in error.message.orEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readerPresentationMayConsumeTheDesignSystem() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        assertTrue(
            ":core:designsystem" in
                policy.modules.getValue(":feature:reader").productionDependencies,
        )
    }

    @Test
    fun catalogPresentationMayConsumeTheDesignSystem() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        assertTrue(
            ":core:designsystem" in
                policy.modules.getValue(":feature:catalog").productionDependencies,
        )
    }

    @Test
    fun catalogPresentationMayConsumeReadingProgress() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )

        assertEquals(
            setOf(
                ":core:common",
                ":core:designsystem",
                ":catalog",
                ":library",
                ":chapters",
                ":reader",
                ":downloads",
            ),
            policy.modules.getValue(":feature:catalog").productionDependencies,
        )
    }

    @Test
    fun designSystemIsAProjectIndependentAndroidUiFoundation() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )

        val designSystem = policy.modules.getValue(":core:designsystem")

        assertEquals(ModulePlatform.ANDROID_LIBRARY, designSystem.platform)
        assertTrue(designSystem.productionDependencies.isEmpty())
        assertTrue(designSystem.testDependencies.isEmpty())
        assertTrue(
            ":core:designsystem" in
                policy.modules.getValue(":app").productionDependencies,
        )
    }

    @Test
    fun currentPolicyContainsR2Boundaries() {
        val policy = ModuleBoundaryPolicyLoader.load(File("../config/architecture/module-boundaries.json"))
        assertTrue(":plugins:api" in policy.modules)
        assertTrue(":plugins:runtime" in policy.modules)
        assertTrue(":catalog" in policy.modules)
        assertEquals(
            setOf(":core:common", ":plugins:api", ":plugins:runtime"),
            policy.modules.getValue(":catalog").productionDependencies.toSet(),
        )
    }
}
