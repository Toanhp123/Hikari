package app.openstory.build.architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyAppStructureTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @get:Internal
    abstract val appDirectory: DirectoryProperty

    @TaskAction
    fun verifyStructure() {
        val canonicalAppDirectory = appDirectory.get().asFile.canonicalFile
        val sources = productionSources.files
            .asSequence()
            .filter { file ->
                file.isFile && file.extension.lowercase() in SOURCE_EXTENSIONS
            }
            .sortedBy { file -> file.canonicalPath }
            .associateTo(linkedMapOf()) { file ->
                val canonicalFile = file.canonicalFile
                canonicalFile.relativeTo(canonicalAppDirectory).invariantSeparatorsPath to
                    canonicalFile.readText()
            }
        val policy = FoundationPolicyLoader.parse(
            policyFile.get().asFile.readText(),
        )
        val violations = AppStructuralVerifier.verify(
            sources = sources,
            policy = policy,
        )

        if (violations.isNotEmpty()) {
            val report = buildString {
                appendLine("App structural verification failed:")
                violations.forEach { violation ->
                    append("- ")
                    append(violation.code)
                    append(": ")
                    appendLine(violation.detail)
                }
            }
            throw GradleException(report.trimEnd())
        }

        logger.lifecycle(
            "V2 app structure verified for ${sources.size} production files.",
        )
    }

    private companion object {
        val SOURCE_EXTENSIONS = setOf("kt", "java")
    }
}
