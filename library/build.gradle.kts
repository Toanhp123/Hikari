plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.library"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(project(":plugins:api"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
