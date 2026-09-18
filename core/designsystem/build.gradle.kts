plugins {
    id("universalmedia.android.library")
    id("universalmedia.android.compose")
}
android { namespace = "app.universalmedia.core.designsystem" }
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
