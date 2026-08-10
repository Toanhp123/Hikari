plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.library"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
