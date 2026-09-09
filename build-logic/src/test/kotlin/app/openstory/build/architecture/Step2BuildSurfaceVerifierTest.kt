package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Step2BuildSurfaceVerifierTest {
    @Test
    fun exactStep2SurfaceIsAccepted() = withFixture { fixture ->
        assertEquals(emptyList(), fixture.verify())
    }

    @Test
    fun unapprovedProjectEdgesAreRejected() = withFixture { fixture ->
        fixture.policy = fixture.policy.copy(
            modules = fixture.policy.modules + mapOf(
                ":app" to fixture.policy.modules.getValue(":app").copy(
                    productionDependencies = setOf(":catalog:runtime"),
                ),
                ":feature:catalog" to fixture.policy.modules
                    .getValue(":feature:catalog")
                    .copy(productionDependencies = setOf(":catalog:model")),
                ":catalog:runtime" to fixture.policy.modules
                    .getValue(":catalog:runtime")
                    .copy(productionDependencies = setOf(":catalog:engine")),
            ),
        )

        val details = fixture.verify().map(ArchitectureViolation::detail)

        assertTrue(":app expected=:feature:catalog actual=:catalog:runtime" in details)
        assertTrue(
            ":feature:catalog expected=:catalog:domain,:catalog:runtime " +
                "actual=:catalog:model" in details,
        )
        assertTrue(
            ":catalog:runtime expected=:catalog:domain,:catalog:storage " +
                "actual=:catalog:engine" in details,
        )
    }

    @Test
    fun frameworkDependenciesAreConfinedToTheirApprovedOwners() = withFixture { fixture ->
        fixture.write("catalog/runtime/build.gradle.kts", "implementation(libs.androidx.room.runtime)")
        fixture.write("catalog/storage/build.gradle.kts", "implementation(libs.coil.compose)")
        fixture.write("feature/catalog/build.gradle.kts", "implementation(libs.okhttp)")

        val violations = fixture.verify()

        assertViolation(violations, "step2_surface.room_owner", ":catalog:runtime")
        assertViolation(violations, "step2_surface.coil_owner", ":catalog:storage")
        assertViolation(violations, "step2_surface.http_dependency", ":feature:catalog")
    }

    @Test
    fun coilNetworkAndConcreteHttpImportsAreRejected() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/build.gradle.kts",
            "implementation(libs.coil.network.okhttp)",
        )
        fixture.write(
            "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/Remote.kt",
            """
                package app.openstory.catalog.feature.assets

                import java.net.HttpURLConnection
                import okhttp3.OkHttpClient
            """.trimIndent(),
        )

        val violations = fixture.verify()

        assertViolation(violations, "step2_surface.coil_network_forbidden", ":feature:catalog")
        assertViolation(violations, "step2_surface.http_import", ":feature:catalog")
    }

    @Test
    fun coilImportsOutsideFeatureAssetsAreRejected() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/discover/Card.kt",
            """
                package app.openstory.catalog.feature.discover

                import coil.compose.AsyncImage
            """.trimIndent(),
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.coil_import_owner",
            ":feature:catalog",
        )
    }

    @Test
    fun releaseSeedAndPluginHarnessSourcesAreRejected() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/SeedCatalogSource.kt",
            "package app.openstory.catalog.feature",
        )
        fixture.write(
            "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/PluginHarness.kt",
            "package app.openstory.catalog.feature",
        )

        val violations = fixture.verify()

        assertViolation(violations, "step2_surface.release_fixture", ":feature:catalog")
        assertTrue(violations.count { it.code == "step2_surface.release_fixture" } == 2)
    }

    @Test
    fun bundledSeedDataUnderMainIsRejected() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/main/resources/catalog_seed.json",
            "{}",
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.release_fixture",
            ":feature:catalog",
        )
    }

    @Test
    fun appMayImportOnlyTheCatalogEntryPoint() = withFixture { fixture ->
        fixture.write(
            "app/src/main/kotlin/app/openstory/BadImport.kt",
            """
                package app.openstory

                import app.openstory.catalog.feature.discover.DiscoverScreen
            """.trimIndent(),
        )

        assertViolation(fixture.verify(), "step2_surface.app_catalog_import", ":app")
    }

    private fun assertViolation(
        violations: List<ArchitectureViolation>,
        code: String,
        module: String,
    ) {
        assertTrue(
            violations.any { it.code == code && it.module == module },
            "Expected $code for $module, got $violations",
        )
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val root = createTempDirectory("step2-surface").toFile()
        try {
            val fixture = Fixture(root)
            fixture.createAcceptedSurface()
            block(fixture)
        } finally {
            root.deleteRecursively()
        }
    }

    private class Fixture(private val root: File) {
        var policy: ModuleBoundaryPolicy = acceptedPolicy()

        fun createAcceptedSurface() {
            policy.modules.values.forEach { rule ->
                write("${rule.path}/build.gradle.kts", "plugins {}")
            }
            write(
                "app/src/main/kotlin/app/openstory/CatalogUse.kt",
                """
                    package app.openstory

                    import app.openstory.catalog.feature.CatalogEntryPoint
                """.trimIndent(),
            )
        }

        fun write(relativePath: String, text: String) {
            File(root, relativePath).apply {
                parentFile.mkdirs()
                writeText(text)
            }
        }

        fun verify(): List<ArchitectureViolation> =
            Step2BuildSurfaceVerifier.verify(root, policy)

        companion object {
            private fun acceptedPolicy(): ModuleBoundaryPolicy {
                fun rule(
                    path: String,
                    platform: ModulePlatform,
                    production: Set<String> = emptySet(),
                    test: Set<String> = emptySet(),
                ) = ModuleBoundaryRule(
                    path = path,
                    platform = platform,
                    dependencyMode = DependencyMode.EXACT,
                    productionDependencies = production,
                    testDependencies = test,
                    forbiddenProductionImports = emptySet(),
                )

                return ModuleBoundaryPolicy(
                    schemaVersion = 2,
                    modules = linkedMapOf(
                        ":app" to rule(
                            "app",
                            ModulePlatform.ANDROID_APPLICATION,
                            production = setOf(":feature:catalog"),
                            test = setOf(":benchmark"),
                        ),
                        ":core:common" to rule("core/common", ModulePlatform.JVM),
                        ":catalog:model" to rule(
                            "catalog/model",
                            ModulePlatform.JVM,
                            setOf(":core:common"),
                        ),
                        ":catalog:engine" to rule(
                            "catalog/engine",
                            ModulePlatform.JVM,
                            setOf(":catalog:model", ":core:common"),
                        ),
                        ":reader:engine" to rule(
                            "reader/engine",
                            ModulePlatform.JVM,
                            setOf(":core:common"),
                        ),
                        ":plugins:api" to rule("plugins/api", ModulePlatform.JVM),
                        ":benchmark" to rule(
                            "benchmark",
                            ModulePlatform.ANDROID_TEST,
                            test = setOf(":app"),
                        ),
                        ":catalog:domain" to rule(
                            "catalog/domain",
                            ModulePlatform.JVM,
                            setOf(":core:common"),
                        ),
                        ":catalog:storage" to rule(
                            "catalog/storage",
                            ModulePlatform.ANDROID_LIBRARY,
                            setOf(":catalog:domain", ":core:common"),
                        ),
                        ":catalog:runtime" to rule(
                            "catalog/runtime",
                            ModulePlatform.ANDROID_LIBRARY,
                            setOf(":catalog:domain", ":catalog:storage"),
                        ),
                        ":feature:catalog" to rule(
                            "feature/catalog",
                            ModulePlatform.ANDROID_LIBRARY,
                            setOf(":catalog:domain", ":catalog:runtime"),
                        ),
                    ),
                )
            }
        }
    }
}
