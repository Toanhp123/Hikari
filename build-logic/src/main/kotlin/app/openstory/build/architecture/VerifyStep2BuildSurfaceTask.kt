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

abstract class VerifyStep2BuildSurfaceTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val surfaceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun verifySurface() {
        val violations = Step2BuildSurfaceVerifier.verify(
            rootDirectory = rootDirectory.get().asFile,
            policy = ModuleBoundaryPolicyLoader.load(policyFile.get().asFile),
        )
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Step 2 build surface verification failed:")
                    violations.forEach { violation ->
                        append("- ${violation.code}")
                        violation.module?.let { append(" [$it]") }
                        appendLine(": ${violation.detail}")
                    }
                }.trimEnd(),
            )
        }
        logger.lifecycle("Step 2 build surface verified.")
    }
}
