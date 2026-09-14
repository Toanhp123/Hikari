plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.library.runtime"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":library:domain"))
    implementation(project(":library:storage"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
