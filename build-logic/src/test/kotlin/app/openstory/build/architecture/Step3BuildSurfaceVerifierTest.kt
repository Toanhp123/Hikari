package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Step3BuildSurfaceVerifierTest {
    @Test
    fun currentSourceFreeMultiAuthorityReleaseIsAcceptedThroughStepThreeDelegation() =
        withCatalogFixture { fixture ->
            assertTrue(fixture.verify().isEmpty())
        }

    @Test
    fun nonEmptyReleaseAuthorityRegistrationIsRejected() = withCatalogFixture { fixture ->
        fixture.write(
            "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
            """
                package app.openstory.catalog.feature

                internal object VariantCatalogBinding : CatalogVariantBinding {
                    override val bindings = listOf(CatalogSourceBinding())
                }
            """.trimIndent(),
        )

        assertEquals(
            setOf("step3_surface.release_authority"),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    @Test
    fun emptyListPrefixCannotHideAdditionalReleaseAuthorities() = withCatalogFixture { fixture ->
        fixture.write(
            "feature/catalog/src/release/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt",
            """
                package app.openstory.catalog.feature

                internal object VariantCatalogBinding : CatalogVariantBinding {
                    override val bindings = emptyList<CatalogSourceBinding>() + registeredBindings
                }
            """.trimIndent(),
        )

        assertEquals(
            setOf("step3_surface.release_authority"),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    @Test
    fun productionFixtureAndHarnessReferencesRemainRejected() = withCatalogFixture { fixture ->
        listOf("main", "release").forEach { sourceSet ->
            mapOf(
                "seed" to "internal val leaked = LocalSeedCatalogSource",
                "benchmark" to "internal val leaked = BenchmarkCatalogFixture",
                "plugin" to "internal val leaked = ReferencePluginHarness",
            ).forEach { (fixtureKind, declaration) ->
                val path =
                    "feature/catalog/src/$sourceSet/kotlin/app/openstory/catalog/feature/Leak.kt"
                fixture.write(path, "package app.openstory.catalog.feature\n$declaration")

                assertEquals(
                    setOf("step3_surface.release_fixture"),
                    fixture.verify().map { it.code }.toSet(),
                    "$sourceSet/$fixtureKind",
                )

                fixture.delete(path)
            }
        }
    }

    @Test
    fun liveGraphCanGrowWithoutChangingTheArchivedStepTwoGraph() = withFixture { fixture ->
        val archived = stepTwoPolicy()
        val live = archived.copy(
            modules = archived.modules +
                (":core:network" to rule("core/network", ModulePlatform.JVM)),
        )

        assertTrue(Step2BuildSurfaceVerifier.verifyGraph(archived).isEmpty())
        assertEquals(
            setOf("step2_surface.module_set"),
            Step2BuildSurfaceVerifier.verifyGraph(live).map { it.code }.toSet(),
        )
        assertTrue(Step3BuildSurfaceVerifier.verify(fixture.root, live).isEmpty())
    }

    @Test
    fun appRuntimeAndSourceImportsAreConfinedToComposition() = withFixture { fixture ->
        fixture.write(
            "app/src/main/kotlin/app/openstory/composition/AppComposition.kt",
            """
            package app.openstory.composition
            import app.openstory.catalog.runtime.CatalogRuntimeHost
            import app.openstory.sources.mangaupdates.MangaUpdatesSource
            """.trimIndent(),
        )
        assertTrue(fixture.verify().isEmpty())

        fixture.write(
            "app/src/main/kotlin/app/openstory/navigation/AppRoute.kt",
            """
            package app.openstory.navigation
            import app.openstory.catalog.runtime.CatalogRuntimeHost
            import app.openstory.sources.mangaupdates.MangaUpdatesSource
            """.trimIndent(),
        )

        assertEquals(
            setOf(
                "step3_surface.app_runtime_import_scope",
                "step3_surface.app_source_import_scope",
            ),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    @Test
    fun appStorageImportsRemainForbiddenEverywhere() = withFixture { fixture ->
        fixture.write(
            "app/src/main/kotlin/app/openstory/composition/AppComposition.kt",
            """
            package app.openstory.composition
            import app.openstory.catalog.storage.CatalogDatabase
            """.trimIndent(),
        )

        assertEquals(
            setOf("step3_surface.app_storage_import"),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    @Test
    fun navigationThreeImportsAreConfinedToAppNavigation() = withFixture { fixture ->
        fixture.write(
            "app/src/main/kotlin/app/openstory/navigation/AppRoute.kt",
            """
            package app.openstory.navigation
            import androidx.navigation3.runtime.NavKey
            """.trimIndent(),
        )
        assertTrue(fixture.verify().isEmpty())

        fixture.write(
            "app/src/main/kotlin/app/openstory/MainActivity.kt",
            """
            package app.openstory
            import androidx.navigation3.runtime.NavKey
            """.trimIndent(),
        )

        assertEquals(
            setOf("step3_surface.navigation_import_scope"),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    @Test
    fun navigationThreeIsRejectedOutsideTheAppModule() = withFixture { fixture ->
        fixture.write(
            "feature/story/build.gradle.kts",
            "implementation(libs.androidx.navigation3.runtime)",
        )
        fixture.write(
            "feature/story/src/main/kotlin/app/openstory/story/StoryRoute.kt",
            """
            package app.openstory.story
            import androidx.navigation3.runtime.NavKey
            """.trimIndent(),
        )

        assertEquals(
            setOf(
                "step3_surface.navigation_dependency_owner",
                "step3_surface.navigation_import_scope",
            ),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    @Test
    fun httpImportsAreConfinedToCoreNetwork() = withFixture { fixture ->
        fixture.write(
            "core/network/src/main/kotlin/app/openstory/network/HttpClient.kt",
            """
            package app.openstory.network
            import okhttp3.OkHttpClient
            """.trimIndent(),
        )
        assertTrue(fixture.verify().isEmpty())

        fixture.write(
            "catalog/runtime/src/main/kotlin/app/openstory/catalog/runtime/HttpClient.kt",
            """
            package app.openstory.catalog.runtime
            import okhttp3.OkHttpClient
            """.trimIndent(),
        )

        assertEquals(
            setOf("step3_surface.http_import_owner"),
            fixture.verify().map { it.code }.toSet(),
        )
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val root = createTempDirectory("step3-build-surface").toFile()
        try {
            block(Fixture(root))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun withCatalogFixture(block: (Fixture) -> Unit) {
        val root = createTempDirectory("step3-catalog-build-surface").toFile()
        try {
            block(Fixture(root, includeCatalog = true))
        } finally {
            root.deleteRecursively()
        }
    }

    private class Fixture(
        val root: File,
        includeCatalog: Boolean = false,
    ) {
        private val policy = ModuleBoundaryPolicy(
            schemaVersion = 2,
            modules = linkedMapOf(
                ":app" to rule("app", ModulePlatform.ANDROID_APPLICATION),
                ":core:network" to rule("core/network", ModulePlatform.JVM),
                ":catalog:runtime" to rule(
                    "catalog/runtime",
                    ModulePlatform.ANDROID_LIBRARY,
                ),
                ":catalog:storage" to rule(
                    "catalog/storage",
                    ModulePlatform.ANDROID_LIBRARY,
                ),
                ":sources:mangaupdates" to rule(
                    "sources/mangaupdates",
                    ModulePlatform.JVM,
                ),
                ":feature:story" to rule(
                    "feature/story",
                    ModulePlatform.ANDROID_LIBRARY,
                ),
            ).apply {
                if (includeCatalog) {
                    put(
                        ":feature:catalog",
                        rule("feature/catalog", ModulePlatform.ANDROID_LIBRARY),
                    )
                }
            },
        )

        init {
            policy.modules.values.forEach { module ->
                write("${module.path}/build.gradle.kts", "plugins {}")
            }
            if (includeCatalog) createAcceptedCatalogSurface()
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

        fun verify(): List<ArchitectureViolation> =
            Step3BuildSurfaceVerifier.verify(root, policy)

        private fun createAcceptedCatalogSurface() {
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
            writeVariantBinding("debug", "internal object VariantCatalogBinding : CatalogVariantBinding")
            write(
                "feature/catalog/src/debug/kotlin/app/openstory/catalog/feature/seed/" +
                    "LocalSeedCatalogSource.kt",
                "package app.openstory.catalog.feature.seed\ninternal class LocalSeedCatalogSource",
            )
            DEBUG_COVERS.forEach { name ->
                writeWebp("feature/catalog/src/debug/res/drawable-nodpi/$name")
            }
            writeVariantBinding(
                "benchmarkRelease",
                "internal object VariantCatalogBinding : CatalogVariantBinding",
            )
            BENCHMARK_SOURCES.forEach { (relativePath, declaration) ->
                write(
                    "feature/catalog/src/benchmarkRelease/kotlin/app/openstory/catalog/feature/" +
                        relativePath,
                    "package app.openstory.catalog.feature\n$declaration",
                )
            }
            BENCHMARK_COVERS.forEach { name ->
                writeWebp("feature/catalog/src/benchmarkRelease/res/drawable-nodpi/$name")
            }
            write("feature/catalog/src/benchmarkRelease/AndroidManifest.xml", "<manifest />")
            writeVariantBinding(
                "release",
                """
                    internal object VariantCatalogBinding : CatalogVariantBinding {
                        override val bindings = emptyList<CatalogSourceBinding>()
                    }
                """.trimIndent(),
            )
        }

        private fun writeVariantBinding(sourceSet: String, declaration: String) {
            write(
                "feature/catalog/src/$sourceSet/kotlin/app/openstory/catalog/feature/" +
                    "VariantCatalogBinding.kt",
                "package app.openstory.catalog.feature\n$declaration",
            )
        }

        private fun writeWebp(relativePath: String) {
            File(root, relativePath).apply {
                parentFile.mkdirs()
                writeBytes("RIFF0000WEBPVP8 ".encodeToByteArray())
            }
        }
    }

    private companion object {
        val DEBUG_COVERS = listOf(
            "catalog_debug_manga_a.webp",
            "catalog_debug_manga_b.webp",
            "catalog_debug_light_novel_a.webp",
            "catalog_debug_light_novel_b.webp",
        )
        val BENCHMARK_COVERS = listOf(
            "catalog_benchmark_manga_a.webp",
            "catalog_benchmark_manga_b.webp",
            "catalog_benchmark_light_novel_a.webp",
            "catalog_benchmark_light_novel_b.webp",
        )
        val BENCHMARK_SOURCES = mapOf(
            "seed/BenchmarkCatalogSource.kt" to "internal class BenchmarkCatalogSource",
            "seed/BenchmarkCatalogFixture.kt" to "public object BenchmarkCatalogFixture",
            "seed/BenchmarkCatalogPreparation.kt" to "public enum class BenchmarkCatalogPreparation",
            "seed/BenchmarkRetentionPreparation.kt" to "internal object BenchmarkRetentionPreparation",
            "seed/BenchmarkCoverPreparation.kt" to "internal object BenchmarkCoverPreparation",
            "seed/BenchmarkPinPruneSource.kt" to "internal class BenchmarkPinPruneSource",
            "fixture/BenchmarkCoverFixture.kt" to "public object BenchmarkCoverFixture",
            "fixture/BenchmarkCatalogDiagnostics.kt" to "public object BenchmarkCatalogDiagnostics",
        )

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

        fun stepTwoPolicy(): ModuleBoundaryPolicy = ModuleBoundaryPolicy(
            schemaVersion = 2,
            modules = linkedMapOf(
                ":app" to rule(
                    "app",
                    ModulePlatform.ANDROID_APPLICATION,
                    setOf(":core:designsystem", ":feature:catalog"),
                    setOf(":benchmark"),
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
                    setOf(":plugins:api"),
                ),
            ),
        )
    }
}
