plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.storage.files"
}

dependencies {
    implementation(project(":downloads"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
