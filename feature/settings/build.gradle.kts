plugins {
    id("openstory.android.library")
    id("openstory.hilt")
}

android {
    namespace = "app.openstory.settings.ui"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
