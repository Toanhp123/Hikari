package app.openstory.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Wave10ProductionGraphTest {
    private val root = File("..").canonicalFile

    @Test
    fun bundledPluginIdentityComesFromRealManifests() {
        val manifestIds = File(root, "bundled-plugins").walkTopDown()
            .filter { it.isFile && it.name == "manifest.json" }
            .mapNotNull { manifest ->
                Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                    .find(manifest.readText())
                    ?.groupValues
                    ?.get(1)
            }
            .toSet()
        assertTrue("org.openstory.content.mangadex" in manifestIds)
        assertTrue("org.openstory.catalog.myanimelist" in manifestIds)

        val production = kotlinSources(File(root, "app/src/main"))
            .joinToString("\n", transform = File::readText)
        assertFalse("plugin.mangadex" in production)
        assertFalse("plugin.myanimelist" in production)
    }

    @Test
    fun managedCredentialsUseCollisionCheckingComposition() {
        val source = File(root, "app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt").readText()
        assertTrue("CompositeManagedCredentialProvider" in source)
        assertTrue("MyAnimeListManagedCredentials" in source)
        assertTrue("PluginSessionManagedCredentialProvider" in source)
    }

    private fun kotlinSources(directory: File): Sequence<File> = directory.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
}
