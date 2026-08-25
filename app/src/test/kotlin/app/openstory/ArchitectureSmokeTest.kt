package app.openstory

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureSmokeTest {
    private val root = File("..").canonicalFile

    @Test
    fun applicationRootUsesHikariTheme() {
        val appRoot = File(
            root,
            "app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt",
        ).readText()

        assertTrue(
            "HikariTheme" in appRoot,
            "Application root must use the shared Hikari theme",
        )
    }

    @Test
    fun legacyCoreModulesAreDeletedAfterCatalogCutover() {
        listOf("core/model", "core/matching", "core/database").forEach { module ->
            assertFalse(File(root, module).exists(), "Legacy module must be deleted: $module")
        }
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
    fun readerProductionUsesHesSessionWithoutLegacyReleaseComparator() {
        val viewModel = File(
            root,
            "feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt",
        ).readText()
        val readerModule = File(root, "app/src/main/kotlin/app/openstory/di/ReaderModule.kt").readText()
        val selectionRoot = File(root, "reader/src/main/kotlin/app/openstory/reader/selection")

        assertTrue("ReaderRouteSessionFactory" in viewModel)
        assertTrue("routeSession.execute(" in viewModel)
        assertFalse("ReaderDocumentRepository" in viewModel)
        val executor = File(
            root,
            "reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt",
        ).readText()

        assertFalse("ReleaseSelector" in readerModule)
        assertFalse("provideReaderDocumentRepository" in readerModule)
        assertFalse(selectionRoot.exists(), "Legacy Reader selection package must be fully retired")
        assertFalse(
            File(root, "reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentRepository.kt").exists(),
        )
        assertFalse("executeCompatibility" in executor)
        assertFalse("ReleaseCandidate" in executor)
    }

    @Test
    fun repositoryQualityGateFilesAreCommitted() {
        val requiredPaths = listOf(
            ".github/workflows/android.yml",
            "config/architecture/module-boundaries.json",
            "config/detekt/detekt.yml",
            "scripts/check-module-dependencies.sh",
            "scripts/verify-architecture-baseline-2.sh",
            "scripts/verify-current-architecture.sh",
            "scripts/structural-review-report.sh",
            "scripts/verify-source-layout.sh",
            "scripts/verify.sh",
            "scripts/verify-fast.sh",
            "scripts/verification-common.sh",
            "scripts/instrumentation/android.sh",
            "scripts/instrumentation/storage-room.sh",
            "scripts/checkpoints/app-shell.sh",
            "scripts/tests/verify-architecture-baseline-2-test.sh",
            "scripts/tests/verify-current-architecture-test.sh",
            "scripts/tests/verify-source-layout-test.sh",
            "scripts/tests/instrumentation-android-test.sh",
            "scripts/tests/checkpoint-app-shell-test.sh",
            "docs/contributing/adding-a-module.md",
            "docs/internal/archive/pre-baseline-development/checkpoints/wave-01-remediation.md",
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
            "./scripts/instrumentation/android.sh 26",
            "./scripts/instrumentation/android.sh 37",
            "wave-01-checkpoint:",
            "storage/room/build/reports",
            "plugins/api/build/reports",
            "plugins/runtime/build/reports",
            "catalog/build/reports",
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
        val commonScript = File(root, "scripts/verification-common.sh").readText()
        val verifyScript = File(root, "scripts/verify.sh").readText()
        val fastVerifyScript = File(root, "scripts/verify-fast.sh").readText()

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
        assertFalse(
            "--no-daemon" in boundaryScript,
            "Architecture gate must allow Gradle daemon reuse",
        )
        assertTrue(
            "for test_script in ./scripts/tests/*.sh" in commonScript,
            "Shared repository verification must run every shell contract test",
        )
        assertTrue(
            "verifyArchitecture" in verifyScript,
            "Shared full verification must include architecture verification in the same Gradle invocation",
        )
        assertFalse(
            "./scripts/check-module-dependencies.sh" in verifyScript,
            "Shared full verification must not spawn a second Gradle build for architecture",
        )
        assertFalse(
            "--no-daemon" in verifyScript,
            "Shared full verification must allow Gradle daemon reuse",
        )
        assertFalse(
            "--no-daemon" in fastVerifyScript,
            "Shared fast verification must allow Gradle daemon reuse",
        )

        val configuredTasks = verifyScript
            .lineSequence()
            .map(String::trim)
            .map { line -> line.removeSuffix("\\").trim() }
            .filter(String::isNotEmpty)
            .toSet()

        listOf(
            "verifyArchitecture",
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

        listOf(
            "verifyArchitecture",
            ":build-logic:test",
            "test",
            "testDebugUnitTest",
            "detekt",
        ).forEach { expected ->
            assertTrue(
                expected in fastVerifyScript,
                "Fast verification is missing task: $expected",
            )
        }
        assertFalse("lintDebug" in fastVerifyScript)
        assertFalse(":app:assembleDebug" in fastVerifyScript)
    }

    @Test
    fun sharedVerificationRunsRepositoryGatesBeforeGradleWork() {
        val commonScript = File(root, "scripts/verification-common.sh").readText()
        val verifyScript = File(root, "scripts/verify.sh").readText()
        val fastVerifyScript = File(root, "scripts/verify-fast.sh").readText()

        listOf(
            "./scripts/verify-source-layout.sh",
            "./scripts/structural-review-report.sh",
            "./scripts/verify-current-architecture.sh",
        ).forEach { gate ->
            assertTrue(gate in commonScript, "Shared repository verification must invoke $gate")
        }

        listOf(verifyScript, fastVerifyScript).forEach { entryPoint ->
            assertTrue("run_repository_static_gates" in entryPoint)
            assertTrue(
                entryPoint.indexOf("run_repository_static_gates") <
                    entryPoint.indexOf("ROOM_SCHEMA_FINGERPRINT"),
                "Repository/static gates must run before Room fingerprinting",
            )
        }
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
            "./scripts/verify-fast.sh",
            "./scripts/verify.sh",
            "./scripts/checkpoints/app-shell.sh",
            "API 26",
            "API 37",
            "config/architecture/module-boundaries.json",
            ":plugins:runtime",
        ).forEach { expected ->
            assertTrue(
                expected in readme,
                "README bootstrap is missing: $expected",
            )
        }
    }
}
