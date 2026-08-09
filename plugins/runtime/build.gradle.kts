plugins {
    id("openstory.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.openstory.plugins.runtime"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":plugins:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.client)
    implementation(libs.jsoup)
    implementation(libs.androidx.javascriptengine)
    implementation(libs.bouncy.castle.provider)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
