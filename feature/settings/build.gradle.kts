plugins {
    id("universalmedia.android.library")
    id("universalmedia.android.compose")
}
android { namespace = "app.universalmedia.feature.settings" }
dependencies {
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
