import dev.detekt.gradle.Detekt

plugins {
    id("openstory.architecture")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = true
    config.setFrom(
        rootProject.file("config/detekt/detekt.yml"),
    )
    source.setFrom(
        rootProject.fileTree(rootDir) {
            include("**/*.kt")
            exclude(
                "**/build/**",
                "**/.gradle/**",
                "**/.idea/**",
                "**/src/test/**",
                "**/src/androidTest/**",
            )
        },
    )
    parallel = true
    ignoreFailures = false
    basePath.set(rootDir)
}

tasks.withType<Detekt>().configureEach {
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
        markdown.required.set(true)
    }
}
