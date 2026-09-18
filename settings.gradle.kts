pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UniversalMedia"

include(
    ":app",
    ":core:model",
    ":core:domain",
    ":core:designsystem",
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
    ":benchmark",
)
