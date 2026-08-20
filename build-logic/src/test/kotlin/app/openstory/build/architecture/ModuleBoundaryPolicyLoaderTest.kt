package app.openstory.build.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleBoundaryPolicyLoaderTest {
    @Test
    fun loadsVersionOnePolicy() {
        val file = policyFile(
            """
            {
              "schemaVersion": 1,
              "modules": {
                ":core:common": {
                  "path": "core/common",
                  "platform": "jvm",
                  "productionDependencies": [],
                  "testDependencies": [],
                  "forbiddenProductionImports": []
                }
              }
            }
            """.trimIndent(),
        )

        val policy = ModuleBoundaryPolicyLoader.load(file)

        assertEquals(1, policy.schemaVersion)
        assertEquals(setOf(":core:common"), policy.modules.keys)
        assertEquals(ModulePlatform.JVM, policy.modules.getValue(":core:common").platform)
        assertEquals(DependencyMode.EXACT, policy.modules.getValue(":core:common").dependencyMode)
    }

    @Test
    fun loadsVersionTwoDependencyMode() {
        val file = policyFile(
            """
            {
              "schemaVersion": 2,
              "modules": {
                ":core:common": {
                  "path": "core/common",
                  "platform": "jvm",
                  "dependencyMode": "allowlist",
                  "productionDependencies": [],
                  "testDependencies": [],
                  "forbiddenProductionImports": []
                }
              }
            }
            """.trimIndent(),
        )

        val policy = ModuleBoundaryPolicyLoader.load(file)

        assertEquals(2, policy.schemaVersion)
        assertEquals(DependencyMode.ALLOWLIST, policy.modules.getValue(":core:common").dependencyMode)
    }

    @Test
    fun loadsAndroidTestPlatform() {
        val file = policyFile(
            """
            {
              "schemaVersion": 2,
              "modules": {
                ":benchmark": {
                  "path": "benchmark",
                  "platform": "android-test",
                  "dependencyMode": "allowlist",
                  "productionDependencies": [],
                  "testDependencies": [],
                  "forbiddenProductionImports": []
                }
              }
            }
            """.trimIndent(),
        )

        val policy = ModuleBoundaryPolicyLoader.load(file)

        assertEquals(ModulePlatform.ANDROID_TEST, policy.modules.getValue(":benchmark").platform)
    }

    @Test
    fun unsupportedSchemaVersionFailsClosed() {
        val file = policyFile(
            """
            {
              "schemaVersion": 3,
              "modules": {}
            }
            """.trimIndent(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ModuleBoundaryPolicyLoader.load(file)
        }

        assertTrue("module_policy.unsupported_schema" in error.message.orEmpty())
    }

    @Test
    fun dependencyMustReferenceDeclaredModule() {
        val file = policyFile(
            """
            {
              "schemaVersion": 1,
              "modules": {
                ":core:model": {
                  "path": "core/model",
                  "platform": "jvm",
                  "productionDependencies": [":core:missing"],
                  "testDependencies": [],
                  "forbiddenProductionImports": []
                }
              }
            }
            """.trimIndent(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ModuleBoundaryPolicyLoader.load(file)
        }

        assertTrue("module_policy.unknown_dependency" in error.message.orEmpty())
    }


    @Test
    fun unknownRuleFieldFailsClosed() {
        val file = policyFile(
            """
            {
              "schemaVersion": 1,
              "modules": {
                ":core:common": {
                  "path": "core/common",
                  "platform": "jvm",
                  "productionDependencies": [],
                  "testDependencies": [],
                  "forbiddenProductionImports": [],
                  "unexpected": true
                }
              }
            }
            """.trimIndent(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ModuleBoundaryPolicyLoader.load(file)
        }

        assertTrue("module_policy.unknown_field" in error.message.orEmpty())
    }

    private fun policyFile(content: String): File =
        kotlin.io.path.createTempFile(
            prefix = "module-boundaries-",
            suffix = ".json",
        ).toFile().apply {
            writeText(content)
            deleteOnExit()
        }
}
