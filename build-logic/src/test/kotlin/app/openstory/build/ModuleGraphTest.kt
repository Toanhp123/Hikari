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
        val declaredModules = Regex("""include\("([^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(policy.modules.keys, declaredModules)
    }

    @Test
    fun currentGraphContainsNoLegacyModules() {
        val settings = File("../settings.gradle.kts").readText()
        val policy = File("../config/architecture/module-boundaries.json").readText()

        val forbidden = listOf(
            ":core:model",
            ":core:database",
            ":core:matching",
            ":core:plugin-api",
            ":core:plugin-host",
            ":core:network",
            ":feature:home",
            ":feature:story",
            ":test:fixtures",
        )
        forbidden.forEach { module ->
            assertFalse(module in settings, "Legacy module still in settings: $module")
            assertFalse(module in policy, "Legacy module still in architecture policy: $module")
        }
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
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )

        policy.modules.forEach { (module, rule) ->
            val buildFile = File("../${rule.path}/build.gradle.kts")
            val expectedPlugin = when (rule.platform.policyValue) {
                "android-application" -> "id(\"openstory.android.application\")"
                "android-library" -> "id(\"openstory.android.library\")"
                "android-test" -> "alias(libs.plugins.android.test)"
                "jvm" -> "id(\"openstory.kotlin.jvm\")"
                else -> error("Unexpected platform ${rule.platform.policyValue}")
            }
            assertTrue(buildFile.isFile, "Missing build script for $module at ${rule.path}")
            assertTrue(
                expectedPlugin in buildFile.readText(),
                "$module does not apply $expectedPlugin",
            )
        }
    }

    @Test
    fun readerEngineIsConstitutionallyPureJvm() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        val rule = policy.modules.getValue(":reader:engine")

        assertEquals("jvm", rule.platform.policyValue)
        assertEquals("exact", rule.dependencyMode.policyValue)
        assertEquals(setOf(":core:common"), rule.productionDependencies)
        assertTrue(rule.testDependencies.isEmpty())

        val build = File("../reader/engine/build.gradle.kts").readText()
        assertTrue("id(\"openstory.kotlin.jvm\")" in build)
        assertFalse("openstory.android" in build)
        assertFalse("openstory.compose" in build)
        assertFalse("openstory.hilt" in build)
        assertFalse("openstory.room" in build)
        assertFalse("kotlinx.coroutines" in build)
        assertFalse("kotlinx.serialization" in build)

        val engineConsumers = policy.modules
            .filterValues { ":reader:engine" in it.productionDependencies }
            .keys
        assertEquals(setOf(":reader"), engineConsumers)

        val readerBuild = File("../reader/build.gradle.kts").readText()
        assertTrue("implementation(project(\":reader:engine\"))" in readerBuild)
        assertFalse("api(project(\":reader:engine\"))" in readerBuild)
    }

    @Test
    fun hesV1AndRiccV1ArchitectureAndPersistenceBoundaryAreFrozen() {
        val root = File("..").canonicalFile
        val policy = ModuleBoundaryPolicyLoader.load(
            File(root, "config/architecture/module-boundaries.json"),
        )
        val productionModules = policy.modules.filterValues {
            it.platform.policyValue != "android-test"
        }

        assertEquals(17, productionModules.size)
        assertEquals("android-test", policy.modules.getValue(":benchmark").platform.policyValue)

        val engine = policy.modules.getValue(":reader:engine")
        assertEquals("jvm", engine.platform.policyValue)
        assertEquals("exact", engine.dependencyMode.policyValue)
        assertEquals(setOf(":core:common"), engine.productionDependencies)

        val reader = policy.modules.getValue(":reader")
        assertTrue(":reader:engine" in reader.productionDependencies)
        assertFalse(":settings" in reader.productionDependencies)

        val featureReader = policy.modules.getValue(":feature:reader")
        assertFalse(":downloads" in featureReader.productionDependencies)
        assertFalse(
            "project(\":downloads\")" in File(root, "feature/reader/build.gradle.kts").readText(),
        )

        val database = File(
            root,
            "storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt",
        ).readText()
        assertTrue("version = 12," in database)
        assertTrue("RoomMigrations.MIGRATION_10_11" in database)
        assertTrue("RoomMigrations.MIGRATION_11_12" in database)

        val schemaVersions = File(
            root,
            "storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase",
        ).listFiles()
            .orEmpty()
            .mapNotNull { file -> file.nameWithoutExtension.toIntOrNull() }
            .sorted()
        assertEquals((1..12).toList(), schemaVersions)

        val riccEngineLeaks = File(root, "reader/engine/src").walkTopDown()
            .filter(File::isFile)
            .filter { file ->
                file.name.contains("ReaderAsset", ignoreCase = true) ||
                    file.name.contains("ricc", ignoreCase = true) ||
                    (file.extension == "kt" && "ReaderAsset" in file.readText())
            }
            .map { it.relativeTo(root).path }
            .toList()
        assertTrue(riccEngineLeaks.isEmpty(), "RICC leaked into :reader:engine: $riccEngineLeaks")

        val leakedEngineImports = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .filterNot { "${File.separator}build${File.separator}" in it.path }
            .filterNot { it.path.startsWith(File(root, "reader/src/main").path) }
            .filterNot { it.path.startsWith(File(root, "reader/engine/src/main").path) }
            .filter { "app.openstory.reader.engine" in it.readText() }
            .map { it.relativeTo(root).path }
            .toList()
        assertTrue(
            leakedEngineImports.isEmpty(),
            "HES engine types leaked outside :reader: ${leakedEngineImports.joinToString()}",
        )
    }

    @Test
    fun libraryContentSearchUsesOnlyApprovedPluginFacadeDependencies() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )

        assertEquals(
            setOf(":core:common", ":catalog", ":plugins:api", ":plugins:runtime"),
            policy.modules.getValue(":library").productionDependencies,
        )
        val libraryBuild = File("../library/build.gradle.kts").readText()
        assertTrue("project(\":plugins:api\")" in libraryBuild)
        assertTrue("project(\":plugins:runtime\")" in libraryBuild)
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
    fun roomKeepsApprovedPluginPersistenceSpiAccess() {
        val policy = ModuleBoundaryPolicyLoader.load(
            File("../config/architecture/module-boundaries.json"),
        )
        val roomRule = policy.modules.getValue(":storage:room")

        assertTrue(
            ":plugins:runtime" in roomRule.productionDependencies,
            ":storage:room must keep the runtime dependency required by plugin persistence adapters",
        )
        assertFalse(
            "app.openstory.plugins.runtime.persistence." in roomRule.forbiddenProductionImports,
            ":storage:room must be allowed to implement plugins.runtime.persistence SPI contracts",
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
