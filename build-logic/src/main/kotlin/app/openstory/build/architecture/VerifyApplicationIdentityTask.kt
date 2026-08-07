package app.openstory.build.architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyApplicationIdentityTask : DefaultTask() {
    @get:Input
    abstract val actualNamespace: Property<String>

    @get:Input
    abstract val actualApplicationId: Property<String>

    @get:Input
    abstract val expectedIdentity: Property<String>

    @get:Input
    abstract val forbiddenLegacyToken: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionIdentityFiles: ConfigurableFileCollection

    @TaskAction
    fun verifyIdentity() {
        val expected = expectedIdentity.get()
        val violations = mutableListOf<String>()

        if (actualNamespace.get() != expected) {
            violations +=
                "application_identity.namespace_mismatch: " +
                "expected=$expected;actual=${actualNamespace.get()}"
        }

        if (actualApplicationId.get() != expected) {
            violations +=
                "application_identity.application_id_mismatch: " +
                "expected=$expected;actual=${actualApplicationId.get()}"
        }

        val legacyToken = forbiddenLegacyToken.get()
        productionIdentityFiles.files
            .asSequence()
            .filter { it.isFile }
            .sortedBy { it.path }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (legacyToken in line) {
                            violations +=
                                "application_identity.legacy_token: " +
                                "${file.path}:${index + 1}"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Application identity verification failed:")
                    violations.forEach { appendLine("- $it") }
                }.trimEnd(),
            )
        }

        logger.lifecycle("Application identity verified as $expected.")
    }
}
