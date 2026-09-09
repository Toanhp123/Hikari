package app.openstory.build.architecture

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder

class BootSourceBoundaryVerifierTest {
    @Test
    fun repositoryStepTwoPolicyKeepsStartupSourceBansWhileGraphVerifierOwnsProjectEdges() {
        val policyFile = File("../config/architecture/v2-foundation-policy.json")
        assertTrue(policyFile.isFile, "Canonical V2 foundation policy is missing")
        val policy = FoundationPolicyLoader.parse(policyFile.readText())

        val violations = BootSourceBoundaryVerifier.verify(
            sources = mapOf(
                "MainActivity.kt" to "import androidx.work.WorkManager",
            ),
            buildScript = """implementation(project(":reader:engine"))""",
            policy = policy,
        )

        assertEquals(
            listOf(
                FoundationViolation(
                    "v2_boot.forbidden_source_reference",
                    "MainActivity.kt:androidx.work.",
                ),
            ),
            violations,
        )
    }

    @Test
    fun reportsForbiddenSourceAndBuildTokens() {
        val policy = foundationTestPolicy(
            forbiddenSourceTokens = setOf("androidx.work.", "app.openstory.reader."),
            forbiddenBuildTokens = setOf("implementation(project("),
        )

        val violations = BootSourceBoundaryVerifier.verify(
            sources = mapOf(
                "MainActivity.kt" to
                    "package app.openstory\nimport androidx.work.WorkManager",
                "StartupGate.kt" to
                    "package app.openstory.startup\nval x = app.openstory.reader.ReaderRuntime",
            ),
            buildScript = """implementation(project(":reader"))""",
            policy = policy,
        )

        assertEquals(
            setOf(
                "v2_boot.forbidden_build_reference",
                "v2_boot.forbidden_source_reference",
            ),
            violations.map { it.code }.toSet(),
        )
    }

    @Test
    fun reportsEveryDistinctViolationInDeterministicOrder() {
        val policy = foundationTestPolicy(
            forbiddenSourceTokens = linkedSetOf("reader.", "work."),
            forbiddenBuildTokens = linkedSetOf("room", "project("),
        )

        val violations = BootSourceBoundaryVerifier.verify(
            sources = linkedMapOf(
                "z/Startup.kt" to "reader.ReaderRuntime work.WorkManager reader.ReaderRuntime",
                "a/Main.kt" to "work.WorkManager",
            ),
            buildScript = "room project( room",
            policy = policy,
        )

        assertEquals(
            listOf(
                FoundationViolation("v2_boot.forbidden_build_reference", "project("),
                FoundationViolation("v2_boot.forbidden_build_reference", "room"),
                FoundationViolation("v2_boot.forbidden_source_reference", "a/Main.kt:work."),
                FoundationViolation("v2_boot.forbidden_source_reference", "z/Startup.kt:reader."),
                FoundationViolation("v2_boot.forbidden_source_reference", "z/Startup.kt:work."),
            ),
            violations,
        )
    }

    @Test
    fun allowsComposeAndDataStoreShellReferences() {
        val policy = foundationTestPolicy()
        val violations = BootSourceBoundaryVerifier.verify(
            sources = mapOf(
                "StartupGate.kt" to
                    """
                    package app.openstory.startup.ui
                    import androidx.compose.runtime.Composable
                    import androidx.datastore.preferences.core.Preferences
                    """.trimIndent(),
            ),
            buildScript = "implementation(libs.androidx.datastore.preferences)",
            policy = policy,
        )

        assertTrue(violations.isEmpty())
    }

    @Test
    fun gradleTaskReportsAllViolationsUsingRelativeSourcePaths() {
        val root = createTempDirectory("boot-source-boundary").toFile()
        try {
            val policyFile = File(root, "config/architecture/v2-foundation-policy.json")
            policyFile.parentFile.mkdirs()
            policyFile.writeText(
                """
                {
                  "schemaVersion": 1,
                  "maxProductionKotlinLines": 300,
                  "forbiddenSourceTokens": ["androidx.work.", "app.openstory.reader."],
                  "forbiddenBuildTokens": ["implementation(project("],
                  "forbiddenBroadTypeSuffixes": [],
                  "forbiddenManifestPermissions": [],
                  "allowedStartupInitializers": []
                }
                """.trimIndent(),
            )
            val appDirectory = File(root, "app")
            val buildScript = File(appDirectory, "build.gradle.kts").apply {
                parentFile.mkdirs()
                writeText("""implementation(project(":reader"))""")
            }
            val activity = File(
                appDirectory,
                "src/main/kotlin/app/openstory/MainActivity.kt",
            ).apply {
                parentFile.mkdirs()
                writeText("import androidx.work.WorkManager")
            }
            val startup = File(
                appDirectory,
                "src/main/java/app/openstory/startup/Startup.java",
            ).apply {
                parentFile.mkdirs()
                writeText("app.openstory.reader.ReaderRuntime runtime;")
            }
            val ignored = File(appDirectory, "src/main/resources/Ignored.txt").apply {
                parentFile.mkdirs()
                writeText("androidx.work.WorkManager")
            }
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val task = project.tasks.register(
                "verifyBootSourceBoundary",
                VerifyBootSourceBoundaryTask::class.java,
            ).get().apply {
                this.policyFile.set(policyFile)
                appBuildScript.set(buildScript)
                productionSources.from(activity, startup, ignored)
            }

            val error = assertFailsWith<GradleException> {
                task.verifyBoundary()
            }

            assertTrue(
                "v2_boot.forbidden_build_reference: implementation(project(" in
                    error.message.orEmpty(),
            )
            assertTrue(
                "v2_boot.forbidden_source_reference: " +
                    "src/main/kotlin/app/openstory/MainActivity.kt:androidx.work." in
                    error.message.orEmpty(),
            )
            assertTrue(
                "v2_boot.forbidden_source_reference: " +
                    "src/main/java/app/openstory/startup/Startup.java:app.openstory.reader." in
                    error.message.orEmpty(),
            )
            assertFalse("Ignored.txt" in error.message.orEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
