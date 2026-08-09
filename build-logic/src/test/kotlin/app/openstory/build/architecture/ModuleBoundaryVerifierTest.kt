package app.openstory.build.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleBoundaryVerifierTest {
    @Test
    fun databasePolicyRejectsPluginInstallerInternals() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        val actual = policy.modules.mapValues { (module, rule) ->
            ActualModule(
                path = rule.path,
                platform = rule.platform,
                productionDependencies = rule.productionDependencies,
                testDependencies = rule.testDependencies,
                unknownProjectDependencyConfigurations = emptyMap(),
                productionImports = if (module == ":core:database") {
                    setOf("app.openstory.plugin.host.install.StagedPluginPackage")
                } else {
                    emptySet()
                },
            )
        }

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.platform_import_denied" &&
                    it.module == ":core:database" &&
                    it.detail ==
                    "app.openstory.plugin.host.install.StagedPluginPackage"
            },
        )
    }

    @Test
    fun missingIncludedModuleFailsClosed() {
        val policy = policyOf(
            ":core:common" to rule("core/common"),
        )
        val actual = mapOf(
            ":core:common" to actual("core/common"),
            ":core:new" to actual("core/new"),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.missing_module" &&
                    it.module == ":core:new"
            },
        )
    }

    @Test
    fun stalePolicyModuleFailsClosed() {
        val policy = policyOf(
            ":core:common" to rule("core/common"),
            ":core:removed" to rule("core/removed"),
        )
        val actual = mapOf(
            ":core:common" to actual("core/common"),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.stale_module" &&
                    it.module == ":core:removed"
            },
        )
    }

    @Test
    fun deniedProductionDependencyIsReported() {
        val policy = policyOf(
            ":core:common" to rule("core/common"),
            ":core:plugin-host" to rule(
                path = "core/plugin-host",
                productionDependencies = setOf(":core:common"),
            ),
            ":core:network" to rule("core/network"),
        )
        val actual = mapOf(
            ":core:common" to actual("core/common"),
            ":core:network" to actual("core/network"),
            ":core:plugin-host" to actual("core/plugin-host").copy(
                productionDependencies = setOf(
                    ":core:common",
                    ":core:network",
                ),
            ),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertEquals(
            listOf(
                ArchitectureViolation(
                    code = "module_policy.production_dependency_denied",
                    module = ":core:plugin-host",
                    detail = ":core:network",
                ),
            ),
            violations.filter {
                it.code == "module_policy.production_dependency_denied"
            },
        )
    }

    @Test
    fun testOnlyDependencyCannotLeakIntoProduction() {
        val policy = policyOf(
            ":core:plugin-api" to rule(
                path = "core/plugin-api",
                testDependencies = setOf(":test:fixtures"),
            ),
            ":test:fixtures" to rule("test/fixtures"),
        )
        val actual = mapOf(
            ":core:plugin-api" to actual("core/plugin-api").copy(
                productionDependencies = setOf(":test:fixtures"),
            ),
            ":test:fixtures" to actual("test/fixtures"),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.production_dependency_denied" &&
                    it.module == ":core:plugin-api" &&
                    it.detail == ":test:fixtures"
            },
        )
    }

    @Test
    fun staleProductionDependencyAllowanceIsReported() {
        val policy = policyOf(
            ":core:common" to rule("core/common"),
            ":core:network" to rule(
                path = "core/network",
                productionDependencies = setOf(":core:common"),
            ),
        )
        val actual = mapOf(
            ":core:common" to actual("core/common"),
            ":core:network" to actual("core/network"),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code ==
                    "module_policy.production_dependency_allowance_stale" &&
                    it.module == ":core:network" &&
                    it.detail == ":core:common"
            },
        )
    }

    @Test
    fun allowlistModeAllowsUnusedApprovedEdge() {
        val policy = policyOf(
            ":a" to rule("a"),
            ":b" to rule("b"),
            ":consumer" to rule(
                path = "consumer",
                productionDependencies = setOf(":a", ":b"),
                dependencyMode = DependencyMode.ALLOWLIST,
            ),
        )
        val actual = mapOf(
            ":a" to actual("a"),
            ":b" to actual("b"),
            ":consumer" to actual("consumer").copy(
                productionDependencies = setOf(":a"),
            ),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(violations.isEmpty())
    }

    @Test
    fun allowlistModeStillRejectsUnapprovedEdge() {
        val policy = policyOf(
            ":a" to rule("a"),
            ":b" to rule("b"),
            ":c" to rule("c"),
            ":consumer" to rule(
                path = "consumer",
                productionDependencies = setOf(":a", ":b"),
                dependencyMode = DependencyMode.ALLOWLIST,
            ),
        )
        val actual = mapOf(
            ":a" to actual("a"),
            ":b" to actual("b"),
            ":c" to actual("c"),
            ":consumer" to actual("consumer").copy(
                productionDependencies = setOf(":c"),
            ),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(violations.any { it.code == "module_policy.production_dependency_denied" })
    }

    @Test
    fun exactModeStillReportsStaleAllowance() {
        val policy = policyOf(
            ":a" to rule("a"),
            ":b" to rule("b"),
            ":consumer" to rule(
                path = "consumer",
                productionDependencies = setOf(":a", ":b"),
                dependencyMode = DependencyMode.EXACT,
            ),
        )
        val actual = mapOf(
            ":a" to actual("a"),
            ":b" to actual("b"),
            ":consumer" to actual("consumer").copy(
                productionDependencies = setOf(":a"),
            ),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(violations.any { it.code == "module_policy.production_dependency_allowance_stale" })
    }

    @Test
    fun staleTestDependencyAllowanceIsReported() {
        val policy = policyOf(
            ":core:plugin-api" to rule(
                path = "core/plugin-api",
                testDependencies = setOf(":test:fixtures"),
            ),
            ":test:fixtures" to rule("test/fixtures"),
        )
        val actual = mapOf(
            ":core:plugin-api" to actual("core/plugin-api"),
            ":test:fixtures" to actual("test/fixtures"),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.test_dependency_allowance_stale" &&
                    it.module == ":core:plugin-api" &&
                    it.detail == ":test:fixtures"
            },
        )
    }

    @Test
    fun platformMismatchIsReported() {
        val policy = policyOf(
            ":core:model" to rule("core/model"),
        )
        val actual = mapOf(
            ":core:model" to actual("core/model").copy(
                platform = ModulePlatform.ANDROID_LIBRARY,
            ),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.platform_mismatch" &&
                    it.module == ":core:model"
            },
        )
    }

    @Test
    fun forbiddenProductionImportIsReported() {
        val policy = policyOf(
            ":core:model" to rule(
                path = "core/model",
                forbiddenProductionImports = setOf("android.", "androidx.compose."),
            ),
        )
        val actual = mapOf(
            ":core:model" to actual("core/model").copy(
                productionImports = setOf("android.content.Context"),
            ),
        )

        val violations = ModuleBoundaryVerifier.verify(policy, actual)

        assertTrue(
            violations.any {
                it.code == "module_policy.platform_import_denied" &&
                    it.module == ":core:model" &&
                    it.detail == "android.content.Context"
            },
        )
    }

    private fun policyOf(
        vararg modules: Pair<String, ModuleBoundaryRule>,
    ): ModuleBoundaryPolicy = ModuleBoundaryPolicy(
        schemaVersion = 1,
        modules = linkedMapOf(*modules),
    )

    private fun rule(
        path: String,
        productionDependencies: Set<String> = emptySet(),
        testDependencies: Set<String> = emptySet(),
        forbiddenProductionImports: Set<String> = emptySet(),
        dependencyMode: DependencyMode = DependencyMode.EXACT,
    ): ModuleBoundaryRule = ModuleBoundaryRule(
        path = path,
        platform = ModulePlatform.JVM,
        dependencyMode = dependencyMode,
        productionDependencies = productionDependencies,
        testDependencies = testDependencies,
        forbiddenProductionImports = forbiddenProductionImports,
    )

    private fun actual(
        path: String,
    ): ActualModule = ActualModule(
        path = path,
        platform = ModulePlatform.JVM,
        productionDependencies = emptySet(),
        testDependencies = emptySet(),
        unknownProjectDependencyConfigurations = emptyMap(),
        productionImports = emptySet(),
    )
}
