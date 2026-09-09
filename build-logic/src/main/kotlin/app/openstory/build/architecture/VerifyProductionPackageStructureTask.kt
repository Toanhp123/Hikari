package app.openstory.build.architecture

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyProductionPackageStructureTask : DefaultTask() {
    @get:Input
    abstract val moduleDirectories: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verifyStructure() {
        val root = rootDirectory.get().asFile.canonicalFile
        val sourcesByModule = moduleDirectories.get().mapValues { (_, relativePath) ->
            val sourceRoot = File(root, "$relativePath/src/main").canonicalFile.toPath()
            productionSources.files
                .asSequence()
                .filter { file ->
                    file.isFile && file.canonicalFile.toPath().startsWith(sourceRoot)
                }
                .sortedBy(File::getCanonicalPath)
                .associate { file ->
                    file.relativeTo(root).invariantSeparatorsPath to file.readText()
                }
        }
        val violations = ProductionPackageStructureVerifier.verify(sourcesByModule)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Step 2 production package structure verification failed:")
                    violations.forEach { violation ->
                        appendLine("- ${violation.code} [${violation.module}]: ${violation.detail}")
                    }
                }.trimEnd(),
            )
        }
        logger.lifecycle("Step 2 package structure verified for ${sourcesByModule.size} modules.")
    }
}
