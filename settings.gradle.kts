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
include(":core:model")
include(":test:fixtures")
include(":core:database")
