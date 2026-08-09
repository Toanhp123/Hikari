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

    sourceSets {
        getByName("androidTest") {
            assets.directories.add(
                "$projectDir/schemas",
            )
        }
    }
}

dependencies {
    api(project(":core:model"))

    api(project(":core:common"))

    implementation(project(":plugins:runtime"))
    api(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))

    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
