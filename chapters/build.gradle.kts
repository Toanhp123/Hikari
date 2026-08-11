plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.chapters"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":library"))
    implementation(project(":plugins:api"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
