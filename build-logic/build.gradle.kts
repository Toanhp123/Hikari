plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.room.gradle.plugin)
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

        register("androidApplication") {
            id = "openstory.android.application"
            implementationClass =
                "app.openstory.build.AndroidApplicationConventionPlugin"
        }

        register("androidLibrary") {
            id = "openstory.android.library"
            implementationClass =
                "app.openstory.build.AndroidLibraryConventionPlugin"
        }

        register("compose") {
            id = "openstory.compose"
            implementationClass =
                "app.openstory.build.ComposeConventionPlugin"
        }
        register("hilt") {
            id = "openstory.hilt"
            implementationClass =
                "app.openstory.build.HiltConventionPlugin"
        }

        register("room") {
            id = "openstory.room"
            implementationClass =
                "app.openstory.build.RoomConventionPlugin"
        }
        register("kotlinJvm") {
            id = "openstory.kotlin.jvm"
            implementationClass =
                "app.openstory.build.KotlinJvmConventionPlugin"
        }
    }
}
