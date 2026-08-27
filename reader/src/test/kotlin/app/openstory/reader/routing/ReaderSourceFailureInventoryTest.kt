package app.openstory.reader.routing

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSourceFailureInventoryTest {
    @Test
    fun everyCurrentContentChapterRuntimeAndSanitizerCodeHasAnExactClassifierEntry() {
        val root = repositoryRoot()
        val relativeFiles = listOf(
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/BundledPluginProvisioner.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PackageVerifier.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PluginInstaller.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/TransactionalPluginPackageStorage.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/PluginOperationRunner.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/AndroidxJavaScriptEngine.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/execution/InvocationScriptBuilder.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/CapabilityBroker.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/BoundedResponseReader.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProvider.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/html/HtmlCapability.kt",
            "plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/log/SafePluginLogger.kt",
            "reader/src/main/kotlin/app/openstory/reader/content/PluginReaderDocumentSource.kt",
            "reader/src/main/kotlin/app/openstory/reader/document/ReaderDocumentSanitizer.kt",
        )
        val literal = Regex("\\\"((?:plugin|reader)\\.[a-z0-9._-]+)\\\"")
        val discovered = relativeFiles.flatMap { relative ->
            literal.findAll(root.resolve(relative).readText()).map { it.groupValues[1] }.toList()
        }.toSet() + "protocol.invalid_payload" + "reader.source_unavailable"

        assertEquals(
            discovered,
            ReaderSourceFailureClassifier.knownCodes,
            "Update ReaderSourceFailureClassifier with an exact semantic entry whenever the Reader-reachable " +
                "CONTENT_CHAPTER runtime/sanitizer surface changes.",
        )
    }

    private fun repositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        repeat(6) {
            if (current.resolve("settings.gradle.kts").exists()) return current
            current = current.parent ?: return@repeat
        }
        error("Unable to locate repository root from ${System.getProperty("user.dir")}")
    }
}
