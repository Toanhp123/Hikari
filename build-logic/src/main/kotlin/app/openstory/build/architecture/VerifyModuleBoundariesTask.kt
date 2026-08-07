package app.openstory.build.architecture

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyModuleBoundariesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFile: RegularFileProperty

    @get:Input
    abstract val moduleDirectories: MapProperty<String, String>

    @get:Input
    abstract val modulePlatforms: MapProperty<String, String>

    @get:Input
    abstract val productionDependencies: MapProperty<String, String>

    @get:Input
    abstract val testDependencies: MapProperty<String, String>

    @get:Input
    abstract val unknownProjectDependencyConfigurations: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @TaskAction
    fun verifyBoundaries() {
        val policy = ModuleBoundaryPolicyLoader.load(
            policyFile.get().asFile,
        )
        val root = policyFile.get().asFile
            .parentFile
            .parentFile
            .parentFile
            .canonicalFile

        val actualModules = moduleDirectories.get().mapValues { (module, relativePath) ->
            ActualModule(
                path = relativePath,
                platform = ModulePlatform.fromPolicyValue(
                    modulePlatforms.get().getValue(module),
                ),
                productionDependencies = decodeSet(
                    productionDependencies.get()[module],
                ),
                testDependencies = decodeSet(
                    testDependencies.get()[module],
                ),
                unknownProjectDependencyConfigurations = decodeUnknownConfigurations(
                    unknownProjectDependencyConfigurations.get()[module],
                ),
                productionImports = importsForModule(
                    root = root,
                    modulePath = relativePath,
                ),
            )
        }

        val violations = ModuleBoundaryVerifier.verify(
            policy = policy,
            actualModules = actualModules,
        )

        if (violations.isNotEmpty()) {
            val report = buildString {
                appendLine("Architecture verification failed:")
                violations.forEach { violation ->
                    append("- ")
                    append(violation.code)
                    violation.module?.let { module ->
                        append(" [")
                        append(module)
                        append(']')
                    }
                    append(": ")
                    appendLine(violation.detail)
                }
                appendLine()
                appendLine(
                    "Update config/architecture/module-boundaries.json only " +
                        "when the dependency is architecturally approved.",
                )
            }

            throw GradleException(report.trimEnd())
        }

        logger.lifecycle(
            "Module architecture verified for ${actualModules.size} modules.",
        )
    }

    private fun importsForModule(
        root: File,
        modulePath: String,
    ): Set<String> {
        val sourceRoot = File(root, "$modulePath/src/main").canonicalFile
        if (!sourceRoot.isDirectory) {
            return emptySet()
        }

        val importPattern = Regex(
            pattern = """^\s*import\s+([A-Za-z_][A-Za-z0-9_.*]*)""",
        )

        return productionSources.files
            .asSequence()
            .filter { file ->
                file.isFile && file.canonicalFile.toPath().startsWith(sourceRoot.toPath())
            }
            .flatMap { file ->
                file.useLines { lines ->
                    lines.mapNotNull { line ->
                        importPattern.find(line)?.groupValues?.get(1)
                    }.toList().asSequence()
                }
            }
            .toSortedSet()
    }

    private fun decodeSet(encoded: String?): Set<String> = encoded
        .orEmpty()
        .split(UNIT_SEPARATOR)
        .filter(String::isNotBlank)
        .toCollection(linkedSetOf())

    private fun decodeUnknownConfigurations(
        encoded: String?,
    ): Map<String, Set<String>> = encoded
        .orEmpty()
        .split(RECORD_SEPARATOR)
        .filter(String::isNotBlank)
        .associate { record ->
            val separatorIndex = record.indexOf(CONFIGURATION_SEPARATOR)
            require(separatorIndex > 0) {
                "module_policy.invalid_encoded_configuration: $record"
            }
            val configuration = record.substring(0, separatorIndex)
            val dependencies = decodeSet(
                record.substring(separatorIndex + 1),
            )
            configuration to dependencies
        }

    companion object {
        const val UNIT_SEPARATOR: String = "\u001F"
        const val RECORD_SEPARATOR: String = "\u001E"
        const val CONFIGURATION_SEPARATOR: Char = '\u001D'
    }
}
