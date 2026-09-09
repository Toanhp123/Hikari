package app.openstory.build

import app.openstory.build.architecture.VerifyAppStructureTask
import app.openstory.build.architecture.VerifyBootSourceBoundaryTask
import app.openstory.build.architecture.VerifyMergedManifestStartupTask
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

class FoundationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val verifyFoundation = tasks.register("verifyFoundation") {
            group = "verification"
            description = "Runs all V2 foundation verification gates."
        }

        pluginManager.withPlugin("com.android.application") {
            configureFoundationVerification(verifyFoundation)
        }
    }

    private fun Project.configureFoundationVerification(
        verifyFoundation: TaskProvider<*>,
    ) {
        val policy = rootProject.layout.projectDirectory.file(
            "config/architecture/v2-foundation-policy.json",
        )
        val productionSources = fileTree("src/main") {
            include("**/*.kt")
            include("**/*.java")
        }
        val sourceBoundary = tasks.register<VerifyBootSourceBoundaryTask>(
            "verifyBootSourceBoundary",
        ) {
            group = "verification"
            description = "Verifies the V2 app source and build dependency boundary."
            policyFile.set(policy)
            appBuildScript.set(layout.projectDirectory.file("build.gradle.kts"))
            this.productionSources.from(productionSources)
        }
        val appStructure = tasks.register<VerifyAppStructureTask>(
            "verifyAppStructure",
        ) {
            group = "verification"
            description = "Verifies the V2 app structural ratchet."
            policyFile.set(policy)
            appDirectory.set(layout.projectDirectory)
            this.productionSources.from(productionSources)
        }

        verifyFoundation.configure {
            dependsOn(sourceBoundary, appStructure)
        }

        val androidComponents = extensions
            .getByType<ApplicationAndroidComponentsExtension>()
        androidComponents.onVariants(androidComponents.selector().all()) { variant ->
            val manifestVerification =
                tasks.register<VerifyMergedManifestStartupTask>(
                    foundationManifestVerificationTaskName(variant.name),
                ) {
                    group = "verification"
                    description =
                        "Verifies hidden startup in the ${variant.name} merged manifest."
                    policyFile.set(policy)
                    mergedManifest.set(
                        variant.artifacts.get(SingleArtifact.MERGED_MANIFEST),
                    )
                }
            verifyFoundation.configure {
                dependsOn(manifestVerification)
            }
        }
    }

}

internal fun foundationManifestVerificationTaskNames(
    variantNames: Set<String>,
): Set<String> = variantNames.mapTo(linkedSetOf(), ::foundationManifestVerificationTaskName)

private fun foundationManifestVerificationTaskName(variantName: String): String =
    "verify${variantName.taskSegment()}MergedManifestStartup"

private fun String.taskSegment(): String =
    replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
