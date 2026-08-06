package app.openstory

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureSmokeTest {

    private val root =
        File("..").canonicalFile

    @Test
    fun coreModelStaysPlatformIndependent() {
        val source = File(
            root,
            "core/model/src/main",
        )
            .walkTopDown()
            .filter { file ->
                file.isFile && file.extension == "kt"
            }
            .joinToString(
                separator = "\n",
            ) { file ->
                file.readText()
            }

        assertFalse(
            "android." in source,
            "core:model must not import Android APIs",
        )
        assertFalse(
            "androidx.compose" in source,
            "core:model must not import Compose APIs",
        )
    }

    @Test
    fun repositoryQualityGateFilesAreCommitted() {
        val requiredPaths = listOf(
            ".github/workflows/android.yml",
            "config/detekt/detekt.yml",
            "scripts/verify.sh",
            "scripts/check-module-dependencies.sh",
            "README.md",
            "gradle/verification-metadata.xml",
        )

        requiredPaths.forEach { path ->
            assertTrue(
                File(root, path).isFile,
                "Missing repository quality gate: $path",
            )
        }

        val wrapperProperties = File(
            root,
            "gradle/wrapper/gradle-wrapper.properties",
        ).readText()

        assertTrue(
            "distributionSha256Sum=" in wrapperProperties,
            "Gradle wrapper distribution checksum is missing",
        )
    }

    @Test
    fun ciUsesSharedVerificationAndUploadsFailureReports() {
        val workflow = File(
            root,
            ".github/workflows/android.yml",
        ).readText()

        listOf(
            "pull_request:",
            "actions/setup-java@v5",
            "distribution: temurin",
            "java-version: \"17\"",
            "gradle/actions/setup-gradle@v6",
            "run: ./scripts/verify.sh",
            "if: failure()",
            "actions/upload-artifact@v7",
            "app/build/reports",
            "build/reports/detekt",
        ).forEach { expected ->
            assertTrue(
                expected in workflow,
                "CI workflow is missing: $expected",
            )
        }
    }

    @Test
    fun sharedVerificationCoversFutureModules() {
        val verifyScript = File(
            root,
            "scripts/verify.sh",
        ).readText()

        assertTrue(
            "--dependency-verification strict" in verifyScript,
            "Strict dependency verification is missing",
        )

        val configuredTasks = verifyScript
            .lineSequence()
            .map { line ->
                line.trim()
            }
            .map { line ->
                line.removeSuffix("\\").trim()
            }
            .filter { line ->
                line.isNotEmpty()
            }
            .toSet()

        listOf(
            ":build-logic:test",
            "test",
            "testDebugUnitTest",
            "lintDebug",
            "detekt",
            ":app:assembleDebug",
        ).forEach { expected ->
            assertTrue(
                expected in configuredTasks,
                "Shared verification is missing task: $expected",
            )
        }

        assertFalse(
            ":app:testDebugUnitTest" in configuredTasks,
            "Verification must cover every Android module's unit tests",
        )
        assertFalse(
            ":app:lintDebug" in configuredTasks,
            "Verification must cover every Android module's lint task",
        )
    }

    @Test
    fun readmeDocumentsExactAndroidSdkBootstrap() {
        val readme = File(
            root,
            "README.md",
        ).readText()

        listOf(
            "JDK 17",
            "Android SDK Platform 37",
            "SDK Build-Tools 36.0.0",
            "platforms;android-37",
            "build-tools;36.0.0",
            "local.properties",
            "sdk.dir=",
            "./scripts/verify.sh",
        ).forEach { expected ->
            assertTrue(
                expected in readme,
                "README bootstrap is missing: $expected",
            )
        }
    }
}
