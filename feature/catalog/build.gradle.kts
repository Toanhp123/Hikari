plugins {
    id("openstory.android.library")
    id("openstory.compose")
    id("openstory.hilt")
}

android {
    namespace = "app.openstory.catalog.ui"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test-junit"))
}
