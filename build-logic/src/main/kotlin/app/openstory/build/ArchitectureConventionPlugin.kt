package app.openstory.build

import app.openstory.build.architecture.VerifyApplicationIdentityTask
import app.openstory.build.architecture.ModulePlatform
import app.openstory.build.architecture.VerifyModuleBoundariesTask
import app.openstory.build.architecture.VerifyProductionPackageStructureTask
import app.openstory.build.architecture.VerifyStep3BuildSurfaceTask
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
        val packageStructureTask = registerPackageStructureTask()
        val step3BuildSurfaceTask = registerStep3BuildSurfaceTask()
        val step3FastModules = tasks.register("verifyStep3FastModules") {
            group = "verification"
            description = "Runs focused host gates for every live Step 3 production module."
        }
        val step3FullModules = tasks.register("verifyStep3FullModules") {
            group = "verification"
            description = "Runs full host gates for every live Step 3 production module."
        }

        tasks.register("verifyArchitecture") {
            group = "verification"
            description =
                "Runs module-boundary, identity, and V2 foundation verification."
            dependsOn(
                boundaryTask,
                identityTask,
                ":app:verifyFoundation",
                packageStructureTask,
                step3BuildSurfaceTask,
            )
        }

        gradle.projectsEvaluated {
            configureBoundaryInputs(boundaryTask)
            configureIdentityInputs(identityTask)
            configurePackageStructureInputs(packageStructureTask)
            configureStep3ModuleAggregates(step3FastModules, step3FullModules)
        }
    }

    private fun Project.registerPackageStructureTask():
        TaskProvider<VerifyProductionPackageStructureTask> =
        tasks.register<VerifyProductionPackageStructureTask>(
            "verifyProductionPackageStructure",
        ) {
            group = "verification"
            description = "Rejects package cycles in live Step 3 production modules."
            rootDirectory.set(layout.projectDirectory)
            productionSources.from(
                fileTree(rootDir) {
                    include("**/src/main/**/*.kt")
                    include("**/src/main/**/*.java")
                    exclude("**/build/**")
                },
            )
        }

    private fun Project.registerStep3BuildSurfaceTask():
        TaskProvider<VerifyStep3BuildSurfaceTask> =
        tasks.register<VerifyStep3BuildSurfaceTask>(
            "verifyStep3BuildSurface",
        ) {
            group = "verification"
            description = "Verifies live Step 3 framework, source-set, and app ownership."
            policyFile.set(
                layout.projectDirectory.file(
                    "config/architecture/module-boundaries.json",
                ),
            )
            rootDirectory.set(layout.projectDirectory)
            surfaceFiles.from(
                file("settings.gradle.kts"),
                file("gradle/libs.versions.toml"),
                fileTree(rootDir) {
                    include("app/build.gradle.kts")
                    include("core/*/build.gradle.kts")
                    include("catalog/*/build.gradle.kts")
                    include("library/*/build.gradle.kts")
                    include("reading/*/build.gradle.kts")
                    include("settings/*/build.gradle.kts")
                    include("sources/*/build.gradle.kts")
                    include("feature/*/build.gradle.kts")
                    include("app/src/main/**")
                    include("core/*/src/main/**")
                    include("catalog/*/src/main/**")
                    include("library/*/src/main/**")
                    include("reading/*/src/main/**")
                    include("settings/*/src/main/**")
                    include("sources/*/src/main/**")
                    include("feature/*/src/main/**")
                    include("feature/*/src/debug/**")
                    include("feature/*/src/benchmarkRelease/**")
                    include("feature/*/src/nonMinifiedRelease/**")
                    include("feature/*/src/release/**")
                    include(
                        "build-logic/src/main/kotlin/app/openstory/build/" +
                            "AndroidLibraryConventionPlugin.kt",
                    )
                    exclude("**/build/**")
                },
            )
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
                snapshots.associate { it.module to it.production.encodeSet() },
            )
            testDependencies.set(
                snapshots.associate { it.module to it.test.encodeSet() },
            )
            unknownProjectDependencyConfigurations.set(
                snapshots.associate { it.module to it.unknown.encodeUnknown() },
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

    private fun Project.configurePackageStructureInputs(
        task: TaskProvider<VerifyProductionPackageStructureTask>,
    ) {
        val snapshots = rootProject.subprojects
            .filter { project -> project.buildFile.isFile }
            .sortedBy(Project::getPath)
            .map { project -> project.snapshotArchitecture() }
        val productionModules = productionReachableModules(
            snapshots.associate { snapshot -> snapshot.module to snapshot.production },
        )
        val moduleDirectories = snapshots
            .filter { snapshot -> snapshot.module in productionModules }
            .filterNot { snapshot -> snapshot.platform == ModulePlatform.ANDROID_TEST }
            .associate { snapshot ->
                snapshot.module to snapshot.directory
            }
        task.configure {
            this.moduleDirectories.set(moduleDirectories)
        }
    }

    private fun Project.configureStep3ModuleAggregates(
        fastTask: TaskProvider<*>,
        fullTask: TaskProvider<*>,
    ) {
        val modules = rootProject.subprojects
            .filter { project -> project.buildFile.isFile }
            .sortedBy(Project::getPath)
        fastTask.configure {
            dependsOn(
                modules.flatMap { project ->
                    step3ModuleVerificationTasks(
                        project.path,
                        project.appliedPlatform(),
                        full = false,
                    )
                },
            )
        }
        fullTask.configure {
            dependsOn(
                modules.flatMap { project ->
                    step3ModuleVerificationTasks(
                        project.path,
                        project.appliedPlatform(),
                        full = true,
                    )
                },
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
            production = productionDependencies,
            test = testDependencies,
            unknown = unknownDependencies,
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
        val production: Set<String>,
        val test: Set<String>,
        val unknown: Map<String, Set<String>>,
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

internal fun step3ModuleVerificationTasks(
    module: String,
    platform: ModulePlatform,
    full: Boolean,
): Set<String> = when (platform) {
    ModulePlatform.JVM -> setOf("$module:test")
    ModulePlatform.ANDROID_APPLICATION,
    ModulePlatform.ANDROID_LIBRARY,
    -> buildSet {
        add("$module:testDebugUnitTest")
        add("$module:assembleDebug")
        if (full) {
            add("$module:assembleRelease")
            add("$module:lint")
        }
    }
    ModulePlatform.ANDROID_TEST -> emptySet()
}

internal fun productionReachableModules(
    productionDependencies: Map<String, Set<String>>,
    rootModule: String = ":app",
): Set<String> {
    val reachable = linkedSetOf<String>()

    fun visit(module: String) {
        if (!reachable.add(module)) return
        productionDependencies[module].orEmpty().sorted().forEach(::visit)
    }

    visit(rootModule)
    return reachable
}


internal fun interModuleDependencyPaths(
    ownerPath: String,
    dependencyPaths: Iterable<String>,
): Set<String> = dependencyPaths
    .asSequence()
    .filterNot { it == ownerPath }
    .toSortedSet()
