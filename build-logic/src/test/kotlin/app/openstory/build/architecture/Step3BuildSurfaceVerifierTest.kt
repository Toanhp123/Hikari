package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Step3BuildSurfaceVerifierTest {
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

    private class Fixture(val root: File) {
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
            ),
        )

        init {
            policy.modules.values.forEach { module ->
                write("${module.path}/build.gradle.kts", "plugins {}")
            }
        }

        fun write(relativePath: String, text: String) {
            File(root, relativePath).apply {
                parentFile.mkdirs()
                writeText(text)
            }
        }

        fun verify(): List<ArchitectureViolation> =
            Step3BuildSurfaceVerifier.verify(root, policy)
    }

    private companion object {
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
