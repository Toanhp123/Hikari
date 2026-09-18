import app.universalmedia.buildlogic.VerifyArchitectureTask
import app.universalmedia.buildlogic.VerifySecurityBaselineTask

val architecturePolicy =
    mapOf(
        ":core:model" to emptyList(),
        ":core:domain" to listOf(":core:model"),
        ":core:designsystem" to emptyList(),
        ":data" to listOf(":core:model", ":core:domain"),
        ":storage:local" to listOf(":core:model", ":core:domain"),
        ":ingestion:local" to listOf(":core:model", ":core:domain"),
        ":source:api" to listOf(":core:model", ":core:domain"),
        ":source:local" to listOf(":core:model", ":core:domain", ":source:api"),
        ":playback:api" to listOf(":core:model", ":source:api"),
        ":playback:media3" to listOf(":core:model", ":source:api", ":playback:api"),
        ":reader:image" to listOf(":core:model", ":core:domain", ":source:api"),
        ":reader:publication" to listOf(":core:model", ":core:domain", ":source:api"),
        ":feature:library" to listOf(":core:model", ":core:domain", ":core:designsystem"),
        ":feature:settings" to listOf(":core:designsystem"),
        ":app" to
            listOf(
                ":core:model",
                ":core:domain",
                ":data",
                ":storage:local",
                ":ingestion:local",
                ":source:api",
                ":source:local",
                ":playback:api",
                ":playback:media3",
                ":reader:image",
                ":reader:publication",
                ":feature:library",
                ":feature:settings",
                ":core:designsystem",
            ),
        ":benchmark" to listOf(":app"),
    )

val verifyArchitecture =
    tasks.register<VerifyArchitectureTask>("verifyArchitecture") {
        group = "verification"
        description = "Verifies the bootstrap module dependency allow-list."
        allowedProjectDependencies.set(architecturePolicy)
        dependencyPattern.set("""project\("(:[^"]+)"\)""")
    }

subprojects.forEach { module ->
    val buildFile = module.layout.projectDirectory.file("build.gradle.kts")
    if (buildFile.asFile.isFile) {
        verifyArchitecture.configure {
            moduleBuildScripts.put(module.path, providers.fileContents(buildFile).asText)
        }
    }
}

val verifySecurityBaseline =
    tasks.register<VerifySecurityBaselineTask>("verifySecurityBaseline") {
        group = "verification"
        description = "Checks bootstrap security invariants that can be verified statically."
        forbiddenPermissions.set(
            listOf(
                "MANAGE_EXTERNAL_STORAGE",
                "READ_EXTERNAL_STORAGE",
                "WRITE_EXTERNAL_STORAGE",
                "android.permission.INTERNET",
            ),
        )
        appManifestKey.set(":app")
    }

subprojects.forEach { module ->
    val manifestFile = module.layout.projectDirectory.file("src/main/AndroidManifest.xml")
    if (manifestFile.asFile.isFile) {
        verifySecurityBaseline.configure {
            manifestContents.put(module.path, providers.fileContents(manifestFile).asText)
        }
    }
}
