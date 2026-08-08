plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.plugin.host"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    api(project(":core:plugin-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncy.castle.provider)
    implementation(libs.jsoup)
    implementation(libs.androidx.javascriptengine)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test-junit"))
}
