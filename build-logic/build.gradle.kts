plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
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

        register("hilt") {
            id = "openstory.hilt"
            implementationClass =
                "app.openstory.build.HiltConventionPlugin"
        }

        register("kotlinJvm") {
            id = "openstory.kotlin.jvm"
            implementationClass =
                "app.openstory.build.KotlinJvmConventionPlugin"
        }
    }
}
