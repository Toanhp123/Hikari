plugins { id("universalmedia.android.library") }
android {
    namespace = "app.universalmedia.playback.media3"
    defaultConfig { testInstrumentationRunner = "app.universalmedia.playback.media3.PlaybackTestRunner" }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":source:api"))
    implementation(project(":playback:api"))

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
