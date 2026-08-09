plugins {
    id("openstory.android.library")
    id("openstory.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.openstory.storage.room"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.room.testing)
}
