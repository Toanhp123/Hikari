package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner

class AppStructuralVerifierTest {
    @Test
    fun rejectsBroadAuthorityAndTestOnlyProductionApi() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "app/openstory/startup/StartupManager.kt" to
                    """
                    package app.openstory.startup
                    class StartupManager
                    object BootRegistry
                    interface StartupCoordinator
                    fun interface EventManager
                    class ShellServiceLocator
                    fun forTest() = StartupManager()
                    fun createForTest() = StartupManager()
                    """.trimIndent(),
                "app/openstory/startup/Extension.java" to
                    "non-sealed interface ExtensionManager {}",
            ),
            policy = foundationTestPolicy(),
        )

        assertEquals(
            setOf(
                "app/openstory/startup/StartupManager.kt:BootRegistry",
                "app/openstory/startup/StartupManager.kt:EventManager",
                "app/openstory/startup/StartupManager.kt:ShellServiceLocator",
                "app/openstory/startup/StartupManager.kt:StartupCoordinator",
                "app/openstory/startup/StartupManager.kt:StartupManager",
                "app/openstory/startup/Extension.java:ExtensionManager",
            ),
            violations
                .filter { it.code == "v2_structure.broad_authority" }
                .map { it.detail }
                .toSet(),
        )
        assertEquals(
            setOf(
                "app/openstory/startup/StartupManager.kt:createForTest(",
                "app/openstory/startup/StartupManager.kt:forTest(",
            ),
            violations
                .filter { it.code == "v2_structure.test_only_production_api" }
                .map { it.detail }
                .toSet(),
        )
    }

    @Test
    fun rejectsPackageCycle() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "a/A.kt" to
                    """
                    package app.openstory.a
                    import app.openstory.b.B
                    class A
                    """.trimIndent(),
                "b/B.kt" to
                    """
                    package app.openstory.b
                    import app.openstory.a.A
                    class B
                    """.trimIndent(),
            ),
            policy = foundationTestPolicy(),
        )

        assertEquals(
            listOf(
                FoundationViolation(
                    code = "v2_structure.package_cycle",
                    detail = "packages=app.openstory.a,app.openstory.b",
                ),
            ),
            violations,
        )
    }

    @Test
    fun acceptsOneWayStartupPackageGraph() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "ui/HikariBootSurface.kt" to
                    "package app.openstory.ui\nclass HikariBootSurface",
                "startup/AppLaunchState.kt" to
                    "package app.openstory.startup\nsealed interface AppLaunchState",
                "startup/ui/StartupGate.kt" to
                    """
                    package app.openstory.startup.ui
                    import app.openstory.startup.AppLaunchState
                    import app.openstory.ui.HikariBootSurface
                    class StartupGate
                    """.trimIndent(),
            ),
            policy = foundationTestPolicy(),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun ignoresExternalModuleImportBelowAnOwnedPackagePrefix() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "MainActivity.kt" to
                    """
                    package app.openstory
                    import app.openstory.startup.ui.HikariStartupApp
                    class MainActivity
                    """.trimIndent(),
                "startup/ui/StartupGate.kt" to
                    """
                    package app.openstory.startup.ui
                    import app.openstory.catalog.feature.CatalogEntryPoint
                    class StartupGate
                    """.trimIndent(),
            ),
            policy = foundationTestPolicy(),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun resolvesImportToLongestDeclaredPackagePrefix() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "a/A.kt" to
                    """
                    package app.openstory.a
                    import app.openstory.b.B
                    class A
                    """.trimIndent(),
                "a/deep/DeepType.kt" to
                    "package app.openstory.a.deep\nclass DeepType",
                "b/B.kt" to
                    """
                    package app.openstory.b
                    import app.openstory.a.deep.DeepType
                    class B
                    """.trimIndent(),
            ),
            policy = foundationTestPolicy(),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun ignoresSamePackageImportSelfEdge() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "startup/AppLaunchState.kt" to
                    "package app.openstory.startup\nclass AppLaunchState",
                "startup/Store.kt" to
                    """
                    package app.openstory.startup
                    import app.openstory.startup.AppLaunchState
                    class Store
                    """.trimIndent(),
            ),
            policy = foundationTestPolicy(),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun rejectsTotalProductionLinesAboveBudget() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "A.kt" to "line1\nline2",
                "B.kt" to "line1\nline2\nline3",
            ),
            policy = foundationTestPolicy(maxProductionKotlinLines = 4),
        )

        assertEquals(
            FoundationViolation(
                code = "v2_structure.line_budget_exceeded",
                detail = "actual=5 max=4",
            ),
            violations.single(),
        )
    }

    @Test
    fun acceptsProductionLinesAtBudget() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf("A.kt" to "line1\nline2\nline3\nline4"),
            policy = foundationTestPolicy(maxProductionKotlinLines = 4),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun ignoresBroadTypeWordsThatAreNotDeclarations() {
        val violations = AppStructuralVerifier.verify(
            sources = mapOf(
                "Startup.kt" to
                    """
                    package app.openstory.startup
                    // class CommentedManager
                    val documentation = "interface QuotedRegistry"
                    class Startup
                    """.trimIndent(),
            ),
            policy = foundationTestPolicy(),
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun gradleTaskReportsCompleteStructuralViolationSet() {
        val root = createTempDirectory("app-structure").toFile()
        try {
            val policyFile = File(root, "config/architecture/v2-foundation-policy.json")
            policyFile.parentFile.mkdirs()
            policyFile.writeText(
                """
                {
                  "schemaVersion": 1,
                  "maxProductionKotlinLines": 2,
                  "forbiddenSourceTokens": [],
                  "forbiddenBuildTokens": [],
                  "forbiddenBroadTypeSuffixes": ["Manager"],
                  "forbiddenManifestPermissions": [],
                  "allowedStartupInitializers": []
                }
                """.trimIndent(),
            )
            val appProjectDirectory = File(root, "app").apply { mkdirs() }
            val source = File(
                appProjectDirectory,
                "src/main/kotlin/app/openstory/StartupManager.kt",
            ).apply {
                parentFile.mkdirs()
                writeText(
                    """
                    package app.openstory
                    class StartupManager
                    fun createForTest() = StartupManager()
                    """.trimIndent(),
                )
            }
            val project = ProjectBuilder.builder().withProjectDir(appProjectDirectory).build()
            val task = project.tasks.register(
                "verifyAppStructure",
                VerifyAppStructureTask::class.java,
            ).get().apply {
                this.policyFile.set(policyFile)
                appDirectory.set(appProjectDirectory)
                productionSources.from(source)
            }

            val error = assertFailsWith<GradleException> {
                task.verifyStructure()
            }

            assertTrue("v2_structure.line_budget_exceeded" in error.message.orEmpty())
            assertTrue("v2_structure.broad_authority" in error.message.orEmpty())
            assertTrue("v2_structure.test_only_production_api" in error.message.orEmpty())
            assertTrue(
                "src/main/kotlin/app/openstory/StartupManager.kt" in error.message.orEmpty(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun gradleTaskSupportsConfigurationCache() {
        val root = createTempDirectory("app-structure-configuration-cache").toFile()
        try {
            File(root, "settings.gradle.kts").writeText(
                "rootProject.name = \"app-structure-configuration-cache\"",
            )
            File(root, "build.gradle").writeText(
                """
                plugins {
                    id 'openstory.foundation'
                }

                def taskType = Class.forName(
                    'app.openstory.build.architecture.VerifyAppStructureTask'
                )
                tasks.register('verifyAppStructure', taskType) {
                    policyFile.set(layout.projectDirectory.file(
                        'config/architecture/v2-foundation-policy.json'
                    ))
                    productionSources.from(layout.projectDirectory.file(
                        'src/main/kotlin/app/openstory/App.kt'
                    ))
                    appDirectory.set(layout.projectDirectory)
                }
                """.trimIndent(),
            )
            File(root, "config/architecture/v2-foundation-policy.json").apply {
                parentFile.mkdirs()
                writeText(
                    """
                    {
                      "schemaVersion": 1,
                      "maxProductionKotlinLines": 10,
                      "forbiddenSourceTokens": [],
                      "forbiddenBuildTokens": [],
                      "forbiddenBroadTypeSuffixes": [],
                      "forbiddenManifestPermissions": [],
                      "allowedStartupInitializers": []
                    }
                    """.trimIndent(),
                )
            }
            File(root, "src/main/kotlin/app/openstory/App.kt").apply {
                parentFile.mkdirs()
                writeText("package app.openstory\nclass App")
            }

            val result = GradleRunner.create()
                .withProjectDir(root)
                .withPluginClasspath()
                .withArguments("verifyAppStructure", "--configuration-cache")
                .build()

            assertTrue("BUILD SUCCESSFUL" in result.output)
        } finally {
            root.deleteRecursively()
        }
    }
}
