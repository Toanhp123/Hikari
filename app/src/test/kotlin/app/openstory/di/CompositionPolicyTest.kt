package app.openstory.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositionPolicyTest {
    private val root = File("..").canonicalFile

    @Test
    fun baselineModulesInstallInSingletonComponentWithoutViewModelFactories() {
        listOf("StorageModule.kt", "CatalogModule.kt", "PluginRuntimeModule.kt").forEach { name ->
            val source = File(root, "app/src/main/kotlin/app/openstory/di/$name")
            assertTrue(source.isFile, "Missing Hilt composition module: $name")
            val text = source.readText()
            assertTrue("@Module" in text, "$name must be a Hilt module")
            assertTrue(
                "@InstallIn(SingletonComponent::class)" in text,
                "$name must install in SingletonComponent",
            )
            assertFalse("ViewModelProvider.Factory" in text, "$name must not provide ViewModel factories")
            assertFalse(Regex("fun\\s+\\w+\\([^)]*\\)\\s*:\\s*\\w*ViewModel").containsMatchIn(text))
        }
    }

    @Test
    fun catalogAndPresentationObeyPlatformSchedulingBoundary() {
        val catalogSources = kotlinSources(File(root, "catalog/src/main"))
        assertForbidden(catalogSources, "android.content.Context", "AppDispatchers")

        val viewModels = kotlinSources(File(root, "feature/catalog"))
            .filter { it.name.endsWith("ViewModel.kt") }
        assertForbidden(
            viewModels,
            "android.content.Context",
            "AppDispatchers",
            "CoroutineScope(",
            "Dispatchers.",
        )
    }

    private fun assertForbidden(files: List<File>, vararg symbols: String) {
        files.forEach { file ->
            val source = file.readText()
            symbols.forEach { symbol ->
                assertFalse(symbol in source, "${file.relativeTo(root)} must not reference $symbol")
            }
        }
    }

    private fun kotlinSources(directory: File): List<File> = directory.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
}
