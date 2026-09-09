package app.openstory.build

import app.openstory.build.architecture.ModuleBoundaryPolicyLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleGraphTest {
    private val root = File("..").canonicalFile
    private val policy = ModuleBoundaryPolicyLoader.load(
        File(root, "config/architecture/module-boundaries.json"),
    )

    @Test
    fun activePolicyContainsExactlyTheStepTwoFoundationGraph() {
        assertEquals(expectedModules.keys, policy.modules.keys)

        expectedModules.forEach { (module, expected) ->
            val actual = policy.modules.getValue(module)

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
    fun settingsDeclaresExactlyTheStepTwoFoundationGraph() {
        val settings = File(root, "settings.gradle.kts").readText()
        val declaredModules = Regex("""include\("([^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(expectedModules.keys, declaredModules)
    }

    @Test
    fun appPolicyRejectsAllFoundationForbiddenImports() {
        assertEquals(
            setOf(
                "androidx.room.",
                "androidx.work.",
                "androidx.navigation",
                "androidx.lifecycle.viewmodel",
                "androidx.startup.",
                "okhttp3.",
                "coil.",
                "dagger.hilt.",
                "javax.inject.",
                "app.openstory.catalog.domain.",
                "app.openstory.catalog.runtime.",
                "app.openstory.catalog.storage.",
                "app.openstory.catalog.model.",
                "app.openstory.catalog.engine.",
                "app.openstory.library.",
                "app.openstory.chapters.",
                "app.openstory.reader.",
                "app.openstory.downloads.",
                "app.openstory.settings.",
                "app.openstory.storage.",
                "app.openstory.plugins.",
            ),
            policy.modules.getValue(":app").forbiddenProductionImports,
        )
    }

    private data class ExpectedModule(
        val path: String,
        val platform: String,
        val dependencyMode: String,
        val productionDependencies: Set<String> = emptySet(),
        val testDependencies: Set<String> = emptySet(),
    )

    private companion object {
        val expectedModules = linkedMapOf(
            ":app" to ExpectedModule(
                path = "app",
                platform = "android-application",
                dependencyMode = "exact",
                productionDependencies = setOf(":feature:catalog"),
                testDependencies = setOf(":benchmark"),
            ),
            ":core:common" to ExpectedModule(
                path = "core/common",
                platform = "jvm",
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
                productionDependencies = setOf(":catalog:domain", ":catalog:runtime"),
            ),
        )
    }
}
