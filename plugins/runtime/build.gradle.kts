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
    api(project(":plugins:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.client)
    implementation(libs.jsoup)
    implementation(libs.androidx.javascriptengine)
    implementation(libs.bouncy.castle.provider)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test-junit"))
}
