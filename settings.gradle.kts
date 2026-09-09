import org.gradle.api.JavaVersion

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

val currentJavaVersion = JavaVersion.current()

require(currentJavaVersion == JavaVersion.VERSION_17) {
    "Hikari requires JDK 17. " +
        "Current JVM: ${System.getProperty("java.version")} " +
        "(${System.getProperty("java.vendor")})"
}

rootProject.name = "Hikari"

include(":app")
include(":core:common")
include(":catalog:model")
include(":catalog:engine")
include(":catalog:domain")
include(":catalog:storage")
include(":catalog:runtime")
include(":feature:catalog")
include(":reader:engine")
include(":plugins:api")
include(":benchmark")

// Nested retained modules require parent Gradle projects, but their V1 builds are inactive.
project(":catalog").buildFileName = "inactive-parent.gradle.kts"
project(":feature").buildFileName = "inactive-parent.gradle.kts"
project(":reader").buildFileName = "inactive-parent.gradle.kts"
