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

        assertTrue(
            ":app expected=:core:designsystem,:feature:catalog actual=:catalog:runtime" in details,
        )
        assertTrue(
            ":feature:catalog expected=:catalog:domain,:catalog:runtime,:core:designsystem " +
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
    fun productionRemoteTransportImplementationIsRejectedWithoutRelyingOnClientImports() =
        withFixture { fixture ->
            fixture.write(
                "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/" +
                    "HiddenNetworkTransport.kt",
                """
                    package app.openstory.catalog.feature.assets

                    internal class HiddenNetworkTransport : RemoteCoverTransport
                """.trimIndent(),
            )

            assertViolation(
                fixture.verify(),
                "step2_surface.remote_transport_implementation_forbidden",
                ":feature:catalog",
            )
        }

    @Test
    fun remoteTransportConsumersAreNotMistakenForImplementations() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/assets/Consumer.kt",
            """
                package app.openstory.catalog.feature.assets

                internal class Consumer(
                    private val transport: RemoteCoverTransport?,
                )
            """.trimIndent(),
        )

        assertEquals(emptyList(), fixture.verify())
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
    fun missingVariantBindingContractIsRejected() = withFixture { fixture ->
        fixture.delete(
            "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.variant_binding_missing",
            ":feature:catalog",
        )
    }

    @Test
    fun missingBenchmarkScenarioOwnerIsRejected() {
        listOf(
            "BenchmarkRetentionPreparation.kt",
            "BenchmarkCoverPreparation.kt",
            "BenchmarkPinPruneSource.kt",
        ).forEach { fileName ->
            withFixture { fixture ->
                fixture.delete(
                    "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/$fileName",
                )

                assertViolation(
                    fixture.verify(),
                    "step2_surface.variant_binding_missing",
                    ":feature:catalog",
                )
            }
        }
    }

    @Test
    fun nonMinifiedReleaseMustReuseBenchmarkSourceDirectories() = withFixture { fixture ->
        fixture.write("feature/catalog/build.gradle.kts", "plugins {}")

        val violations = fixture.verify()

        assertViolation(
            violations,
            "step2_surface.benchmark_source_mapping",
            ":feature:catalog",
        )
    }

    @Test
    fun nonMinifiedReleaseRuntimeMustReuseBenchmarkSourceDirectories() = withFixture { fixture ->
        fixture.write("catalog/runtime/build.gradle.kts", "plugins {}")

        val violations = fixture.verify()

        assertViolation(
            violations,
            "step2_surface.benchmark_source_mapping",
            ":catalog:runtime",
        )
    }

    @Test
    fun duplicateNonMinifiedFixtureImplementationIsRejected() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/nonMinifiedRelease/kotlin/app/openstory/catalog/feature/" +
                "VariantCatalogBinding.kt",
            "package app.openstory.catalog.feature",
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.non_minified_fixture_duplicate",
            ":feature:catalog",
        )
    }

    @Test
    fun releaseBindingMustBeSourceFree() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
            """
                package app.openstory.catalog.feature

                internal object VariantCatalogBinding : CatalogVariantBinding {
                    override val binding = BenchmarkCatalogFixture.binding
                }
            """.trimIndent(),
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.release_fixture",
            ":feature:catalog",
        )
    }

    @Test
    fun benchmarkDiagnosticsUnderFeatureReleaseAreRejected() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/fixture/" +
                "BenchmarkCatalogDiagnostics.kt",
            "package app.openstory.catalog.feature.fixture\nobject BenchmarkCatalogDiagnostics",
        )

        assertViolation(fixture.verify(), "step2_surface.release_fixture", ":feature:catalog")
    }

    @Test
    fun agedBenchmarkFixtureUnderRuntimeReleaseIsRejected() = withFixture { fixture ->
        fixture.write(
            "catalog/runtime/src/release/kotlin/app/openstory/catalog/runtime/fixture/" +
                "BenchmarkAgedCatalogFixture.kt",
            "package app.openstory.catalog.runtime.fixture\nobject BenchmarkAgedCatalogFixture",
        )

        assertViolation(fixture.verify(), "step2_surface.release_fixture", ":catalog:runtime")
    }

    @Test
    fun missingFixtureCoverIsRejected() = withFixture { fixture ->
        fixture.delete(
            "feature/catalog/src/benchmarkRelease/res/drawable-nodpi/" +
                "catalog_benchmark_manga_a.webp",
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.fixture_asset_missing",
            ":feature:catalog",
        )
    }

    @Test
    fun fixtureCoverMustBeCompressedWebp() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/debug/res/drawable-nodpi/catalog_debug_manga_a.webp",
            "not a webp",
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.fixture_asset_invalid",
            ":feature:catalog",
        )
    }

    @Test
    fun concreteVariantBindingMustImplementTheSharedContract() = withFixture { fixture ->
        fixture.write(
            "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
            "package app.openstory.catalog.feature\ninternal class VariantCatalogBinding",
        )

        assertViolation(
            fixture.verify(),
            "step2_surface.variant_binding_contract",
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

    @Test
    fun appMayImportOnlyTheRootDesignSystemTheme() = withFixture { fixture ->
        fixture.write(
            "app/src/main/kotlin/app/openstory/BadDesignSystemImport.kt",
            """
                package app.openstory

                import app.openstory.designsystem.control.HikariSegmentedControl
            """.trimIndent(),
        )

        assertViolation(fixture.verify(), "step2_surface.app_designsystem_import", ":app")
    }

    @Test
    fun designSystemRejectsFrameworkAndWorkOwnerDependencies() = withFixture { fixture ->
        fixture.policy = fixture.policy.copy(
            modules = fixture.policy.modules + mapOf(
                ":core:designsystem" to fixture.policy.modules
                    .getValue(":core:designsystem")
                    .copy(productionDependencies = setOf(":core:common")),
            ),
        )
        fixture.write(
            "core/designsystem/build.gradle.kts",
            """
                implementation(project(":core:common"))
                implementation(libs.androidx.room.runtime)
                implementation(libs.coil.compose)
                implementation(libs.okhttp)
                implementation(libs.androidx.work.runtime)
                implementation(libs.androidx.javascriptengine)
                implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
            """.trimIndent(),
        )

        val violations = fixture.verify()

        assertViolation(violations, "step2_surface.production_graph", ":core:designsystem")
        assertViolation(violations, "step2_surface.room_owner", ":core:designsystem")
        assertViolation(violations, "step2_surface.coil_owner", ":core:designsystem")
        assertViolation(violations, "step2_surface.http_dependency", ":core:designsystem")
        assertViolation(violations, "step2_surface.designsystem_dependency", ":core:designsystem")
    }

    @Test
    fun designSystemRejectsCatalogRuntimeAndPlatformWorkImports() = withFixture { fixture ->
        fixture.write(
            "core/designsystem/src/main/kotlin/app/openstory/designsystem/BadOwner.kt",
            """
                package app.openstory.designsystem

                import androidx.room.Room
                import androidx.work.WorkManager
                import coil.ImageLoader
                import okhttp3.OkHttpClient
                import app.openstory.catalog.runtime.CatalogRuntime
                import app.openstory.plugins.api.Plugin
            """.trimIndent(),
        )

        assertViolation(fixture.verify(), "step2_surface.designsystem_import", ":core:designsystem")
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
                "feature/catalog/build.gradle.kts",
                """
                    plugins {}
                    androidComponents {
                        finalizeDsl { extension ->
                            listOf("benchmarkRelease", "nonMinifiedRelease").forEach { sourceSetName ->
                                extension.sourceSets.getByName(sourceSetName).apply {
                                    kotlin.directories.add("src/benchmarkRelease/kotlin")
                                    res.srcDir("src/benchmarkRelease/res")
                                    manifest.srcFile("src/benchmarkRelease/AndroidManifest.xml")
                                }
                            }
                        }
                    }
                """.trimIndent(),
            )
            write(
                "catalog/runtime/build.gradle.kts",
                """
                    plugins {}
                    androidComponents {
                        finalizeDsl { extension ->
                            listOf("benchmarkRelease", "nonMinifiedRelease").forEach { sourceSetName ->
                                extension.sourceSets.getByName(sourceSetName).apply {
                                    kotlin.directories.add("src/benchmarkRelease/kotlin")
                                }
                            }
                        }
                    }
                """.trimIndent(),
            )
            write(
                "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogVariantBinding.kt",
                "package app.openstory.catalog.feature\ninternal interface CatalogVariantBinding",
            )
            write(
                "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
                "package app.openstory.catalog.feature\n" +
                    "internal object VariantCatalogBinding : CatalogVariantBinding",
            )
            write(
                "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/seed/" +
                    "LocalSeedCatalogSource.kt",
                "package app.openstory.catalog.feature.seed\ninternal class LocalSeedCatalogSource",
            )
            listOf(
                "catalog_debug_manga_a.webp",
                "catalog_debug_manga_b.webp",
                "catalog_debug_light_novel_a.webp",
                "catalog_debug_light_novel_b.webp",
            ).forEach { name ->
                writeWebp("feature/catalog/src/debug/res/drawable-nodpi/$name")
            }
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/" +
                    "VariantCatalogBinding.kt",
                "package app.openstory.catalog.feature\n" +
                    "internal object VariantCatalogBinding : CatalogVariantBinding",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
                    "BenchmarkCatalogSource.kt",
                "package app.openstory.catalog.feature.seed\ninternal class BenchmarkCatalogSource",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
                    "BenchmarkCatalogFixture.kt",
                "package app.openstory.catalog.feature.seed\npublic object BenchmarkCatalogFixture",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
                    "BenchmarkCatalogPreparation.kt",
                "package app.openstory.catalog.feature.seed\npublic enum class BenchmarkCatalogPreparation",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
                    "BenchmarkRetentionPreparation.kt",
                "package app.openstory.catalog.feature.seed\ninternal object BenchmarkRetentionPreparation",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
                    "BenchmarkCoverPreparation.kt",
                "package app.openstory.catalog.feature.seed\ninternal object BenchmarkCoverPreparation",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/seed/" +
                    "BenchmarkPinPruneSource.kt",
                "package app.openstory.catalog.feature.seed\ninternal class BenchmarkPinPruneSource",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/fixture/" +
                    "BenchmarkCoverFixture.kt",
                "package app.openstory.catalog.feature.fixture\npublic object BenchmarkCoverFixture",
            )
            write(
                "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/fixture/" +
                    "BenchmarkCatalogDiagnostics.kt",
                "package app.openstory.catalog.feature.fixture\npublic object BenchmarkCatalogDiagnostics",
            )
            listOf(
                "catalog_benchmark_manga_a.webp",
                "catalog_benchmark_manga_b.webp",
                "catalog_benchmark_light_novel_a.webp",
                "catalog_benchmark_light_novel_b.webp",
            ).forEach { name ->
                writeWebp("feature/catalog/src/benchmarkRelease/res/drawable-nodpi/$name")
            }
            write(
                "feature/catalog/src/benchmarkRelease/AndroidManifest.xml",
                "<manifest />",
            )
            write(
                "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
                """
                    package app.openstory.catalog.feature
                    internal object VariantCatalogBinding : CatalogVariantBinding {
                        override val binding = null
                    }
                """.trimIndent(),
            )
            write(
                "app/src/main/kotlin/app/openstory/CatalogUse.kt",
                """
                    package app.openstory

                    import app.openstory.catalog.feature.CatalogEntryPoint
                    import app.openstory.designsystem.theme.HikariTheme
                """.trimIndent(),
            )
        }

        fun write(relativePath: String, text: String) {
            File(root, relativePath).apply {
                parentFile.mkdirs()
                writeText(text)
            }
        }

        fun delete(relativePath: String) {
            File(root, relativePath).delete()
        }

        private fun writeWebp(relativePath: String) {
            File(root, relativePath).apply {
                parentFile.mkdirs()
                writeBytes("RIFF0000WEBPVP8 ".encodeToByteArray())
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
                            production = setOf(":core:designsystem", ":feature:catalog"),
                            test = setOf(":benchmark"),
                        ),
                        ":core:common" to rule("core/common", ModulePlatform.JVM),
                        ":core:designsystem" to rule(
                            "core/designsystem",
                            ModulePlatform.ANDROID_LIBRARY,
                        ),
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
                            setOf(
                                ":catalog:domain",
                                ":catalog:runtime",
                                ":core:designsystem",
                            ),
                        ),
                    ),
                )
            }
        }
    }
}
