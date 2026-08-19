package app.openstory.build.packaging

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class CanonicalPluginPackageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainScriptFile: RegularFileProperty

    @get:OutputFile
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun packagePlugin() {
        val output = archiveFile.get().asFile
        output.parentFile.mkdirs()

        ZipOutputStream(output.outputStream().buffered()).use { archive ->
            writeCanonicalTextEntry(
                archive = archive,
                name = MANIFEST_ENTRY_NAME,
                source = manifestFile.get().asFile,
            )
            writeCanonicalTextEntry(
                archive = archive,
                name = MAIN_SCRIPT_ENTRY_NAME,
                source = mainScriptFile.get().asFile,
            )
        }
    }

    private fun writeCanonicalTextEntry(
        archive: ZipOutputStream,
        name: String,
        source: File,
    ) {
        val bytes = source.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .toByteArray(Charsets.UTF_8)

        archive.putNextEntry(ZipEntry(name).apply { time = 0L })
        archive.write(bytes)
        archive.closeEntry()
    }

    private companion object {
        const val MANIFEST_ENTRY_NAME = "manifest.json"
        const val MAIN_SCRIPT_ENTRY_NAME = "main.js"
    }
}
