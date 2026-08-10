package app.openstory.build

import app.openstory.build.architecture.ModuleBoundaryPolicyLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleGraphTest {
    @Test
    fun settingsRejectsNonJava17Runtime() {
        val settings = File("../settings.gradle.kts").readText()

        listOf(
            "require(",
            "JavaVersion.current()",
            "JavaVersion.VERSION_17",
        ).forEach { expected ->
            assertTrue(
                expected in settings,
                "Missing Java 17 runtime guard: $expected",
            )
        }
    }

    @Test
    fun gradlePropertiesEnableFoundationBuildPolicy() {
        val properties = File("../gradle.properties")
            .readLines()
            .map(String::trim)

        listOf(
            "org.gradle.parallel=true",
            "org.gradle.configuration-cache=true",
            "kotlin.incremental=true",
        ).forEach { property ->
            assertTrue(
                property in properties,
                "Missing Gradle property $property",
            )
        }

        assertTrue(
            properties.any { it.startsWith("org.gradle.jvmargs=") },
            "Missing org.gradle.jvmargs",
        )
    }

    @Test
    fun versionCatalogUsesPinnedFoundationVersions() {
        val catalog = File("../gradle/libs.versions.toml").readText()

        mapOf(
            "agp" to "9.3.0",
            "kotlin" to "2.4.10",
            "composeBom" to "2026.06.00",
            "room" to "2.8.4",
        ).forEach { (name, version) ->
            assertTrue(
                "$name = \"$version\"" in catalog,
                "Expected $name version $version",
            )
        }
    }

    @Test
    fun settingsAndPolicyContainEveryCurrentModule() {
        val settings = File("../settings.gradle.kts").readText()
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        val expectedModules = setOf(
            ":app",
            ":core:common",
            ":catalog",
            ":feature:catalog",
            ":storage:room",
            ":plugins:api",
            ":plugins:runtime",
        )

        expectedModules.forEach { module ->
            assertTrue(
                "include(\"$module\")" in settings,
                "Missing module $module from settings.gradle.kts",
            )
        }
        assertEquals(expectedModules, policy.modules.keys)
    }

    @Test
    fun baselineTwoTargetModulesReplaceLegacyPresentationModules() {
        val settings = File("../settings.gradle.kts").readText()

        listOf(
            ":catalog",
            ":feature:catalog",
            ":storage:room",
            ":plugins:api",
            ":plugins:runtime",
        ).forEach { module ->
            assertTrue("include(\"$module\")" in settings, "Missing $module")
        }
        assertFalse("include(\":feature:home\")" in settings, "Legacy Home module must be removed")
        assertFalse("include(\":feature:story\")" in settings, "Legacy Story module must be removed")
    }

    @Test
    fun buildLogicRegistersAllConventionPlugins() {
        val buildLogic = File("build.gradle.kts").readText()

        listOf(
            "openstory.architecture",
            "openstory.android.application",
            "openstory.android.library",
            "openstory.kotlin.jvm",
            "openstory.compose",
            "openstory.hilt",
            "openstory.room",
        ).forEach { pluginId ->
            assertTrue(
                "id = \"$pluginId\"" in buildLogic,
                "Missing convention plugin $pluginId",
            )
        }
    }

    @Test
    fun modulesUseExpectedPlatformConventions() {
        val expectedPlugins = mapOf(
            "../app/build.gradle.kts" to "id(\"openstory.android.application\")",
            "../core/common/build.gradle.kts" to "id(\"openstory.kotlin.jvm\")",
            "../catalog/build.gradle.kts" to "id(\"openstory.android.library\")",
            "../feature/catalog/build.gradle.kts" to "id(\"openstory.android.library\")",
            "../storage/room/build.gradle.kts" to "id(\"openstory.android.library\")",
            "../plugins/api/build.gradle.kts" to "id(\"openstory.kotlin.jvm\")",
            "../plugins/runtime/build.gradle.kts" to "id(\"openstory.android.library\")",
        )

        expectedPlugins.forEach { (path, expectedPlugin) ->
            val buildFile = File(path)
            assertTrue(buildFile.isFile, "Missing build script $path")
            assertTrue(
                expectedPlugin in buildFile.readText(),
                "$path does not apply $expectedPlugin",
            )
        }
    }

    @Test
    fun architecturePluginIgnoresSyntheticParentProjectsWithoutBuildScripts() {
        val convention = File(
            "src/main/kotlin/app/openstory/build/ArchitectureConventionPlugin.kt",
        ).readText()

        assertTrue(
            ".filter { it.buildFile.isFile }" in convention,
            "Architecture snapshots must ignore synthetic parent projects such as :core and :test",
        )
    }

    @Test
    fun staleFixtureAndResultContractsAreAbsent() {
        val root = File("..").canonicalFile
        val gradleFiles = root.walkTopDown()
            .filter { it.isFile && it.extension == "kts" }
            .filterNot { "${File.separator}build${File.separator}" in it.path }
            .toList()
        val productionSources = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filterNot { "${File.separator}build${File.separator}" in it.path }
            .toList()

        gradleFiles.forEach { file ->
            val source = file.readText()
            assertFalse("project(\":test:fixtures\")" in source, "Legacy fixture dependency in ${file.path}")
            assertFalse("include(\":test:fixtures\")" in source, "Legacy fixture module in ${file.path}")
        }
        productionSources.forEach { file ->
            val source = file.readText()
            assertFalse("AppResult" in source, "Legacy AppResult usage in ${file.path}")
            assertFalse("AppError" in source, "Legacy AppError usage in ${file.path}")
        }
    }

    @Test
    fun architecturePluginIsAppliedAndShellDelegatesToIt() {
        val rootBuild = File("../build.gradle.kts").readText()
        val boundaryScript = File(
            "../scripts/check-module-dependencies.sh",
        ).readText()

        assertTrue(
            "id(\"openstory.architecture\")" in rootBuild,
            "Root project must apply openstory.architecture",
        )
        assertTrue(
            "verifyArchitecture" in boundaryScript,
            "Boundary shell entry point must delegate to Gradle architecture verification",
        )
    }

    @Test
    fun catalogRuntimeDependencyIsAnExplicitPolicyDecision() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )

        assertTrue(
            ":plugins:runtime" in policy.modules
                .getValue(":catalog")
                .productionDependencies,
            ":catalog must explicitly allow :plugins:runtime",
        )
    }

    @Test
    fun applicationUsesComposeConventionPlugin() {
        val appBuild = File("../app/build.gradle.kts").readText()

        assertTrue(
            "id(\"openstory.compose\")" in appBuild,
            "Application must apply the Compose convention plugin",
        )
    }

    @Test
    fun roomConventionConfiguresCommittedSchemaExport() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val buildLogic = File("build.gradle.kts").readText()
        val convention = File(
            "src/main/kotlin/app/openstory/build/RoomConventionPlugin.kt",
        ).readText()

        assertTrue(
            "room-gradle-plugin" in catalog,
            "Version catalog must expose the Room Gradle plugin",
        )
        assertTrue(
            "implementation(libs.room.gradle.plugin)" in buildLogic,
            "Build logic must load the Room Gradle plugin",
        )
        listOf(
            "pluginManager.apply(\"androidx.room\")",
            "schemaDirectory(\"${'$'}projectDir/schemas\")",
        ).forEach { expected ->
            assertTrue(
                expected in convention,
                "Room convention is missing: $expected",
            )
        }
    }
}
