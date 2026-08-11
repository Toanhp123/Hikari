plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.downloads"
}

dependencies {
    api(project(":core:common"))
    implementation(project(":chapters"))
    implementation(project(":reader"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
