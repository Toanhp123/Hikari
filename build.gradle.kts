import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    id("universalmedia.root-verification")
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        lineEndings = LineEnding.UNIX
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        lineEndings = LineEnding.UNIX
    }
    format("misc") {
        target("*.md", ".gitignore", ".gitattributes", "**/*.xml", "**/*.yml", "**/*.yaml")
        targetExclude(
            "**/build/**",
            ".idea/**",
            "**/.idea/**",
            ".gradle/**",
            "**/.gradle/**",
            "docs/foundation/**",
        )
        trimTrailingWhitespace()
        endWithNewline()
        lineEndings = LineEnding.UNIX
    }
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Fast bootstrap gate for formatting, architecture, JVM tests, lint, and debug assembly."
    dependsOn(
        "spotlessCheck",
        "verifyArchitecture",
        "verifySecurityBaseline",
        ":core:model:test",
        ":core:domain:test",
        ":source:api:test",
        ":playback:api:test",
        ":app:lintDebug",
        ":app:assembleDebug",
    )
}

tasks.register("verifyRelease") {
    group = "verification"
    description = "Release-like bootstrap build gate."
    dependsOn("verifyFast", ":app:assembleRelease", ":benchmark:assembleBenchmark")
}
