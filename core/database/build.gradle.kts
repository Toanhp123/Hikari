plugins {
    id("openstory.android.library")
    id("openstory.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.openstory.database"

    defaultConfig {
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":core:model"))

    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
