package app.openstory.build

import app.openstory.build.architecture.VerifyApplicationIdentityTask
import app.openstory.build.architecture.VerifyModuleBoundariesTask
import app.openstory.build.architecture.ModulePlatform
import com.android.build.api.dsl.ApplicationExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register

class ArchitectureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        require(this == rootProject) {
            "openstory.architecture must be applied to the root project"
        }

        val boundaryTask = registerBoundaryTask()
        val identityTask = registerIdentityTask()

        tasks.register("verifyArchitecture") {
            group = "verification"
            description =
                "Runs module-boundary and application-identity verification."
            dependsOn(boundaryTask, identityTask)
        }

        gradle.projectsEvaluated {
            configureBoundaryInputs(boundaryTask)
            configureIdentityInputs(identityTask)
        }
    }

    private fun Project.registerBoundaryTask():
        TaskProvider<VerifyModuleBoundariesTask> =
        tasks.register<VerifyModuleBoundariesTask>(
            "verifyModuleBoundaries",
        ) {
            group = "verification"
            description =
                "Verifies the versioned direct project dependency policy."
            policyFile.set(
                layout.projectDirectory.file(
                    "config/architecture/module-boundaries.json",
                ),
            )
            productionSources.from(
                fileTree(rootDir) {
                    include("**/src/main/**/*.kt")
                    include("**/src/main/**/*.java")
                    exclude("**/build/**")
                },
            )
        }

    private fun Project.registerIdentityTask():
        TaskProvider<VerifyApplicationIdentityTask> =
        tasks.register<VerifyApplicationIdentityTask>(
            "verifyApplicationIdentity",
        ) {
            group = "verification"
            description =
                "Verifies the app.openstory Android application identity."
            expectedIdentity.set("app.openstory")
            forbiddenLegacyToken.set("com.example.hikari")
            productionIdentityFiles.from(
                file("app/build.gradle.kts"),
                fileTree("app/src/main") {
                    include("**/*.kt")
                    include("**/*.java")
                    include("**/*.xml")
                },
            )
        }

    private fun Project.configureBoundaryInputs(
        task: TaskProvider<VerifyModuleBoundariesTask>,
    ) {
        val snapshots = rootProject.subprojects
            .filter { it.buildFile.isFile }
            .sortedBy { it.path }
            .map { it.snapshotArchitecture() }

        task.configure {
            moduleDirectories.set(
                snapshots.associate { it.module to it.directory },
            )
            modulePlatforms.set(
                snapshots.associate { it.module to it.platform.policyValue },
            )
            productionDependencies.set(
                snapshots.associate { it.module to it.production },
            )
            testDependencies.set(
                snapshots.associate { it.module to it.test },
            )
            unknownProjectDependencyConfigurations.set(
                snapshots.associate { it.module to it.unknown },
            )
        }
    }

    private fun Project.configureIdentityInputs(
        task: TaskProvider<VerifyApplicationIdentityTask>,
    ) {
        val appProject = rootProject.project(":app")
        val android = appProject.extensions
            .findByType<ApplicationExtension>()
            ?: error(
                "application_identity.android_extension_missing: :app",
            )

        task.configure {
            actualNamespace.set(android.namespace.orEmpty())
            actualApplicationId.set(
                android.defaultConfig.applicationId.orEmpty(),
            )
        }
    }

    private fun Project.snapshotArchitecture(): ModuleSnapshot {
        val productionDependencies = linkedSetOf<String>()
        val testDependencies = linkedSetOf<String>()
        val unknownDependencies = linkedMapOf<String, MutableSet<String>>()
        val platform = appliedPlatform()

        configurations
            .sortedBy { it.name }
            .forEach { configuration ->
                val projectDependencies = interModuleDependencyPaths(
                    ownerPath = path,
                    dependencyPaths = configuration.dependencies
                        .withType(ProjectDependency::class.java)
                        .map(ProjectDependency::getPath),
                )

                when {
                    projectDependencies.isEmpty() -> Unit
                    platform == ModulePlatform.ANDROID_TEST ->
                        testDependencies += projectDependencies
                    configurationKind(configuration.name) ==
                        DependencyConfigurationKind.PRODUCTION ->
                        productionDependencies += projectDependencies
                    configurationKind(configuration.name) ==
                        DependencyConfigurationKind.TEST ->
                        testDependencies += projectDependencies
                    else -> unknownDependencies
                        .getOrPut(configuration.name, ::linkedSetOf)
                        .addAll(projectDependencies)
                }
            }

        return ModuleSnapshot(
            module = path,
            directory = rootDir.toPath()
                .relativize(projectDir.toPath())
                .toString()
                .replace(File.separatorChar, '/'),
            platform = platform,
            production = productionDependencies.encodeSet(),
            test = testDependencies.encodeSet(),
            unknown = unknownDependencies.encodeUnknown(),
        )
    }

    private fun Project.appliedPlatform(): ModulePlatform = when {
        pluginManager.hasPlugin("com.android.application") ->
            ModulePlatform.ANDROID_APPLICATION
        pluginManager.hasPlugin("com.android.library") ->
            ModulePlatform.ANDROID_LIBRARY
        pluginManager.hasPlugin("com.android.test") ->
            ModulePlatform.ANDROID_TEST
        pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ->
            ModulePlatform.JVM
        else -> error("module_policy.platform_unresolved: $path")
    }

    private fun configurationKind(
        name: String,
    ): DependencyConfigurationKind {
        val lower = name.lowercase()

        if ("test" in lower || lower == "baselineprofile") {
            return DependencyConfigurationKind.TEST
        }

        if (productionConfigurationSuffixes.any(lower::endsWith)) {
            return DependencyConfigurationKind.PRODUCTION
        }

        return DependencyConfigurationKind.UNKNOWN
    }

    private fun Set<String>.encodeSet(): String =
        sorted().joinToString(VerifyModuleBoundariesTask.UNIT_SEPARATOR)

    private fun Map<String, Set<String>>.encodeUnknown(): String = entries
        .sortedBy { it.key }
        .joinToString(VerifyModuleBoundariesTask.RECORD_SEPARATOR) {
            (configuration, dependencies) ->
            configuration +
                VerifyModuleBoundariesTask.CONFIGURATION_SEPARATOR +
                dependencies.encodeSet()
        }

    private data class ModuleSnapshot(
        val module: String,
        val directory: String,
        val platform: ModulePlatform,
        val production: String,
        val test: String,
        val unknown: String,
    )

    private enum class DependencyConfigurationKind {
        PRODUCTION,
        TEST,
        UNKNOWN,
    }

    private companion object {
        val productionConfigurationSuffixes: Set<String> = setOf(
            "api",
            "implementation",
            "compileonly",
            "runtimeonly",
        )
    }
}


internal fun interModuleDependencyPaths(
    ownerPath: String,
    dependencyPaths: Iterable<String>,
): Set<String> = dependencyPaths
    .asSequence()
    .filterNot { it == ownerPath }
    .toSortedSet()
