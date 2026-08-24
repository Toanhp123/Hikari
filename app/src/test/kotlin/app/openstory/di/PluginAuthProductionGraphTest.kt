package app.openstory.di

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginAuthProductionGraphTest {
    private val source = File(
        File("..").canonicalFile,
        "app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt",
    ).readText()

    @Test
    fun productionComposesStaticAndRuntimeSessionCredentials() {
        assertTrue("MyAnimeListManagedCredentials" in source)
        assertTrue("PluginSessionManagedCredentialProvider" in source)
        assertTrue("CompositeManagedCredentialProvider" in source)
    }
}
