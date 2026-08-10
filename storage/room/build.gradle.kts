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

    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
