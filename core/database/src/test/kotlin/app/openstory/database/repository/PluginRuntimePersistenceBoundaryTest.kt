package app.openstory.database.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginRuntimePersistenceBoundaryTest {
    @Test
    fun roomRuntimeAdaptersImportOnlyPersistenceSpi() {
        val root = File(System.getProperty("user.dir"))
        val sourceRoot = generateSequence(root) { directory -> directory.parentFile }
            .map { candidate -> File(candidate, "core/database/src/main/kotlin/app/openstory/database/repository") }
            .first { directory -> directory.isDirectory }
        val source = listOf("RoomPluginStateStore.kt", "RoomPluginDiagnosticsSink.kt")
            .joinToString("\n") { name -> File(sourceRoot, name).readText() }

        assertTrue("app.openstory.plugins.runtime.persistence." in source)
        assertFalse("app.openstory.plugins.runtime.execution." in source)
        assertFalse("app.openstory.plugins.runtime.install." in source)
        assertFalse("app.openstory.plugins.runtime.capabilities." in source)
    }
}
