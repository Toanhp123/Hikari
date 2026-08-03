package app.openstory.build

import java.io.File
import kotlin.test.Test
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
    ).forEach { (name, version) ->
        assertTrue(
            """$name = "$version"""" in catalog,
            "Expected $name version $version",
        )
    }
}

    @Test
    fun settingsContainsBootstrapModules() {
        val settings = File("../settings.gradle.kts").readText()

        listOf(
            ":app",
            ":core:common",
            ":core:model",
            ":test:fixtures",
        ).forEach { module ->
            assertTrue(
                "include(\"$module\")" in settings,
                "Missing module $module",
            )
        }
    }

    @Test
    fun buildLogicRegistersFoundationConventionPlugins() {
        val buildLogic = File("build.gradle.kts").readText()

        listOf(
            "openstory.android.application",
            "openstory.android.library",
            "openstory.kotlin.jvm",
        ).forEach { pluginId ->
            assertTrue(
                "id = \"$pluginId\"" in buildLogic,
                "Missing convention plugin $pluginId",
            )
        }
    }

    @Test
    fun bootstrapModulesUseFoundationConventions() {
        val expectedPlugins = mapOf(
            "../app/build.gradle.kts" to "id(\"openstory.android.application\")",
            "../core/common/build.gradle.kts" to "id(\"openstory.kotlin.jvm\")",
            "../core/model/build.gradle.kts" to "id(\"openstory.kotlin.jvm\")",
            "../test/fixtures/build.gradle.kts" to "id(\"openstory.kotlin.jvm\")",
        )

        expectedPlugins.forEach { (path, expectedPlugin) ->
            val buildFile = File(path)

            assertTrue(
                buildFile.isFile,
                "Missing build script $path",
            )

            assertTrue(
                expectedPlugin in buildFile.readText(),
                "$path does not apply $expectedPlugin",
            )
        }
    }
}
