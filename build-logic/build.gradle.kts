plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("architecture") {
            id = "openstory.architecture"
            implementationClass =
                "app.openstory.build.ArchitectureConventionPlugin"
        }

        register("v2Foundation") {
            id = "openstory.foundation"
            implementationClass =
                "app.openstory.build.FoundationConventionPlugin"
        }

        register("androidApplication") {
            id = "openstory.android.application"
            implementationClass =
                "app.openstory.build.AndroidApplicationConventionPlugin"
        }

        register("compose") {
            id = "openstory.compose"
            implementationClass =
                "app.openstory.build.ComposeConventionPlugin"
        }

        register("androidLibrary") {
            id = "openstory.android.library"
            implementationClass =
                "app.openstory.build.AndroidLibraryConventionPlugin"
        }
        register("kotlinJvm") {
            id = "openstory.kotlin.jvm"
            implementationClass =
                "app.openstory.build.KotlinJvmConventionPlugin"
        }
    }
}
