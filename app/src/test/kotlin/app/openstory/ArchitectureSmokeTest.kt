package app.openstory

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureSmokeTest {
    private val root = File("..").canonicalFile

    @Test
    fun coreModelStaysPlatformIndependent() {
        val source = File(root, "core/model/src/main")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString(separator = "\n") { file -> file.readText() }

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
    fun applicationIdentityUsesOpenStoryNamespace() {
        val appBuild = File(root, "app/build.gradle.kts").readText()

        assertTrue(
            "namespace = \"app.openstory\"" in appBuild,
            "Android namespace must be app.openstory",
        )
        assertTrue(
            "applicationId = \"app.openstory\"" in appBuild,
            "Android applicationId must be app.openstory",
        )
        assertFalse(
            "com.example.hikari" in appBuild,
            "Legacy application identity must not remain in production configuration",
        )
    }

    @Test
    fun repositoryQualityGateFilesAreCommitted() {
        val requiredPaths = listOf(
            ".github/workflows/android.yml",
            "config/architecture/module-boundaries.json",
            "config/detekt/detekt.yml",
            "scripts/check-module-dependencies.sh",
            "scripts/verify-baseline-architecture.sh",
            "scripts/verify.sh",
            "scripts/verify-instrumentation.sh",
            "scripts/verify-wave-checkpoint.sh",
            "scripts/tests/verify-baseline-architecture-test.sh",
            "scripts/tests/verify-instrumentation-test.sh",
            "scripts/tests/verify-wave-checkpoint-test.sh",
            "docs/contributing/adding-a-module.md",
            "docs/internal/checkpoints/wave-01-remediation.md",
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
    fun ciUsesSharedVerificationAndTwoSdkInstrumentationGates() {
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
            "find scripts -type f -name '*.sh'",
            "instrumentation-api-26:",
            "instrumentation-api-37:",
            "api-level: 26",
            "api-level: 37",
            "./scripts/verify-instrumentation.sh 26",
            "./scripts/verify-instrumentation.sh 37",
            "wave-01-checkpoint:",
            "core/database/build/reports",
            "core/network/build/reports",
            "core/plugin-api/build/reports",
            "core/plugin-host/build/reports",
        ).forEach { expected ->
            assertTrue(
                expected in workflow,
                "CI workflow is missing: $expected",
            )
        }
    }

    @Test
    fun sharedVerificationCoversArchitectureAndEveryModuleClass() {
        val boundaryScript = File(
            root,
            "scripts/check-module-dependencies.sh",
        ).readText()
        val verifyScript = File(root, "scripts/verify.sh").readText()

        assertTrue(
            "verifyArchitecture" in boundaryScript,
            "Architecture verification task is missing",
        )
        assertTrue(
            "GRADLEW=\"${'$'}{GRADLEW:-./gradlew}\"" in boundaryScript,
            "Architecture gate must support an injected Gradle launcher",
        )
        assertTrue(
            "--dependency-verification strict" in boundaryScript,
            "Architecture gate must use strict dependency verification",
        )
        assertTrue(
            "for test_script in ./scripts/tests/*.sh" in verifyScript,
            "Fast verification must run every shell contract test",
        )
        assertTrue(
            "./scripts/verify-baseline-architecture.sh" in verifyScript,
            "Fast verification must invoke the baseline architecture gate",
        )
        assertTrue(
            verifyScript.indexOf("./scripts/verify-baseline-architecture.sh") <
                verifyScript.indexOf("ROOM_SCHEMA_FINGERPRINT"),
            "Baseline architecture must be checked before Room fingerprinting",
        )
        assertTrue(
            "./scripts/check-module-dependencies.sh" in verifyScript,
            "Fast verification must invoke the architecture gate",
        )

        val configuredTasks = verifyScript
            .lineSequence()
            .map(String::trim)
            .map { line -> line.removeSuffix("\\").trim() }
            .filter(String::isNotEmpty)
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
    fun readmeDocumentsFastAndCheckpointBootstrap() {
        val readme = File(root, "README.md").readText()

        listOf(
            "JDK 17",
            "Android SDK Platform 37",
            "SDK Build-Tools 36.0.0",
            "platforms;android-37",
            "build-tools;36.0.0",
            "local.properties",
            "sdk.dir=",
            "./scripts/verify.sh",
            "./scripts/verify-wave-checkpoint.sh",
            "API 26",
            "API 37",
            "config/architecture/module-boundaries.json",
            ":core:plugin-host",
        ).forEach { expected ->
            assertTrue(
                expected in readme,
                "README bootstrap is missing: $expected",
            )
        }
    }
}
