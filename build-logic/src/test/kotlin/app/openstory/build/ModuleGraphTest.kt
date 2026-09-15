package app.openstory.build

import app.openstory.build.architecture.ModuleBoundaryPolicyLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleGraphTest {
    private val root = File("..").canonicalFile
    private val policy = ModuleBoundaryPolicyLoader.load(
        File(root, "config/architecture/module-boundaries.json"),
    )
    private val archivedStepTwoPolicy = ModuleBoundaryPolicyLoader.load(
        File(root, "config/architecture/history/step2-module-boundaries.json"),
    )

    @Test
    fun archivedPolicyContainsExactlyTheAcceptedStepTwoFoundationGraph() {
        assertEquals(expectedStepTwoModules.keys, archivedStepTwoPolicy.modules.keys)

        expectedStepTwoModules.forEach { (module, expected) ->
            val actual = archivedStepTwoPolicy.modules.getValue(module)

            assertEquals(expected.path, actual.path, "$module path")
            assertEquals(expected.platform, actual.platform.policyValue, "$module platform")
            assertEquals(
                expected.dependencyMode,
                actual.dependencyMode.policyValue,
                "$module dependency mode",
            )
            assertEquals(
                expected.productionDependencies,
                actual.productionDependencies,
                "$module production dependencies",
            )
            assertEquals(
                expected.testDependencies,
                actual.testDependencies,
                "$module test dependencies",
            )
        }
    }

    @Test
    fun settingsAndLivePolicyDeclareTheSameCurrentGraph() {
        val settings = File(root, "settings.gradle.kts").readText()
        val declaredModules = Regex("""include\("([^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(policy.modules.keys, declaredModules)
    }

    @Test
    fun livePolicyContainsTheTaskNineStoryLibraryGraph() {
        expectedTaskEightLibraryModules.forEach { (module, expected) ->
            val actual = policy.modules.getValue(module)

            assertEquals(expected.path, actual.path, "$module path")
            assertEquals(expected.platform, actual.platform.policyValue, "$module platform")
            assertEquals(expected.dependencyMode, actual.dependencyMode.policyValue, "$module dependency mode")
            assertEquals(expected.productionDependencies, actual.productionDependencies, "$module dependencies")
            assertEquals(expected.testDependencies, actual.testDependencies, "$module test dependencies")
        }
        assertEquals(
            setOf(
                ":catalog:domain",
                ":catalog:runtime",
                ":core:artwork",
                ":core:common",
                ":core:designsystem",
                ":feature:catalog",
                ":feature:library",
                ":feature:story",
                ":library:domain",
                ":library:runtime",
            ),
            policy.modules.getValue(":app").productionDependencies,
        )
        assertEquals(
            setOf(
                ":catalog:domain",
                ":catalog:runtime",
                ":core:common",
                ":core:designsystem",
                ":library:domain",
                ":library:runtime",
            ),
            policy.modules.getValue(":feature:story").productionDependencies,
        )
        assertTrue(
            "app.openstory.library.storage." in
                policy.modules.getValue(":feature:story").forbiddenProductionImports,
        )
    }

    @Test
    fun appPolicyDelegatesScopedImportsWithoutWeakeningStorageBans() {
        val forbidden = policy.modules.getValue(":app").forbiddenProductionImports

        assertTrue("app.openstory.catalog.storage." in forbidden)
        assertTrue("app.openstory.library.storage." in forbidden)
        assertTrue("app.openstory.reading.storage." in forbidden)
        assertTrue("okhttp3." in forbidden)
        assertFalse("androidx.navigation" in forbidden)
        assertFalse("app.openstory.catalog.runtime." in forbidden)
    }

    @Test
    fun designSystemPolicyRejectsFeatureAndRuntimeOwnershipImports() {
        val forbidden = policy.modules.getValue(":core:designsystem").forbiddenProductionImports

        setOf(
            "androidx.lifecycle.",
            "androidx.navigation.",
            "androidx.room.",
            "androidx.work.",
            "coil.",
            "okhttp3.",
            "java.net.",
            "kotlinx.coroutines.",
            "app.openstory.catalog.",
            "app.openstory.artwork.",
            "app.openstory.library.",
            "app.openstory.reading.",
            "app.openstory.story.",
            "app.openstory.plugins.",
        ).forEach { prefix ->
            assertTrue(prefix in forbidden, "Design System must reject $prefix imports")
        }
    }

    private data class ExpectedModule(
        val path: String,
        val platform: String,
        val dependencyMode: String,
        val productionDependencies: Set<String> = emptySet(),
        val testDependencies: Set<String> = emptySet(),
    )

    private companion object {
        val expectedStepTwoModules = linkedMapOf(
            ":app" to ExpectedModule(
                path = "app",
                platform = "android-application",
                dependencyMode = "exact",
                productionDependencies = setOf(":core:designsystem", ":feature:catalog"),
                testDependencies = setOf(":benchmark"),
            ),
            ":core:common" to ExpectedModule(
                path = "core/common",
                platform = "jvm",
                dependencyMode = "exact",
            ),
            ":core:designsystem" to ExpectedModule(
                path = "core/designsystem",
                platform = "android-library",
                dependencyMode = "exact",
            ),
            ":catalog:model" to ExpectedModule(
                path = "catalog/model",
                platform = "jvm",
                dependencyMode = "exact",
                productionDependencies = setOf(":core:common"),
            ),
            ":catalog:engine" to ExpectedModule(
                path = "catalog/engine",
                platform = "jvm",
                dependencyMode = "exact",
                productionDependencies = setOf(":core:common", ":catalog:model"),
            ),
            ":reader:engine" to ExpectedModule(
                path = "reader/engine",
                platform = "jvm",
                dependencyMode = "exact",
                productionDependencies = setOf(":core:common"),
            ),
            ":plugins:api" to ExpectedModule(
                path = "plugins/api",
                platform = "jvm",
                dependencyMode = "exact",
            ),
            ":benchmark" to ExpectedModule(
                path = "benchmark",
                platform = "android-test",
                dependencyMode = "allowlist",
                testDependencies = setOf(":app"),
            ),
            ":catalog:domain" to ExpectedModule(
                path = "catalog/domain",
                platform = "jvm",
                dependencyMode = "exact",
                productionDependencies = setOf(":core:common"),
            ),
            ":catalog:storage" to ExpectedModule(
                path = "catalog/storage",
                platform = "android-library",
                dependencyMode = "exact",
                productionDependencies = setOf(":catalog:domain", ":core:common"),
            ),
            ":catalog:runtime" to ExpectedModule(
                path = "catalog/runtime",
                platform = "android-library",
                dependencyMode = "exact",
                productionDependencies = setOf(":catalog:domain", ":catalog:storage"),
            ),
            ":feature:catalog" to ExpectedModule(
                path = "feature/catalog",
                platform = "android-library",
                dependencyMode = "exact",
                productionDependencies = setOf(
                    ":catalog:domain",
                    ":catalog:runtime",
                    ":core:designsystem",
                ),
                testDependencies = setOf(":plugins:api"),
            ),
        )
        val expectedTaskEightLibraryModules = linkedMapOf(
            ":library:domain" to ExpectedModule(
                path = "library/domain",
                platform = "jvm",
                dependencyMode = "exact",
                productionDependencies = setOf(":catalog:domain", ":core:common"),
            ),
            ":library:storage" to ExpectedModule(
                path = "library/storage",
                platform = "android-library",
                dependencyMode = "exact",
                productionDependencies = setOf(":library:domain"),
            ),
            ":library:runtime" to ExpectedModule(
                path = "library/runtime",
                platform = "android-library",
                dependencyMode = "exact",
                productionDependencies = setOf(":core:common", ":library:domain", ":library:storage"),
            ),
            ":feature:library" to ExpectedModule(
                path = "feature/library",
                platform = "android-library",
                dependencyMode = "exact",
                productionDependencies = setOf(
                    ":core:designsystem",
                    ":library:domain",
                    ":library:runtime",
                ),
            ),
        )
    }
}
