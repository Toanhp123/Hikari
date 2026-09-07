package app.openstory.build.architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyMergedManifestStartupTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verifyManifest() {
        val policy = FoundationPolicyLoader.parse(
            policyFile.get().asFile.readText(),
        )
        val violations = MergedManifestStartupVerifier.verify(
            xml = mergedManifest.get().asFile.readText(),
            policy = policy,
        )

        if (violations.isNotEmpty()) {
            val report = buildString {
                appendLine("Merged manifest startup verification failed:")
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
            "V2 merged manifest startup verified for ${mergedManifest.get().asFile.name}.",
        )
    }
}
