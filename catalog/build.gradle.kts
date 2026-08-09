plugins {
    id("openstory.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.openstory.catalog"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":plugins:api"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
