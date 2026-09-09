package app.openstory

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellContractTest {
    @Test
    fun applicationOnCreateIsTraceOnly() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/HikariApplication.kt",
        ).readText()

        assertTrue("startupTraceSection" in source)
        assertTrue("super.onCreate()" in source)

        listOf(
            "WorkManager",
            "Reader",
            "Catalog",
            "PluginRuntime",
            "DataStore",
            "CoroutineScope",
            "launch {",
        ).forEach { forbidden ->
            assertFalse("Forbidden Application startup work: $forbidden", forbidden in source)
        }
    }

    @Test
    fun mainActivityHasNoDomainOrSchedulerOwnership() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/MainActivity.kt",
        ).readText()
        listOf(
            "@AndroidEntryPoint",
            "WorkManager",
            "NotificationIntentParser",
            "Reader",
            "Catalog",
            "PluginRuntime",
            "lifecycleScope",
        ).forEach { forbidden ->
            assertFalse("Forbidden MainActivity ownership: $forbidden", forbidden in source)
        }
    }

    @Test
    fun startupGateDoesNotUseNavigationOrAnimationFramework() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt",
        ).readText()

        listOf(
            "NavDisplay",
            "NavController",
            "AnimatedContent",
            "Crossfade",
            "rememberNavBackStack",
        ).forEach { forbidden ->
            assertFalse(forbidden in source)
        }
    }

    @Test
    fun readyDestinationUsesOnlyTheNarrowCatalogEntryPoint() {
        val source = rootFile(
            "app/src/main/kotlin/app/openstory/startup/ui/StartupGate.kt",
        ).readText()

        assertTrue("import app.openstory.catalog.feature.CatalogEntryPoint" in source)
        assertTrue("AppLaunchState.Ready ->" in source)
        assertTrue("firstFrameReached" in source)
        assertTrue("CatalogEntryPoint()" in source)
        assertTrue("launchState == AppLaunchState.Ready && firstFrameReached" in source)
        listOf(
            "CatalogCapabilitySession",
            "CatalogRuntimeFactory",
            "CatalogSourceBinding",
            "CatalogStorageFactory",
            "ImageLoader",
            "Room",
            "VariantCatalogBinding",
        ).forEach { forbidden ->
            assertFalse("App shell owns Catalog implementation detail: $forbidden", forbidden in source)
        }
    }

    @Test
    fun catalogEntryPointIsAFeatureOwnedNoArgumentComposableBoundary() {
        val entryPoint = repositoryFile(
            "feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogEntryPoint.kt",
        )

        assertTrue("Catalog entry point is missing", entryPoint.isFile)
        val source = entryPoint.readText()
        assertTrue("@Composable\nfun CatalogEntryPoint()" in source)
        assertFalse("Context" in source.substringBefore("fun CatalogEntryPoint"))
    }

    @Test
    fun returningBenchmarkWaitsForTheCatalogDestination() {
        val driver = rootFile(
            "benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt",
        ).readText()
        val macrobenchmark = rootFile(
            "benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt",
        ).readText()
        val profile = rootFile(
            "benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt",
        ).readText()

        assertTrue("DISCOVER_TAG = \"catalog-discover\"" in driver)
        assertTrue("startHikariAndWait(DISCOVER_TAG)" in macrobenchmark)
        assertTrue("startHikariAndWait(DISCOVER_TAG)" in profile)
        val benchmarkSources = driver + macrobenchmark + profile
        assertFalse("HOME_TAG" in benchmarkSources)
    }

    private fun rootFile(relativePath: String): File {
        return repositoryFile(relativePath).also { file ->
            check(file.isFile) { "Required repository file is missing: ${file.path}" }
        }
    }

    private fun repositoryFile(relativePath: String): File {
        val userDirectory = checkNotNull(System.getProperty("user.dir"))
        var current = File(userDirectory).canonicalFile
        while (!File(current, "settings.gradle.kts").isFile) {
            current = current.parentFile
                ?: error("Repository root not found from $userDirectory")
        }
        return File(current, relativePath)
    }
}
