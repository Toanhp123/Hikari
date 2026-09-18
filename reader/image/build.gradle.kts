plugins {
    id("universalmedia.android.library")
    id("universalmedia.android.compose")
}
android { namespace = "app.universalmedia.reader.image" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":source:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
